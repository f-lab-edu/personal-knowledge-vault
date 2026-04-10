#!/usr/bin/env python3
"""
통합 벤치마크 러너: 두 메트릭 × N 라운드 자동 실행 + 통계 테이블 생성.

특징:
- 라운드당 질문별 API 호출 1회 (두 메트릭 모두일 경우 응답 재사용)
- --metrics 옵션으로 전체/단일 메트릭 선택 가능
- 라운드별 개별 리포트(result.json + summary.md) + 통합 benchmark_summary.md 생성

사용 예:
  # 두 메트릭 모두 3라운드
  python3 scripts/ragas/run_benchmark.py \
    --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
    --rounds 3 --prompt-label baseline --verbose

  # 단일 메트릭만 3라운드
  python3 scripts/ragas/run_benchmark.py \
    --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
    --rounds 3 --metrics faithfulness --prompt-label baseline
"""

from __future__ import annotations

import argparse
import json
import math
import os
import statistics
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

from eval_common import (
    STATUS_EXCLUDED_CONTEXT_MISSING,
    STATUS_EXCLUDED_FAILED,
    STATUS_EXCLUDED_IRRELEVANT,
    STATUS_OK,
    ChatApiClient,
    DbReader,
    EvalConfig,
    QdrantRestClient,
    QuestionItem,
    add_common_args,
    build_config_from_args,
    iso_ts,
    load_dotenvs,
    load_prompt_snapshot,
    load_questions,
    slug_ts,
    summarize_samples,
    utc_now,
)


# ---------------------------------------------------------------------------
# 응답 수집 (API 호출 1회 → 두 메트릭 공유)
# ---------------------------------------------------------------------------

@dataclass
class CollectedSample:
    """API 호출 + 컨텍스트 복원 결과. 메트릭 평가 전 단계."""

    question_id: str
    question: str
    thread_id: Optional[str]
    turn_id: Optional[int]
    turn_status: Optional[str]
    response: Optional[str]
    retrieved_context_ids: List[str]
    retrieved_context_ids_resolved: List[str]
    retrieved_contexts: List[str]
    status: str
    error: Optional[str]


def collect_responses(
    cfg: EvalConfig,
    questions: List[QuestionItem],
) -> List[CollectedSample]:
    """
    질문 목록에 대해 Chat API → DB → Qdrant 파이프라인을 실행한다.

    메트릭 평가 없이 응답과 컨텍스트만 수집하므로,
    이 결과를 여러 메트릭에 재사용할 수 있다.
    """

    chat = ChatApiClient(cfg.base_url, cfg.access_token, cfg.request_timeout_sec)
    db = DbReader(cfg)
    qdrant = QdrantRestClient(cfg)

    samples: List[CollectedSample] = []

    try:
        for idx, item in enumerate(questions, start=1):
            if cfg.verbose:
                print(f"  [{idx}/{len(questions)}] {item.question_id}")

            s = CollectedSample(
                question_id=item.question_id,
                question=item.question,
                thread_id=None,
                turn_id=None,
                turn_status=None,
                response=None,
                retrieved_context_ids=[],
                retrieved_context_ids_resolved=[],
                retrieved_contexts=[],
                status=STATUS_OK,
                error=None,
            )

            # Step 1) Thread Turn API 호출
            thread_id, turn_id, answer, status, chat_error = chat.send(item.question)
            if chat_error:
                s.status = STATUS_EXCLUDED_FAILED
                s.error = chat_error
                samples.append(s)
                continue

            s.thread_id = thread_id
            s.turn_id = turn_id
            s.response = answer
            s.turn_status = status

            # Step 2) 상태 분기
            upper_status = (status or "").upper()
            if upper_status == "IRRELEVANT":
                s.status = STATUS_EXCLUDED_IRRELEVANT
                s.error = "turn status is IRRELEVANT"
                samples.append(s)
                continue
            if upper_status != "COMPLETED":
                s.status = STATUS_EXCLUDED_FAILED
                s.error = f"turn status is {status or 'UNKNOWN'}"
                samples.append(s)
                continue

            # Step 3) source_chunk_ref 확보
            refs = db.turn_citation_refs(turn_id)
            s.retrieved_context_ids = refs
            if not refs:
                s.status = STATUS_EXCLUDED_CONTEXT_MISSING
                s.error = "no source_chunk_ref found for completed turn"
                samples.append(s)
                continue

            # Step 4) sourceChunkRef -> full chunk text 복원
            contexts: List[str] = []
            resolved_refs: List[str] = []
            missing_refs: List[str] = []
            for ref in refs:
                try:
                    text = qdrant.get_chunk_text(ref, cfg.member_id)
                except Exception as exc:
                    s.status = STATUS_EXCLUDED_FAILED
                    s.error = f"qdrant lookup failed: {exc}"
                    break
                if text:
                    contexts.append(text)
                    resolved_refs.append(ref)
                else:
                    missing_refs.append(ref)

            s.retrieved_context_ids_resolved = resolved_refs
            s.retrieved_contexts = contexts

            if s.status == STATUS_EXCLUDED_FAILED:
                samples.append(s)
                continue

            if not contexts:
                s.status = STATUS_EXCLUDED_CONTEXT_MISSING
                s.error = "full chunk restore failed for all source_chunk_ref ids"
                samples.append(s)
                continue

            if not str(s.response or "").strip():
                s.status = STATUS_EXCLUDED_FAILED
                s.error = "empty response text"
                samples.append(s)
                continue

            if missing_refs:
                s.error = f"partial context restore: {len(missing_refs)} refs missing"

            samples.append(s)
    finally:
        db.close()

    return samples


# ---------------------------------------------------------------------------
# 메트릭 평가 (수집된 샘플에 scorer 적용)
# ---------------------------------------------------------------------------

def score_samples(
    collected: List[CollectedSample],
    scorer: Any,
    threshold: float,
    verbose: bool = False,
) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    """
    수집된 샘플에 메트릭을 적용하고 결과 + summary를 반환한다.

    Returns:
        (samples_list, summary_dict)
    """

    results: List[Dict[str, Any]] = []

    for idx, s in enumerate(collected, start=1):
        base: Dict[str, Any] = {
            "question_id": s.question_id,
            "question": s.question,
            "status": s.status,
            "response": s.response,
            "retrieved_context_ids": s.retrieved_context_ids,
            "retrieved_context_ids_resolved": s.retrieved_context_ids_resolved,
            "retrieved_contexts": s.retrieved_contexts,
            "retrieved_contexts_count": len(s.retrieved_contexts),
            "metric_score": None,
            "error": s.error,
            "thread_id": s.thread_id,
            "turn_id": s.turn_id,
            "turn_status": s.turn_status,
        }

        if s.status != STATUS_OK:
            results.append(base)
            continue

        try:
            score = scorer.score(s.question, str(s.response), s.retrieved_contexts)
        except Exception as exc:
            base["status"] = STATUS_EXCLUDED_FAILED
            base["error"] = f"ragas scoring failed: {exc}"
            results.append(base)
            continue

        base["metric_score"] = score
        if verbose:
            print(f"    [{idx}/{len(collected)}] {s.question_id} -> {score:.4f}")
        results.append(base)

    summary = summarize_samples(results, threshold)
    return results, summary


# ---------------------------------------------------------------------------
# Scorer 팩토리
# ---------------------------------------------------------------------------

def create_scorer(metric_name: str, api_key: str, model_name: str) -> Any:
    """메트릭 이름으로 적절한 scorer 인스턴스를 생성한다."""
    if metric_name == "context_precision":
        from run_context_precision_eval import RagasNoRefScorer
        return RagasNoRefScorer(api_key, model_name)
    elif metric_name == "faithfulness":
        from run_faithfulness_eval import RagasFaithfulnessScorer
        return RagasFaithfulnessScorer(api_key, model_name)
    else:
        raise ValueError(f"Unknown metric: {metric_name}")


METRIC_THRESHOLDS = {
    "context_precision": 0.75,
    "faithfulness": 0.70,
}

METRIC_DISPLAY_NAMES = {
    "context_precision": "Context Precision",
    "faithfulness": "Faithfulness",
}


# ---------------------------------------------------------------------------
# 리포트 생성
# ---------------------------------------------------------------------------

def build_round_result(
    samples: List[Dict[str, Any]],
    summary: Dict[str, Any],
    *,
    round_num: int,
    metric_name: str,
    started: Any,
    finished: Any,
    cfg: EvalConfig,
    scorer_metric_name: str,
) -> Dict[str, Any]:
    """개별 라운드 + 메트릭의 result dict를 구성한다."""

    run_id = f"round{round_num}_{metric_name}"

    return {
        "run_id": run_id,
        "started_at": iso_ts(started),
        "finished_at": iso_ts(finished),
        "duration_sec": round((finished - started).total_seconds(), 3),
        "dataset_path": str(cfg.dataset),
        "base_url": cfg.base_url,
        "member_id": cfg.member_id,
        "judge_model": cfg.judge_model,
        "metric_name": scorer_metric_name,
        "prompt_label": cfg.prompt_label,
        "prompt_snapshot": load_prompt_snapshot(),
        "samples_requested": cfg.max_samples,
        "summary": summary,
        "samples": samples,
    }


def write_round_report(
    result: Dict[str, Any],
    benchmark_dir: Path,
    build_markdown_fn: Any,
) -> Tuple[Path, Path]:
    """라운드별 개별 리포트를 저장한다."""

    run_dir = benchmark_dir / result["run_id"]
    run_dir.mkdir(parents=True, exist_ok=True)

    json_path = run_dir / "result.json"
    md_path = run_dir / "summary.md"

    with json_path.open("w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    with md_path.open("w", encoding="utf-8") as f:
        f.write(build_markdown_fn(result))

    return json_path, md_path


def get_build_markdown_fn(metric_name: str) -> Any:
    """메트릭 이름으로 적절한 markdown 빌더를 반환한다."""
    if metric_name == "context_precision":
        from run_context_precision_eval import build_report_markdown
        return build_report_markdown
    elif metric_name == "faithfulness":
        from run_faithfulness_eval import build_report_markdown
        return build_report_markdown
    else:
        raise ValueError(f"Unknown metric: {metric_name}")


def build_benchmark_summary(
    *,
    metrics: List[str],
    rounds: int,
    scores: Dict[str, List[Optional[float]]],
    round_details: List[Dict[str, Any]],
    cfg: EvalConfig,
    total_started: Any,
    total_finished: Any,
) -> str:
    """통합 benchmark_summary.md 콘텐츠를 생성한다."""

    lines: List[str] = []
    lines.append("# Benchmark Summary")
    lines.append("")
    if cfg.prompt_label:
        lines.append(f"- Prompt Label: `{cfg.prompt_label}`")
    lines.append(f"- Rounds: **{rounds}**")
    lines.append(f"- Metrics: {', '.join(METRIC_DISPLAY_NAMES.get(m, m) for m in metrics)}")
    lines.append(f"- Dataset: `{cfg.dataset}`")
    lines.append(f"- Max Samples: {cfg.max_samples}")
    lines.append(f"- Judge Model: `{cfg.judge_model}`")
    lines.append(f"- Started At (UTC): `{iso_ts(total_started)}`")
    lines.append(f"- Finished At (UTC): `{iso_ts(total_finished)}`")
    total_sec = round((total_finished - total_started).total_seconds(), 1)
    lines.append(f"- Total Duration: **{total_sec}s**")
    lines.append("")

    # --- Results 테이블 ---
    lines.append("## Results")
    lines.append("")

    # 테이블 헤더
    header_parts = ["| Metric"]
    separator_parts = ["|---"]
    for r in range(1, rounds + 1):
        header_parts.append(f"| Round {r} ")
        separator_parts.append("|---:")
    header_parts.extend(["| Mean", "| Stdev", "| Min", "| Max", "|"])
    separator_parts.extend(["|---:", "|---:", "|---:", "|---:", "|"])
    lines.append("".join(header_parts))
    lines.append("".join(separator_parts))

    # 테이블 행
    for metric in metrics:
        display_name = METRIC_DISPLAY_NAMES.get(metric, metric)
        row_parts = [f"| {display_name} "]

        metric_scores = scores.get(metric, [])
        valid_scores = [s for s in metric_scores if s is not None]

        for s in metric_scores:
            if s is None:
                row_parts.append("| N/A ")
            else:
                row_parts.append(f"| {s:.4f} ")

        if valid_scores:
            mean_val = statistics.fmean(valid_scores)
            stdev_val = statistics.stdev(valid_scores) if len(valid_scores) > 1 else 0.0
            min_val = min(valid_scores)
            max_val = max(valid_scores)
            row_parts.append(f"| **{mean_val:.4f}** ")
            row_parts.append(f"| {stdev_val:.4f} ")
            row_parts.append(f"| {min_val:.4f} ")
            row_parts.append(f"| {max_val:.4f} ")
        else:
            row_parts.extend(["| N/A ", "| N/A ", "| N/A ", "| N/A "])

        row_parts.append("|")
        lines.append("".join(row_parts))

    lines.append("")

    # --- Per-Round Details ---
    lines.append("## Per-Round Details")
    lines.append("")
    lines.append("| Round | Metric | Evaluated | Excluded | Duration |")
    lines.append("|---:|---|---:|---:|---:|")

    for detail in round_details:
        summary = detail["summary"]
        excluded_total = (
            summary.get("excluded_failed", 0)
            + summary.get("excluded_irrelevant", 0)
            + summary.get("excluded_context_missing", 0)
        )
        lines.append(
            f"| {detail['round']} "
            f"| {METRIC_DISPLAY_NAMES.get(detail['metric'], detail['metric'])} "
            f"| {summary['evaluated']} "
            f"| {excluded_total} "
            f"| {detail['duration_sec']:.1f}s |"
        )

    lines.append("")

    # --- Prompt Snapshot ---
    prompt_snapshot = load_prompt_snapshot()
    if prompt_snapshot:
        lines.append("## Prompt Snapshot")
        lines.append("")
        for filename, content in prompt_snapshot.items():
            lines.append(f"### {filename}")
            lines.append("")
            lines.append("```")
            lines.append(content)
            lines.append("```")
            lines.append("")

    return "\n".join(lines).strip() + "\n"


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run RAGAS benchmark: multiple metrics x N rounds with statistics.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    add_common_args(parser)
    parser.add_argument(
        "--rounds",
        type=int,
        default=3,
        help="Number of evaluation rounds per metric.",
    )
    parser.add_argument(
        "--metrics",
        choices=["all", "context_precision", "faithfulness"],
        default="all",
        help="Which metrics to evaluate.",
    )
    return parser.parse_args()


def main() -> int:
    """CLI 진입점."""

    try:
        args = parse_args()
    except SystemExit:
        return 1

    # 메트릭 목록 결정
    if args.metrics == "all":
        metrics = ["context_precision", "faithfulness"]
    else:
        metrics = [args.metrics]

    rounds = args.rounds
    if rounds < 1:
        print("[error] --rounds must be >= 1", file=sys.stderr)
        return 1

    try:
        # threshold는 벤치마크에서는 메트릭별로 적용하므로 임시값 사용
        cfg = build_config_from_args(args, threshold=0.0)
        questions = load_questions(cfg.dataset, cfg.max_samples)
    except Exception as exc:
        print(f"[error] {exc}", file=sys.stderr)
        return 1

    # 벤치마크 디렉토리 생성
    total_started = utc_now()
    benchmark_id = slug_ts(total_started) + "_benchmark"
    if cfg.prompt_label:
        benchmark_id += f"_{cfg.prompt_label}"
    benchmark_dir = cfg.report_dir / benchmark_id
    benchmark_dir.mkdir(parents=True, exist_ok=True)

    print(f"benchmark_id={benchmark_id}")
    print(f"metrics={','.join(metrics)} rounds={rounds} samples={len(questions)}")
    print(f"output={benchmark_dir}")
    print()

    # scorer 초기화 (한 번만)
    scorers: Dict[str, Any] = {}
    for metric in metrics:
        try:
            scorers[metric] = create_scorer(metric, cfg.ragas_api_key, cfg.judge_model)
        except Exception as exc:
            print(f"[error] Failed to initialize {metric} scorer: {exc}", file=sys.stderr)
            return 1

    # 라운드별 점수 기록
    scores: Dict[str, List[Optional[float]]] = {m: [] for m in metrics}
    round_details: List[Dict[str, Any]] = []

    for round_num in range(1, rounds + 1):
        print(f"=== Round {round_num}/{rounds} ===")

        # 응답 수집 (라운드당 1회)
        print(f"  Collecting responses...")
        round_started = utc_now()
        collected = collect_responses(cfg, questions)

        ok_count = sum(1 for s in collected if s.status == STATUS_OK)
        excluded_count = len(collected) - ok_count
        print(f"  Collected: {ok_count} ok, {excluded_count} excluded")

        # 각 메트릭으로 평가
        for metric in metrics:
            metric_started = utc_now()
            display_name = METRIC_DISPLAY_NAMES.get(metric, metric)
            threshold = METRIC_THRESHOLDS.get(metric, 0.70)
            print(f"  Scoring: {display_name}...")

            samples, summary = score_samples(
                collected, scorers[metric], threshold, verbose=cfg.verbose
            )
            metric_finished = utc_now()

            mean_score = summary["mean_score"]
            scores[metric].append(mean_score)

            score_text = "N/A" if mean_score is None else f"{mean_score:.4f}"
            print(f"    score={score_text} evaluated={summary['evaluated']}")

            # 라운드별 개별 리포트 저장
            result = build_round_result(
                samples,
                summary,
                round_num=round_num,
                metric_name=metric,
                started=metric_started,
                finished=metric_finished,
                cfg=cfg,
                scorer_metric_name=scorers[metric].metric_name,
            )

            md_fn = get_build_markdown_fn(metric)
            write_round_report(result, benchmark_dir, md_fn)

            round_details.append({
                "round": round_num,
                "metric": metric,
                "summary": summary,
                "duration_sec": result["duration_sec"],
            })

        print()

    # 통합 summary 생성
    total_finished = utc_now()
    summary_content = build_benchmark_summary(
        metrics=metrics,
        rounds=rounds,
        scores=scores,
        round_details=round_details,
        cfg=cfg,
        total_started=total_started,
        total_finished=total_finished,
    )

    summary_path = benchmark_dir / "benchmark_summary.md"
    with summary_path.open("w", encoding="utf-8") as f:
        f.write(summary_content)

    # 최종 출력
    print("=== Benchmark Complete ===")
    print()
    for metric in metrics:
        display_name = METRIC_DISPLAY_NAMES.get(metric, metric)
        valid = [s for s in scores[metric] if s is not None]
        if valid:
            mean_val = statistics.fmean(valid)
            stdev_val = statistics.stdev(valid) if len(valid) > 1 else 0.0
            print(f"{display_name}: mean={mean_val:.4f} stdev={stdev_val:.4f} ({len(valid)} rounds)")
        else:
            print(f"{display_name}: N/A (no valid scores)")
    print()
    print(f"summary={summary_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
