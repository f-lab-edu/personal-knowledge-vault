#!/usr/bin/env python3
"""
Run one-shot retrieval evaluation with ragas (no reference contexts).

이 스크립트는 "정답(reference context) 없이" retrieval 품질을 점수화한다.
즉, 질문/응답/검색된 컨텍스트만으로 LLM judge가 컨텍스트 활용도를 판단한다.

Per-question flow:
1) Chat API 호출: /api/chat/messages (access_token 쿠키 인증)
2) DB 조회: 해당 session의 최신 chat_history를 조회
3) DB 조회: chat_history_sources에서 source_chunk_ref 목록 확보
4) Qdrant 조회: sourceChunkRef 기반으로 full chunk 텍스트 복원
5) ragas 점수화:
   - 우선: ragas.metrics.ContextUtilization
   - fallback: ragas.metrics.LLMContextPrecisionWithoutReference
6) 리포트 출력: JSON + Markdown
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import json
import os
import statistics
import sys
import textwrap
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

import pymysql
import requests
from dotenv import load_dotenv


# 샘플별 최종 처리 상태.
STATUS_OK = "ok"
STATUS_EXCLUDED_FAILED = "excluded_failed"
STATUS_EXCLUDED_IRRELEVANT = "excluded_irrelevant"
STATUS_EXCLUDED_CONTEXT_MISSING = "excluded_context_missing"


@dataclass(frozen=True)
class QuestionItem:
    """질문셋(JSONL) 한 줄에 대응하는 내부 타입."""

    question_id: str
    question: str


@dataclass
class EvalConfig:
    """
    실행 시점에 확정되는 구성값.

    외부 입력(인자/env)을 초기에 강하게 검증해 실행 중간의 실패를 줄인다.
    """

    dataset: Path
    base_url: str
    report_dir: Path
    max_samples: int
    threshold: float
    judge_model: str
    request_timeout_sec: int
    verbose: bool
    access_token: str
    member_id: int
    ragas_api_key: str
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str
    qdrant_host: str
    qdrant_port: int
    qdrant_collection: str


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def iso_ts(dt: Optional[datetime] = None) -> str:
    value = dt or utc_now()
    return value.replace(microsecond=0).isoformat()


def slug_ts(dt: Optional[datetime] = None) -> str:
    value = dt or utc_now()
    return value.strftime("%Y%m%d_%H%M%S")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run one-shot ragas retrieval evaluation (no references).",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--dataset",
        required=True,
        type=Path,
        help="Input JSONL file containing question_id/question.",
    )
    parser.add_argument(
        "--base-url",
        default=os.getenv("PKV_BASE_URL", "http://localhost:8080"),
        help="PKV API base URL.",
    )
    parser.add_argument(
        "--report-dir",
        type=Path,
        default=Path("scripts/ragas/reports"),
        help="Report base directory.",
    )
    parser.add_argument(
        "--max-samples",
        type=int,
        default=50,
        help="Maximum number of questions to evaluate.",
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.75,
        help="Pass threshold for mean context precision.",
    )
    parser.add_argument(
        "--judge-model",
        default=os.getenv("RAGAS_JUDGE_MODEL", "gpt-4o-mini"),
        help="LLM judge model for ragas.",
    )
    parser.add_argument(
        "--request-timeout-sec",
        type=int,
        default=30,
        help="HTTP request timeout in seconds.",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print per-sample progress logs.",
    )
    return parser.parse_args()


def parse_access_token(raw: str) -> str:
    """
    다양한 입력 형태를 실제 access_token 값으로 정규화한다.

    허용:
    - "access_token=<JWT>; ..."
    - "Bearer <JWT>"
    - "<JWT>"
    """

    text = (raw or "").strip()
    if not text:
        raise ValueError("PKV_ACCESS_TOKEN is empty")
    if text.startswith("access_token="):
        # Accept whole cookie-like value.
        text = text.split(";", 1)[0]
        return text.split("=", 1)[1]
    if text.startswith("Bearer "):
        return text[7:].strip()
    return text


def decode_member_id_from_jwt(token: str) -> int:
    """
    JWT payload의 sub를 member_id로 해석한다.

    PKV 서버는 인증 principal로 memberId(Long)를 사용하므로, 평가 스크립트도
    동일 사용자 스코프로 DB/Qdrant 결과를 좁히기 위해 member_id가 필요하다.
    """

    parts = token.split(".")
    if len(parts) < 2:
        raise ValueError("PKV_ACCESS_TOKEN is not a JWT-like token")
    payload = parts[1]
    payload += "=" * (-len(payload) % 4)
    decoded = base64.urlsafe_b64decode(payload.encode("utf-8"))
    obj = json.loads(decoded.decode("utf-8"))
    subject = obj.get("sub")
    if subject is None:
        raise ValueError("JWT payload missing 'sub'")
    return int(subject)


def require_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise ValueError(f"Missing required env var: {name}")
    return value


def resolve_ragas_api_key() -> str:
    """
    ragas 평가 키를 해석한다.

    우선순위:
    1) RAGAS_OPENAI_API_KEY
    2) CHAT_MODEL_API_KEY (기존 앱 키 재사용)
    """

    ragas_key = os.getenv("RAGAS_OPENAI_API_KEY", "").strip()
    if ragas_key:
        return ragas_key

    shared_key = os.getenv("CHAT_MODEL_API_KEY", "").strip()
    if shared_key:
        return shared_key

    raise ValueError(
        "Missing API key: set RAGAS_OPENAI_API_KEY "
        "(or reuse existing CHAT_MODEL_API_KEY)"
    )


def build_config(args: argparse.Namespace) -> EvalConfig:
    """
    CLI 인자 + .env/.env.local + 프로세스 env를 병합해 최종 설정을 만든다.
    """

    # Root project envs.
    load_dotenv(".env")
    load_dotenv(".env.local")
    # RAGAS-specific envs. .env.local should have highest precedence.
    load_dotenv("scripts/ragas/.env")
    load_dotenv("scripts/ragas/.env.local", override=True)

    access_token = parse_access_token(require_env("PKV_ACCESS_TOKEN"))
    explicit_member_id = os.getenv("PKV_MEMBER_ID", "").strip()
    if explicit_member_id:
        member_id = int(explicit_member_id)
    else:
        member_id = decode_member_id_from_jwt(access_token)

    return EvalConfig(
        dataset=args.dataset,
        base_url=args.base_url.rstrip("/"),
        report_dir=args.report_dir,
        max_samples=args.max_samples,
        threshold=args.threshold,
        judge_model=args.judge_model,
        request_timeout_sec=args.request_timeout_sec,
        verbose=args.verbose,
        access_token=access_token,
        member_id=member_id,
        ragas_api_key=resolve_ragas_api_key(),
        db_host=os.getenv("DB_HOST", "localhost"),
        db_port=int(os.getenv("DB_PORT", "3306")),
        db_name=os.getenv("DB_NAME", "pkv"),
        db_user=os.getenv("DB_USERNAME", "root"),
        db_password=os.getenv("DB_PASSWORD", "root"),
        qdrant_host=os.getenv("QDRANT_HOST", "localhost"),
        qdrant_port=int(os.getenv("QDRANT_PORT", "6333")),
        qdrant_collection=os.getenv("QDRANT_COLLECTION", "pkv_text_segments"),
    )


def load_questions(path: Path, max_samples: int) -> List[QuestionItem]:
    """
    JSONL 질문셋을 로드하고 최소 품질 검증을 수행한다.

    검증:
    - question_id 필수
    - question 필수
    - question_id 중복 금지
    """

    if not path.exists():
        raise FileNotFoundError(f"Dataset not found: {path}")
    if max_samples <= 0:
        raise ValueError("--max-samples must be > 0")

    items: List[QuestionItem] = []
    seen_ids = set()
    with path.open("r", encoding="utf-8") as f:
        for idx, raw in enumerate(f, start=1):
            line = raw.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"Invalid JSON at line {idx}: {exc}") from exc

            question_id = str(obj.get("question_id", "")).strip()
            question = str(obj.get("question", "")).strip()
            if not question_id:
                raise ValueError(f"Line {idx}: question_id is required")
            if not question:
                raise ValueError(f"Line {idx}: question is required")
            if question_id in seen_ids:
                raise ValueError(f"Duplicate question_id: {question_id}")

            seen_ids.add(question_id)
            items.append(QuestionItem(question_id=question_id, question=question))
            if len(items) >= max_samples:
                break

    if not items:
        raise ValueError("Dataset has no valid records")
    return items


class ChatApiClient:
    """
    PKV Chat API 호출 전용 클라이언트.

    주의:
    - 이 프로젝트 API 응답은 ApiResponse<T> 래퍼를 사용한다.
    - 따라서 HTTP 200이어도 body.success=false일 수 있어 이중 체크가 필요하다.
    """

    def __init__(self, base_url: str, access_token: str, timeout_sec: int):
        self.endpoint = f"{base_url}/api/chat/messages"
        self.timeout_sec = timeout_sec
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Content-Type": "application/json",
                "Accept": "application/json",
            }
        )
        self.session.cookies.set("access_token", access_token, path="/api")

    def send(self, question: str) -> Tuple[Optional[str], Optional[str], Optional[str]]:
        """
        질문 1건을 전송한다.

        Returns:
        - session_id
        - response(content)
        - error_message (성공 시 None)
        """

        # 질문별 독립평가를 위해 sessionId를 None으로 전달한다.
        payload = {"sessionId": None, "content": question}
        try:
            resp = self.session.post(self.endpoint, json=payload, timeout=self.timeout_sec)
        except Exception as exc:  # requests exceptions can vary by transport
            return None, None, f"chat request failed: {exc}"

        if resp.status_code != 200:
            return None, None, f"chat status={resp.status_code} body={resp.text[:500]}"

        try:
            body = resp.json()
        except ValueError as exc:
            return None, None, f"chat response is not JSON: {exc}"

        if not body.get("success", False):
            error = body.get("error") or {}
            code = error.get("code", "unknown")
            message = error.get("message", "unknown error")
            return None, None, f"chat api error {code}: {message}"

        data = body.get("data") or {}
        session_id = data.get("sessionId")
        response = data.get("content")
        if not session_id:
            return None, None, "chat response missing sessionId"
        if response is None:
            response = ""
        return str(session_id), str(response), None


class DbReader:
    """
    평가용 read-only DB 조회 유틸.

    서버 코드 변경 없이 source_chunk_ref를 얻기 위해 직접 조회한다.
    """

    def __init__(self, cfg: EvalConfig):
        self.conn = pymysql.connect(
            host=cfg.db_host,
            port=cfg.db_port,
            user=cfg.db_user,
            password=cfg.db_password,
            database=cfg.db_name,
            charset="utf8mb4",
            cursorclass=pymysql.cursors.DictCursor,
            autocommit=True,
        )

    def close(self) -> None:
        self.conn.close()

    def latest_history(self, member_id: int, session_key: str) -> Optional[Dict[str, Any]]:
        """
        해당 세션의 최신 chat_history 1건을 가져온다.

        Chat API 호출 직후 같은 세션에서 가장 최신 row가 방금 질의의 결과다.
        """

        sql = textwrap.dedent(
            """
            SELECT ch.id, ch.status, ch.answer
            FROM chat_histories ch
            INNER JOIN chat_sessions cs ON cs.id = ch.session_id
            WHERE ch.member_id = %s
              AND cs.member_id = %s
              AND cs.session_key = %s
            ORDER BY ch.created_at DESC, ch.id DESC
            LIMIT 1
            """
        )
        with self.conn.cursor() as cur:
            cur.execute(sql, (member_id, member_id, session_key))
            return cur.fetchone()

    def source_chunk_refs(self, history_id: int) -> List[str]:
        """
        display_order 순으로 source_chunk_ref를 반환한다.

        ragas 평가의 retrieved_context_ids 역할을 하며,
        이후 Qdrant에서 full chunk 텍스트를 복원하는 키로 사용한다.
        """

        sql = textwrap.dedent(
            """
            SELECT source_chunk_ref
            FROM chat_history_sources
            WHERE history_id = %s
            ORDER BY display_order ASC
            """
        )
        refs: List[str] = []
        with self.conn.cursor() as cur:
            cur.execute(sql, (history_id,))
            rows = cur.fetchall()
            for row in rows:
                ref = (row.get("source_chunk_ref") or "").strip()
                if ref:
                    refs.append(ref)
        return refs


class QdrantRestClient:
    """
    Qdrant REST(scroll) 기반 컨텍스트 복원 클라이언트.

    왜 scroll을 쓰는가:
    - point id를 모르는 상태에서 payload filter(sourceChunkRef)로 조회해야 하기 때문.
    """

    def __init__(self, cfg: EvalConfig):
        self.endpoint = (
            f"http://{cfg.qdrant_host}:{cfg.qdrant_port}"
            f"/collections/{cfg.qdrant_collection}/points/scroll"
        )
        self.timeout_sec = cfg.request_timeout_sec
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

    def _scroll(self, body: Dict[str, Any]) -> Dict[str, Any]:
        """Qdrant scroll API 공통 호출."""
        resp = self.session.post(self.endpoint, json=body, timeout=self.timeout_sec)
        if resp.status_code != 200:
            raise RuntimeError(f"qdrant status={resp.status_code} body={resp.text[:500]}")
        return resp.json()

    @staticmethod
    def _extract_text(payload: Dict[str, Any]) -> Optional[str]:
        """
        payload 스키마 편차를 감안해 텍스트 필드를 탐색한다.

        환경에 따라 키 이름이 다를 수 있어 우선순위 목록을 사용한다.
        """

        for key in ("text_segment", "text", "page_content", "content"):
            value = payload.get(key)
            if isinstance(value, str) and value.strip():
                return value
        return None

    def get_chunk_text(self, source_chunk_ref: str, member_id: int) -> Optional[str]:
        """
        sourceChunkRef 1건에 대한 full chunk 텍스트를 반환한다.

        1차: sourceChunkRef + memberId (엄격 매칭)
        2차: sourceChunkRef only (스키마/타입 드리프트 대응)
        """

        # First pass: sourceChunkRef + memberId for strict isolation.
        strict_filter = {
            "must": [
                {"key": "sourceChunkRef", "match": {"value": source_chunk_ref}},
                {"key": "memberId", "match": {"value": member_id}},
            ]
        }
        body = {"limit": 1, "with_payload": True, "with_vector": False, "filter": strict_filter}
        obj = self._scroll(body)
        points = ((obj.get("result") or {}).get("points") or [])
        if points:
            return self._extract_text((points[0].get("payload") or {}))

        # Fallback: sourceChunkRef only, for payload schema drift.
        relaxed_filter = {"must": [{"key": "sourceChunkRef", "match": {"value": source_chunk_ref}}]}
        body = {"limit": 1, "with_payload": True, "with_vector": False, "filter": relaxed_filter}
        obj = self._scroll(body)
        points = ((obj.get("result") or {}).get("points") or [])
        if not points:
            return None
        return self._extract_text((points[0].get("payload") or {}))


class RagasNoRefScorer:
    """
    ragas 무정답 평가 래퍼.

    실제 ragas 호출 지점:
    - 우선 import:
      - from ragas.llms import llm_factory
      - from ragas.metrics import ContextUtilization
    - fallback import:
      - from ragas.metrics import LLMContextPrecisionWithoutReference
    - 실행:
      - metric.single_turn_ascore(sample)
      - 또는 metric.single_turn_score(sample)

    참고:
    - deprecated score API는 ragas 0.2 계열 경고를 유발하므로 사용하지 않는다.

    payload는 ragas가 요구하는 필드명으로 맞춘다:
    - user_input
    - response
    - retrieved_contexts
    """

    def __init__(self, api_key: str, model_name: str):
        # llm_factory resolves provider credentials from OPENAI_API_KEY.
        os.environ["OPENAI_API_KEY"] = api_key

        self.metric_name = "context_precision_no_reference"

        try:
            # 최신 ragas 계열에서 권장되는 컨텍스트 활용도 지표.
            from ragas.llms import llm_factory
            from ragas.metrics import ContextUtilization

            llm = llm_factory(model=model_name)
            self.metric = ContextUtilization(llm=llm)
        except Exception as exc:
            # 구버전 ragas 호환을 위한 레거시 metric fallback.
            from ragas.llms import llm_factory
            from ragas.metrics import LLMContextPrecisionWithoutReference

            self.metric = LLMContextPrecisionWithoutReference(llm=llm_factory(model=model_name))
            self.metric_name = "context_precision_no_reference_legacy"
            self._init_error = str(exc)
        else:
            self._init_error = None
        self.sample_cls = self._resolve_single_turn_sample_cls()

    @staticmethod
    def _resolve_single_turn_sample_cls() -> Optional[Any]:
        """ragas 버전 차이를 감안해 SingleTurnSample import 경로를 해석한다."""
        try:
            from ragas import SingleTurnSample  # type: ignore

            return SingleTurnSample
        except Exception:
            try:
                from ragas.dataset_schema import SingleTurnSample  # type: ignore

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
        질문 1건에 대한 ragas 점수를 계산한다.

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
            message = "SingleTurnSample class is unavailable for installed ragas version"
            if self._init_error:
                message += f" (metric init fallback reason: {self._init_error})"
            raise RuntimeError(message)

        sample = self.sample_cls(**payload)

        if hasattr(self.metric, "single_turn_ascore"):
            # single_turn_ascore는 async API이므로 이벤트 루프에서 실행.
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
    lines.append("# RAGAS Retrieval Evaluation Report")
    lines.append("")
    lines.append(f"- Run ID: `{result['run_id']}`")
    lines.append(f"- Started At (UTC): `{result['started_at']}`")
    lines.append(f"- Finished At (UTC): `{result['finished_at']}`")
    lines.append(f"- Dataset: `{result['dataset_path']}`")
    lines.append(f"- Judge Model: `{result['judge_model']}`")
    lines.append(f"- Metric Label: `{result['metric_name']}`")
    lines.append("")
    lines.append("## Summary")
    lines.append("")
    lines.append(f"- Total: **{summary['total']}**")
    lines.append(f"- Evaluated (`ok`): **{summary['evaluated']}**")
    lines.append(f"- Excluded Failed: **{summary['excluded_failed']}**")
    lines.append(f"- Excluded Irrelevant: **{summary['excluded_irrelevant']}**")
    lines.append(f"- Excluded Context Missing: **{summary['excluded_context_missing']}**")
    score_text = "N/A" if summary["mean_score"] is None else f"{summary['mean_score']:.6f}"
    lines.append(f"- Mean Score: **{score_text}**")
    lines.append(f"- Threshold: **{summary['threshold']:.2f}**")
    lines.append(f"- Pass: **{summary['pass']}**")
    lines.append("")

    ok_samples = [s for s in result["samples"] if s["status"] == STATUS_OK]
    ok_samples = sorted(ok_samples, key=lambda s: s.get("metric_score", 1e9))
    if ok_samples:
        lines.append("## Lowest Scored Samples")
        lines.append("")
        lines.append("| question_id | score | context_count |")
        lines.append("|---|---:|---:|")
        for sample in ok_samples[:10]:
            score = sample.get("metric_score")
            score_value = "N/A" if score is None else f"{score:.6f}"
            lines.append(
                f"| `{sample['question_id']}` | {score_value} | {sample.get('retrieved_contexts_count', 0)} |"
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


def summarize_samples(samples: Sequence[Dict[str, Any]], threshold: float) -> Dict[str, Any]:
    """
    샘플 상태별 집계를 만들고 mean score + pass/fail 판정을 계산한다.
    """

    counts = {
        "total": len(samples),
        "evaluated": 0,
        "excluded_failed": 0,
        "excluded_irrelevant": 0,
        "excluded_context_missing": 0,
    }
    scores: List[float] = []
    for sample in samples:
        status = sample["status"]
        if status == STATUS_OK:
            counts["evaluated"] += 1
            metric_score = sample.get("metric_score")
            if isinstance(metric_score, (int, float)):
                scores.append(float(metric_score))
        elif status == STATUS_EXCLUDED_FAILED:
            counts["excluded_failed"] += 1
        elif status == STATUS_EXCLUDED_IRRELEVANT:
            counts["excluded_irrelevant"] += 1
        elif status == STATUS_EXCLUDED_CONTEXT_MISSING:
            counts["excluded_context_missing"] += 1

    mean_score: Optional[float] = None
    if scores:
        mean_score = float(statistics.fmean(scores))
    passed = bool(mean_score is not None and mean_score >= threshold)

    return {
        **counts,
        "mean_score": mean_score,
        "threshold": threshold,
        "pass": passed,
    }


def evaluate(cfg: EvalConfig) -> Dict[str, Any]:
    """
    메인 평가 루프.

    샘플 1건 처리 순서:
    1) Chat 호출
    2) 최신 history 조회
    3) history status 분기(COMPLETED/IRRELEVANT/기타)
    4) source_chunk_ref 조회
    5) Qdrant full chunk 복원
    6) ragas 점수 계산
    7) 상태/오류를 샘플 결과에 기록
    """

    questions = load_questions(cfg.dataset, cfg.max_samples)
    chat = ChatApiClient(cfg.base_url, cfg.access_token, cfg.request_timeout_sec)
    db = DbReader(cfg)
    qdrant = QdrantRestClient(cfg)
    scorer = RagasNoRefScorer(cfg.ragas_api_key, cfg.judge_model)

    if scorer._init_error:
        print(
            f"[warn] ContextUtilization init failed, using legacy metric fallback: {scorer._init_error}",
            file=sys.stderr,
        )

    samples: List[Dict[str, Any]] = []
    started = utc_now()

    try:
        for idx, item in enumerate(questions, start=1):
            if cfg.verbose:
                print(f"[{idx}/{len(questions)}] {item.question_id}")

            # 리포트에 그대로 들어가는 per-sample raw 레코드.
            base = {
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
                "chat_session_id": None,
                "chat_history_id": None,
                "history_status": None,
            }

            # Step 1) Chat 호출
            session_id, response, chat_error = chat.send(item.question)
            if chat_error:
                base["status"] = STATUS_EXCLUDED_FAILED
                base["error"] = chat_error
                samples.append(base)
                continue

            base["chat_session_id"] = session_id
            base["response"] = response

            # Step 2) 최신 history 조회
            history = db.latest_history(cfg.member_id, session_id or "")
            if not history:
                base["status"] = STATUS_EXCLUDED_FAILED
                base["error"] = "latest history not found after chat request"
                samples.append(base)
                continue

            history_id = int(history["id"])
            history_status = str(history.get("status") or "").upper()
            answer_text = str(history.get("answer") or "")
            base["chat_history_id"] = history_id
            base["history_status"] = history_status
            if not base["response"] and answer_text:
                base["response"] = answer_text

            # Step 3) 상태 분기: IRRELEVANT는 점수 제외(집계만)
            if history_status == "IRRELEVANT":
                base["status"] = STATUS_EXCLUDED_IRRELEVANT
                base["error"] = "history status is IRRELEVANT"
                samples.append(base)
                continue
            if history_status != "COMPLETED":
                base["status"] = STATUS_EXCLUDED_FAILED
                base["error"] = f"history status is {history_status or 'UNKNOWN'}"
                samples.append(base)
                continue

            # Step 4) source_chunk_ref 확보
            refs = db.source_chunk_refs(history_id)
            base["retrieved_context_ids"] = refs
            if not refs:
                base["status"] = STATUS_EXCLUDED_CONTEXT_MISSING
                base["error"] = "no source_chunk_ref found for completed history"
                samples.append(base)
                continue

            contexts: List[str] = []
            resolved_refs: List[str] = []
            missing_refs: List[str] = []
            # Step 5) sourceChunkRef -> full chunk text 복원
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

            # Step 6) ragas 점수 계산
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

    # Step 7) 전체 요약
    finished = utc_now()
    summary = summarize_samples(samples, cfg.threshold)
    run_id = slug_ts(started)
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
        "samples_requested": cfg.max_samples,
        "summary": summary,
        "samples": samples,
    }


def write_reports(result: Dict[str, Any], report_root: Path) -> Tuple[Path, Path]:
    """실행 결과를 JSON + Markdown 두 가지 형식으로 저장한다."""

    run_dir = report_root / result["run_id"]
    run_dir.mkdir(parents=True, exist_ok=True)

    json_path = run_dir / "result.json"
    md_path = run_dir / "summary.md"

    with json_path.open("w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    with md_path.open("w", encoding="utf-8") as f:
        f.write(build_report_markdown(result))

    return json_path, md_path


def main() -> int:
    """CLI 진입점."""

    try:
        args = parse_args()
        cfg = build_config(args)
        result = evaluate(cfg)
        json_path, md_path = write_reports(result, cfg.report_dir)
    except Exception as exc:
        print(f"[error] {exc}", file=sys.stderr)
        return 1

    summary = result["summary"]
    score = summary["mean_score"]
    score_text = "N/A" if score is None else f"{score:.6f}"
    print(f"run_id={result['run_id']}")
    print(f"score={score_text} threshold={summary['threshold']:.2f} pass={summary['pass']}")
    print(f"evaluated={summary['evaluated']} total={summary['total']}")
    print(f"json={json_path}")
    print(f"markdown={md_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
