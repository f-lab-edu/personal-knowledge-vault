"""
RAGAS 평가 스크립트 공통 모듈.

두 개별 평가 스크립트(run_context_precision_eval.py, run_faithfulness_eval.py)와
통합 벤치마크 러너(run_benchmark.py)가 공유하는 인프라 코드를 모은다.

포함 항목:
- 상수 (STATUS_OK, STATUS_EXCLUDED_*)
- 데이터 클래스 (EvalConfig, QuestionItem)
- 유틸리티 함수 (시간, JWT, env, 프롬프트 스냅샷)
- 인프라 클라이언트 (ChatApiClient, DbReader, QdrantRestClient)
- 공통 로직 (load_questions, summarize_samples, build_config, write_reports)
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import statistics
import textwrap
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

import pymysql
import requests
from dotenv import load_dotenv


# ---------------------------------------------------------------------------
# 상수
# ---------------------------------------------------------------------------

STATUS_OK = "ok"
STATUS_EXCLUDED_FAILED = "excluded_failed"
STATUS_EXCLUDED_IRRELEVANT = "excluded_irrelevant"
STATUS_EXCLUDED_CONTEXT_MISSING = "excluded_context_missing"

PROMPT_DIR = Path("src/main/resources/prompts/chat")
PROMPT_FILES = [
    "system.prompt.md",
    "user_with_context.prompt.md",
    "user_without_context.prompt.md",
]


# ---------------------------------------------------------------------------
# 데이터 클래스
# ---------------------------------------------------------------------------

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
    prompt_label: Optional[str]
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


# ---------------------------------------------------------------------------
# 유틸리티 함수
# ---------------------------------------------------------------------------

def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def iso_ts(dt: Optional[datetime] = None) -> str:
    value = dt or utc_now()
    return value.replace(microsecond=0).isoformat()


def slug_ts(dt: Optional[datetime] = None) -> str:
    value = dt or utc_now()
    kst = value.astimezone(timezone(timedelta(hours=9)))
    return kst.strftime("%Y%m%d_%H%M")


def load_prompt_snapshot() -> Dict[str, str]:
    """평가 시점의 프롬프트 템플릿 전문을 읽어 반환한다."""
    snapshot = {}
    for name in PROMPT_FILES:
        path = PROMPT_DIR / name
        if path.exists():
            snapshot[name] = path.read_text(encoding="utf-8").strip()
        else:
            snapshot[name] = "(file not found)"
    return snapshot


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


def load_dotenvs() -> None:
    """4개 .env 파일을 우선순위 순서대로 로드한다."""
    load_dotenv(".env")
    load_dotenv(".env.local", override=True)
    load_dotenv("scripts/ragas/.env", override=True)
    load_dotenv("scripts/ragas/.env.local", override=True)


# ---------------------------------------------------------------------------
# 질문 로더
# ---------------------------------------------------------------------------

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


# ---------------------------------------------------------------------------
# 인프라 클라이언트
# ---------------------------------------------------------------------------

class ChatApiClient:
    """
    PKV Thread Turn API 호출 전용 클라이언트.

    POST /api/threads/turns 엔드포인트를 사용한다.
    응답에 status, answer, citations가 포함되어 있어 별도 DB 조회 없이
    기본 정보를 획득할 수 있다.
    """

    def __init__(self, base_url: str, access_token: str, timeout_sec: int):
        self.endpoint = f"{base_url}/api/threads/turns"
        self.timeout_sec = timeout_sec
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Content-Type": "application/json",
                "Accept": "application/json",
            }
        )
        self.session.cookies.set("access_token", access_token, path="/api")

    def send(
        self, question: str
    ) -> Tuple[Optional[str], Optional[int], Optional[str], Optional[str], Optional[str]]:
        """
        질문 1건을 전송한다.

        Returns:
        - thread_id
        - turn_id
        - answer
        - status ("COMPLETED", "IRRELEVANT", etc.)
        - error_message (성공 시 None)
        """

        payload = {"threadId": None, "prompt": question}
        try:
            resp = self.session.post(self.endpoint, json=payload, timeout=self.timeout_sec)
        except Exception as exc:
            return None, None, None, None, f"chat request failed: {exc}"

        if resp.status_code != 200:
            return None, None, None, None, f"chat status={resp.status_code} body={resp.text[:500]}"

        try:
            body = resp.json()
        except ValueError as exc:
            return None, None, None, None, f"chat response is not JSON: {exc}"

        if not body.get("success", False):
            error = body.get("error") or {}
            code = error.get("code", "unknown")
            message = error.get("message", "unknown error")
            return None, None, None, None, f"chat api error {code}: {message}"

        data = body.get("data") or {}
        thread_id = data.get("threadId")
        turn_id = data.get("turnId")
        answer = data.get("answer")
        status = data.get("status")

        if not thread_id:
            return None, None, None, None, "chat response missing threadId"
        if turn_id is None:
            return None, None, None, None, "chat response missing turnId"

        return str(thread_id), int(turn_id), answer or "", status or "", None


class DbReader:
    """
    평가용 read-only DB 조회 유틸.

    turn_citations 테이블에서 source_chunk_ref를 조회한다.
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

    def turn_citation_refs(self, turn_id: int) -> List[str]:
        """
        display_order 순으로 source_chunk_ref를 반환한다.

        이후 Qdrant에서 full chunk 텍스트를 복원하는 키로 사용한다.
        """

        sql = textwrap.dedent(
            """
            SELECT source_chunk_ref
            FROM turn_citations
            WHERE turn_id = %s
            ORDER BY display_order ASC
            """
        )
        refs: List[str] = []
        with self.conn.cursor() as cur:
            cur.execute(sql, (turn_id,))
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

        relaxed_filter = {"must": [{"key": "sourceChunkRef", "match": {"value": source_chunk_ref}}]}
        body = {"limit": 1, "with_payload": True, "with_vector": False, "filter": relaxed_filter}
        obj = self._scroll(body)
        points = ((obj.get("result") or {}).get("points") or [])
        if not points:
            return None
        return self._extract_text((points[0].get("payload") or {}))


# ---------------------------------------------------------------------------
# 집계
# ---------------------------------------------------------------------------

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


# ---------------------------------------------------------------------------
# 설정 빌드
# ---------------------------------------------------------------------------

def add_common_args(parser: argparse.ArgumentParser) -> None:
    """개별 평가 스크립트와 벤치마크 러너가 공유하는 CLI 인자를 등록한다."""

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
        "--prompt-label",
        default=None,
        help="Human-readable label for the prompt variant (e.g., 'baseline', 'v2-strict').",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print per-sample progress logs.",
    )


def build_config_from_args(args: argparse.Namespace, threshold: float) -> EvalConfig:
    """
    CLI 인자 + .env/.env.local + 프로세스 env를 병합해 최종 설정을 만든다.

    threshold는 메트릭별로 다르므로 별도 인자로 받는다.
    """

    load_dotenvs()

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
        threshold=threshold,
        judge_model=args.judge_model,
        request_timeout_sec=args.request_timeout_sec,
        verbose=args.verbose,
        prompt_label=args.prompt_label,
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


# ---------------------------------------------------------------------------
# 리포트 저장
# ---------------------------------------------------------------------------

def write_reports(
    result: Dict[str, Any],
    report_root: Path,
    build_markdown_fn: Any,
) -> Tuple[Path, Path]:
    """실행 결과를 JSON + Markdown 두 가지 형식으로 저장한다."""

    run_dir = report_root / result["run_id"]
    run_dir.mkdir(parents=True, exist_ok=True)

    json_path = run_dir / "result.json"
    md_path = run_dir / "summary.md"

    with json_path.open("w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    with md_path.open("w", encoding="utf-8") as f:
        f.write(build_markdown_fn(result))

    return json_path, md_path
