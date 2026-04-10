#!/usr/bin/env python3
"""
Run one-shot faithfulness evaluation with ragas (no reference answers).

이 스크립트는 "정답(reference) 없이" 답변의 충실도(faithfulness)를 점수화한다.
즉, LLM judge가 답변의 각 주장(claim)이 검색된 컨텍스트에 근거하는지 판단한다.

Per-question flow:
1) Thread Turn API 호출: POST /api/threads/turns (access_token 쿠키 인증)
2) 응답에서 status/answer/turnId 추출
3) DB 조회: turn_citations에서 source_chunk_ref 목록 확보
4) Qdrant 조회: sourceChunkRef 기반으로 full chunk 텍스트 복원
5) ragas Faithfulness 점수화:
   - 답변을 개별 claim으로 분해
   - 각 claim이 컨텍스트에 근거하는지 NLI 판정
   - 점수 = (근거 있는 claim 수) / (전체 claim 수)
6) 리포트 출력: JSON + Markdown
"""

from __future__ import annotations

import argparse
import asyncio
import os
import sys
from typing import Any, Dict, List, Optional, Sequence

from eval_common import (
    STATUS_EXCLUDED_CONTEXT_MISSING,
    STATUS_EXCLUDED_FAILED,
    STATUS_EXCLUDED_IRRELEVANT,
    STATUS_OK,
    ChatApiClient,
    DbReader,
    EvalConfig,
    QdrantRestClient,
    add_common_args,
    build_config_from_args,
    iso_ts,
    load_prompt_snapshot,
    load_questions,
    slug_ts,
    summarize_samples,
    utc_now,
    write_reports,
)


class RagasFaithfulnessScorer:
    """
    ragas Faithfulness 평가 래퍼.

    Faithfulness는 답변의 각 claim이 검색된 컨텍스트에 근거하는지를 판정한다.
    점수 = (근거 있는 claim 수) / (전체 claim 수)

    LLM 호출 2회/질문:
    1) claim 분해 (답변 → 개별 주장 목록)
    2) NLI 검증 (각 주장이 컨텍스트에서 추론 가능한지)

    payload는 ragas가 요구하는 필드명으로 맞춘다:
    - user_input
    - response
    - retrieved_contexts
    """

    def __init__(self, api_key: str, model_name: str):
        os.environ["OPENAI_API_KEY"] = api_key

        self.metric_name = "faithfulness"
        self._init_error: Optional[str] = None

        try:
            from ragas.llms import llm_factory
            from ragas.metrics import Faithfulness

            llm = llm_factory(model=model_name)
            self.metric = Faithfulness(llm=llm)
        except Exception as exc:
            self._init_error = str(exc)
            raise RuntimeError(
                f"Failed to initialize Faithfulness metric: {exc}. "
                f"Ensure ragas>=0.2.0 is installed."
            ) from exc

        self.sample_cls = self._resolve_single_turn_sample_cls()

    @staticmethod
    def _resolve_single_turn_sample_cls() -> Optional[Any]:
        """ragas 버전 차이를 감안해 SingleTurnSample import 경로를 해석한다."""
        try:
            from ragas import SingleTurnSample

            return SingleTurnSample
        except Exception:
            try:
                from ragas.dataset_schema import SingleTurnSample

                return SingleTurnSample
            except Exception:
                return None

    @staticmethod
    def _as_float(value: Any) -> float:
        """ragas 점수 반환 타입 차이를 float으로 정규화한다."""
        if isinstance(value, (int, float)):
            return float(value)
        if isinstance(value, dict):
            for candidate in value.values():
                if isinstance(candidate, (int, float)):
                    return float(candidate)
        if hasattr(value, "value"):
            inner = getattr(value, "value")
            if isinstance(inner, (int, float)):
                return float(inner)
        if hasattr(value, "score"):
            inner = getattr(value, "score")
            if isinstance(inner, (int, float)):
                return float(inner)
        raise TypeError(f"Unsupported ragas score value type: {type(value)}")

    def score(self, question: str, response: str, retrieved_contexts: Sequence[str]) -> float:
        """
        질문 1건에 대한 faithfulness 점수를 계산한다.

        핵심 매핑:
        - question -> user_input
        - response -> response
        - retrieved_contexts -> retrieved_contexts
        """

        payload = {
            "user_input": question,
            "response": response,
            "retrieved_contexts": list(retrieved_contexts),
        }

        if not self.sample_cls:
            raise RuntimeError(
                "SingleTurnSample class is unavailable for installed ragas version"
            )

        sample = self.sample_cls(**payload)

        if hasattr(self.metric, "single_turn_ascore"):
            result = asyncio.run(self.metric.single_turn_ascore(sample))
            return self._as_float(result)

        if hasattr(self.metric, "single_turn_score"):
            result = self.metric.single_turn_score(sample)
            return self._as_float(result)

        raise RuntimeError(
            "Installed ragas metric does not support single_turn_ascore/single_turn_score"
        )


def build_report_markdown(result: Dict[str, Any]) -> str:
    summary = result["summary"]
    lines: List[str] = []
    lines.append("# RAGAS Faithfulness Evaluation Report")
    lines.append("")
    lines.append(f"- Run ID: `{result['run_id']}`")
    prompt_label = result.get("prompt_label")
    if prompt_label:
        lines.append(f"- Prompt Label: `{prompt_label}`")
    lines.append(f"- Started At (UTC): `{result['started_at']}`")
    lines.append(f"- Finished At (UTC): `{result['finished_at']}`")
    lines.append(f"- Dataset: `{result['dataset_path']}`")
    lines.append(f"- Judge Model: `{result['judge_model']}`")
    lines.append(f"- Metric: `{result['metric_name']}`")
    lines.append("")
    lines.append("## Summary")
    lines.append("")
    lines.append(f"- Total: **{summary['total']}**")
    lines.append(f"- Evaluated (`ok`): **{summary['evaluated']}**")
    lines.append(f"- Excluded Failed: **{summary['excluded_failed']}**")
    lines.append(f"- Excluded Irrelevant: **{summary['excluded_irrelevant']}**")
    lines.append(f"- Excluded Context Missing: **{summary['excluded_context_missing']}**")
    score_text = "N/A" if summary["mean_score"] is None else f"{summary['mean_score']:.6f}"
    lines.append(f"- Mean Faithfulness Score: **{score_text}**")
    lines.append(f"- Threshold: **{summary['threshold']:.2f}**")
    lines.append(f"- Pass: **{summary['pass']}**")
    lines.append("")

    prompt_snapshot = result.get("prompt_snapshot")
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

    ok_samples = [s for s in result["samples"] if s["status"] == STATUS_OK]
    ok_samples = sorted(ok_samples, key=lambda s: s.get("metric_score", 1e9))
    if ok_samples:
        lines.append("## Lowest Scored Samples (Potential Hallucination)")
        lines.append("")
        lines.append("| question_id | score | context_count | answer_preview |")
        lines.append("|---|---:|---:|---|")
        for sample in ok_samples[:10]:
            score = sample.get("metric_score")
            score_value = "N/A" if score is None else f"{score:.6f}"
            answer = (sample.get("response") or "")[:80].replace("\n", " ").replace("|", "\\|")
            lines.append(
                f"| `{sample['question_id']}` | {score_value}"
                f" | {sample.get('retrieved_contexts_count', 0)} | {answer} |"
            )
        lines.append("")

    excluded = [s for s in result["samples"] if s["status"] != STATUS_OK]
    if excluded:
        lines.append("## Excluded/Failed Samples")
        lines.append("")
        lines.append("| question_id | status | error |")
        lines.append("|---|---|---|")
        for sample in excluded[:20]:
            error = (sample.get("error") or "").replace("\n", " ").strip()
            if len(error) > 120:
                error = error[:117] + "..."
            lines.append(f"| `{sample['question_id']}` | `{sample['status']}` | {error} |")
        lines.append("")

    return "\n".join(lines).strip() + "\n"


def evaluate(cfg: EvalConfig) -> Dict[str, Any]:
    """
    메인 평가 루프.

    샘플 1건 처리 순서:
    1) Thread Turn API 호출
    2) 응답 status 분기 (COMPLETED/IRRELEVANT/기타)
    3) turn_citations에서 source_chunk_ref 조회
    4) Qdrant full chunk 복원
    5) ragas Faithfulness 점수 계산
    6) 상태/오류를 샘플 결과에 기록
    """

    questions = load_questions(cfg.dataset, cfg.max_samples)
    chat = ChatApiClient(cfg.base_url, cfg.access_token, cfg.request_timeout_sec)
    db = DbReader(cfg)
    qdrant = QdrantRestClient(cfg)
    scorer = RagasFaithfulnessScorer(cfg.ragas_api_key, cfg.judge_model)

    samples: List[Dict[str, Any]] = []
    started = utc_now()

    try:
        for idx, item in enumerate(questions, start=1):
            if cfg.verbose:
                print(f"[{idx}/{len(questions)}] {item.question_id}")

            base: Dict[str, Any] = {
                "question_id": item.question_id,
                "question": item.question,
                "status": None,
                "response": None,
                "retrieved_context_ids": [],
                "retrieved_context_ids_resolved": [],
                "retrieved_contexts": [],
                "retrieved_contexts_count": 0,
                "metric_score": None,
                "error": None,
                "thread_id": None,
                "turn_id": None,
                "turn_status": None,
            }

            # Step 1) Thread Turn API 호출
            thread_id, turn_id, answer, status, chat_error = chat.send(item.question)
            if chat_error:
                base["status"] = STATUS_EXCLUDED_FAILED
                base["error"] = chat_error
                samples.append(base)
                continue

            base["thread_id"] = thread_id
            base["turn_id"] = turn_id
            base["response"] = answer
            base["turn_status"] = status

            # Step 2) 상태 분기
            upper_status = (status or "").upper()
            if upper_status == "IRRELEVANT":
                base["status"] = STATUS_EXCLUDED_IRRELEVANT
                base["error"] = "turn status is IRRELEVANT"
                samples.append(base)
                continue
            if upper_status != "COMPLETED":
                base["status"] = STATUS_EXCLUDED_FAILED
                base["error"] = f"turn status is {status or 'UNKNOWN'}"
                samples.append(base)
                continue

            # Step 3) source_chunk_ref 확보
            refs = db.turn_citation_refs(turn_id)
            base["retrieved_context_ids"] = refs
            if not refs:
                base["status"] = STATUS_EXCLUDED_CONTEXT_MISSING
                base["error"] = "no source_chunk_ref found for completed turn"
                samples.append(base)
                continue

            # Step 4) sourceChunkRef -> full chunk text 복원
            contexts: List[str] = []
            resolved_refs: List[str] = []
            missing_refs: List[str] = []
            for ref in refs:
                try:
                    text = qdrant.get_chunk_text(ref, cfg.member_id)
                except Exception as exc:
                    base["status"] = STATUS_EXCLUDED_FAILED
                    base["error"] = f"qdrant lookup failed: {exc}"
                    break
                if text:
                    contexts.append(text)
                    resolved_refs.append(ref)
                else:
                    missing_refs.append(ref)

            if base["status"] == STATUS_EXCLUDED_FAILED:
                base["retrieved_context_ids_resolved"] = resolved_refs
                base["retrieved_contexts"] = contexts
                base["retrieved_contexts_count"] = len(contexts)
                samples.append(base)
                continue

            base["retrieved_context_ids_resolved"] = resolved_refs
            base["retrieved_contexts"] = contexts
            base["retrieved_contexts_count"] = len(contexts)

            if not contexts:
                base["status"] = STATUS_EXCLUDED_CONTEXT_MISSING
                base["error"] = "full chunk restore failed for all source_chunk_ref ids"
                samples.append(base)
                continue

            if not str(base["response"] or "").strip():
                base["status"] = STATUS_EXCLUDED_FAILED
                base["error"] = "empty response text"
                samples.append(base)
                continue

            # Step 5) ragas Faithfulness 점수 계산
            try:
                score = scorer.score(item.question, str(base["response"]), contexts)
            except Exception as exc:
                base["status"] = STATUS_EXCLUDED_FAILED
                base["error"] = f"ragas scoring failed: {exc}"
                samples.append(base)
                continue

            base["status"] = STATUS_OK
            base["metric_score"] = score
            if missing_refs:
                base["error"] = f"partial context restore: {len(missing_refs)} refs missing"
            samples.append(base)
    finally:
        db.close()

    finished = utc_now()
    summary = summarize_samples(samples, cfg.threshold)

    run_id = slug_ts(started) + "_faithfulness"
    if cfg.prompt_label:
        run_id += f"_{cfg.prompt_label}"

    return {
        "run_id": run_id,
        "started_at": iso_ts(started),
        "finished_at": iso_ts(finished),
        "duration_sec": round((finished - started).total_seconds(), 3),
        "dataset_path": str(cfg.dataset),
        "base_url": cfg.base_url,
        "member_id": cfg.member_id,
        "judge_model": cfg.judge_model,
        "metric_name": scorer.metric_name,
        "prompt_label": cfg.prompt_label,
        "prompt_snapshot": load_prompt_snapshot(),
        "samples_requested": cfg.max_samples,
        "summary": summary,
        "samples": samples,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run one-shot ragas faithfulness evaluation (no references).",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    add_common_args(parser)
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.70,
        help="Pass threshold for mean faithfulness score.",
    )
    return parser.parse_args()


def main() -> int:
    """CLI 진입점."""

    try:
        args = parse_args()
        cfg = build_config_from_args(args, args.threshold)
        result = evaluate(cfg)
        json_path, md_path = write_reports(result, cfg.report_dir, build_report_markdown)
    except Exception as exc:
        print(f"[error] {exc}", file=sys.stderr)
        return 1

    summary = result["summary"]
    score = summary["mean_score"]
    score_text = "N/A" if score is None else f"{score:.6f}"
    label_text = f" label={cfg.prompt_label}" if cfg.prompt_label else ""
    print(f"run_id={result['run_id']}")
    print(f"score={score_text} threshold={summary['threshold']:.2f} pass={summary['pass']}{label_text}")
    print(f"evaluated={summary['evaluated']} total={summary['total']}")
    print(f"json={json_path}")
    print(f"markdown={md_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
