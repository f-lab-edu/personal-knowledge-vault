#!/usr/bin/env python3
"""
Seed evaluation data for RAGAS eval scripts.

이 스크립트는 eval 환경을 자동으로 구성한다:
1) Flyway로 생성된 eval 멤버의 member_id를 DB에서 조회
2) JWT_SECRET으로 access_token 생성 (PyJWT)
3) 기존 문서 삭제 (Qdrant + S3 + DB 정리)
4) PDF 파일들을 presign → S3 PUT → confirm API로 업로드
5) GET /api/documents 폴링 → 전체 COMPLETED 대기
6) PKV_ACCESS_TOKEN을 .env.local에 기록

Cleanup mode (--cleanup):
- 업로드된 문서를 DELETE API로 삭제 (Qdrant + S3 + DB 정리 포함)
- .env.local에서 PKV_ACCESS_TOKEN/PKV_MEMBER_ID 제거
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import jwt
import pymysql
import requests
from dotenv import load_dotenv


EVAL_EMAIL_DEFAULT = "eval-bot@pkv.test"
EVAL_NAME_DEFAULT = "Eval Bot"

BOOK_FILES = [
    "java-spec-21-1-400.pdf",
    "java-spec-21-400-872.pdf",
    "spring-auth.pdf",
    "spring-boot-reference-1-500.pdf",
    "spring-boot-reference-500-977.pdf",
    "spring-framework-1-500.pdf",
    "spring-framework-500-1000.pdf",
    "spring-framework-1000-1471.pdf",
    "springpronote.pdf",
    "spring-data-jpa-reference.pdf",
]


@dataclass
class SeedConfig:
    base_url: str
    email: str
    name: str
    poll_interval: int
    poll_timeout: int
    cleanup: bool
    env_output: Path
    verbose: bool
    docs_dir: Optional[Path]
    jwt_secret: str
    db_host: str
    db_port: int
    db_name: str
    db_user: str
    db_password: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Seed evaluation data for RAGAS eval scripts.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--docs-dir",
        type=Path,
        default=None,
        help="Directory containing PDF files to upload.",
    )
    parser.add_argument(
        "--base-url",
        default=os.getenv("PKV_BASE_URL", "http://localhost:8080"),
        help="PKV API base URL.",
    )
    parser.add_argument("--email", default=EVAL_EMAIL_DEFAULT, help="Eval member email.")
    parser.add_argument("--name", default=EVAL_NAME_DEFAULT, help="Eval member name.")
    parser.add_argument(
        "--poll-interval", type=int, default=10, help="Seconds between polling."
    )
    parser.add_argument(
        "--poll-timeout",
        type=int,
        default=600,
        help="Max seconds to wait for embeddings.",
    )
    parser.add_argument(
        "--cleanup", action="store_true", help="Delete seeded data instead of creating."
    )
    parser.add_argument(
        "--env-output",
        type=Path,
        default=Path("scripts/ragas/.env.local"),
        help="Path to write PKV_ACCESS_TOKEN.",
    )
    parser.add_argument("--verbose", action="store_true", help="Print progress logs.")
    return parser.parse_args()


def require_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise ValueError(f"Missing required env var: {name}")
    return value


def build_config(args: argparse.Namespace) -> SeedConfig:
    load_dotenv(".env")
    load_dotenv(".env.local")
    load_dotenv("scripts/ragas/.env")
    load_dotenv("scripts/ragas/.env.local", override=True)

    jwt_secret = require_env("JWT_SECRET")

    return SeedConfig(
        base_url=args.base_url.rstrip("/"),
        email=args.email,
        name=args.name,
        poll_interval=args.poll_interval,
        poll_timeout=args.poll_timeout,
        cleanup=args.cleanup,
        env_output=args.env_output,
        verbose=args.verbose,
        docs_dir=args.docs_dir,
        jwt_secret=jwt_secret,
        db_host=os.getenv("DB_HOST", "localhost"),
        db_port=int(os.getenv("DB_PORT", "3306")),
        db_name=os.getenv("DB_NAME", "pkv"),
        db_user=os.getenv("DB_USERNAME", "root"),
        db_password=os.getenv("DB_PASSWORD", "root"),
    )


# ---------------------------------------------------------------------------
# DB: member lookup
# ---------------------------------------------------------------------------


def lookup_member_id(cfg: SeedConfig) -> int:
    """Flyway V6 마이그레이션으로 생성된 eval 멤버의 id를 조회한다."""
    conn = pymysql.connect(
        host=cfg.db_host,
        port=cfg.db_port,
        user=cfg.db_user,
        password=cfg.db_password,
        database=cfg.db_name,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT id FROM users WHERE email = %s AND deleted_at IS NULL",
                (cfg.email,),
            )
            row = cur.fetchone()
            if not row:
                raise RuntimeError(
                    f"Eval member not found (email={cfg.email}). "
                    "Ensure the server has run at least once so Flyway V6 migration is applied."
                )
            return int(row["id"])
    finally:
        conn.close()


def delete_member(cfg: SeedConfig, member_id: int) -> None:
    """eval 멤버를 soft-delete 한다."""
    conn = pymysql.connect(
        host=cfg.db_host,
        port=cfg.db_port,
        user=cfg.db_user,
        password=cfg.db_password,
        database=cfg.db_name,
        charset="utf8mb4",
        autocommit=True,
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE users SET deleted_at = NOW(6) WHERE id = %s",
                (member_id,),
            )
    finally:
        conn.close()


# ---------------------------------------------------------------------------
# JWT
# ---------------------------------------------------------------------------


def generate_access_token(member_id: int, email: str, jwt_secret: str) -> str:
    """JwtTokenProvider.createAccessToken()과 동일한 JWT를 생성한다."""
    now = int(time.time())
    payload = {
        "sub": str(member_id),
        "email": email,
        "type": "access",
        "iat": now,
        "exp": now + 86400,  # 24h for eval convenience
    }
    return jwt.encode(payload, jwt_secret, algorithm="HS256")


# ---------------------------------------------------------------------------
# API client
# ---------------------------------------------------------------------------


class PkvApiClient:
    """presign / confirm / documents / delete API 클라이언트."""

    def __init__(self, base_url: str, access_token: str, timeout_sec: int = 30):
        self.base_url = base_url
        self.timeout_sec = timeout_sec
        self.session = requests.Session()
        self.session.headers.update(
            {"Content-Type": "application/json", "Accept": "application/json"}
        )
        self.session.cookies.set("access_token", access_token, path="/api")

    def _check(self, resp: requests.Response, context: str) -> Dict[str, Any]:
        if resp.status_code != 200:
            raise RuntimeError(
                f"{context}: status={resp.status_code} body={resp.text[:500]}"
            )
        body = resp.json()
        if not body.get("success", False):
            error = body.get("error") or {}
            raise RuntimeError(
                f"{context}: code={error.get('code')} message={error.get('message')}"
            )
        return body.get("data") or {}

    def get_documents(self) -> List[Dict[str, Any]]:
        resp = self.session.get(
            f"{self.base_url}/api/documents", timeout=self.timeout_sec
        )
        body = resp.json()
        if not body.get("success", False):
            return []
        return body.get("data") or []

    def presign(self, file_name: str, file_size: int) -> Tuple[int, str]:
        resp = self.session.post(
            f"{self.base_url}/api/documents/presign",
            json={"fileName": file_name, "fileSize": file_size},
            timeout=self.timeout_sec,
        )
        data = self._check(resp, f"presign({file_name})")
        return int(data["documentId"]), str(data["presignedUrl"])

    def upload_to_s3(self, presigned_url: str, file_bytes: bytes) -> None:
        resp = requests.put(
            presigned_url,
            data=file_bytes,
            headers={"Content-Type": "application/pdf"},
            timeout=120,
        )
        if resp.status_code not in (200, 204):
            raise RuntimeError(
                f"S3 upload failed: status={resp.status_code} body={resp.text[:500]}"
            )

    def confirm(self, document_id: int) -> Dict[str, Any]:
        resp = self.session.post(
            f"{self.base_url}/api/documents/{document_id}/confirm",
            timeout=self.timeout_sec,
        )
        return self._check(resp, f"confirm({document_id})")

    def delete_document(self, document_id: int) -> None:
        resp = self.session.delete(
            f"{self.base_url}/api/documents/{document_id}",
            timeout=self.timeout_sec,
        )
        if resp.status_code != 200:
            raise RuntimeError(
                f"delete({document_id}): status={resp.status_code} body={resp.text[:500]}"
            )


# ---------------------------------------------------------------------------
# Seed flow
# ---------------------------------------------------------------------------


def find_missing_files(docs_dir: Path) -> List[str]:
    """docs_dir에 없는 BOOK_FILES를 반환한다."""
    missing = []
    for name in BOOK_FILES:
        if not (docs_dir / name).exists():
            missing.append(name)
    return missing


def delete_existing_documents(cfg: SeedConfig, client: PkvApiClient) -> int:
    """기존 문서를 모두 삭제한다. 삭제 건수를 반환한다."""
    docs = client.get_documents()
    if not docs:
        return 0

    deleted = 0
    for doc in docs:
        doc_id = doc.get("documentId") or doc.get("id")
        status = doc.get("status", "")
        name = doc.get("originalFileName", "?")
        if status in ("COMPLETED", "FAILED"):
            try:
                client.delete_document(int(doc_id))
                deleted += 1
                if cfg.verbose:
                    print(f"  deleted: {name} (id={doc_id})")
            except Exception as exc:
                print(f"  [warn] failed to delete {name}: {exc}", file=sys.stderr)
    return deleted


def upload_documents(
    cfg: SeedConfig, client: PkvApiClient, docs_dir: Path
) -> List[str]:
    """
    PDF 파일들을 presign → S3 PUT → confirm 순서로 업로드한다.

    Returns: 업로드한 파일 이름 목록.
    """
    uploaded: List[str] = []

    for idx, file_name in enumerate(BOOK_FILES, start=1):
        file_path = docs_dir / file_name
        file_size = file_path.stat().st_size

        if cfg.verbose:
            print(
                f"  [{idx}/{len(BOOK_FILES)}] uploading: {file_name} "
                f"({file_size / 1024 / 1024:.1f} MB)"
            )

        doc_id, presigned_url = client.presign(file_name, file_size)
        file_bytes = file_path.read_bytes()
        client.upload_to_s3(presigned_url, file_bytes)
        client.confirm(doc_id)
        uploaded.append(file_name)

        if cfg.verbose:
            print(f"           confirmed: documentId={doc_id}")

    return uploaded


def poll_until_complete(
    cfg: SeedConfig, client: PkvApiClient, total_expected: int
) -> bool:
    """
    모든 문서가 COMPLETED 또는 FAILED가 될 때까지 폴링한다.

    Returns: 모든 문서가 COMPLETED이면 True, FAILED가 있으면 False.
    """
    start = time.time()
    poll_count = 0

    while True:
        elapsed = time.time() - start
        if elapsed > cfg.poll_timeout:
            print(
                f"[timeout] {cfg.poll_timeout}s exceeded. "
                "Some documents may still be processing.",
                file=sys.stderr,
            )
            return False

        docs = client.get_documents()
        completed = [d for d in docs if d.get("status") == "COMPLETED"]
        failed = [d for d in docs if d.get("status") == "FAILED"]
        processing = [
            d
            for d in docs
            if d.get("status") in ("UPLOADED", "PROCESSING")
        ]

        poll_count += 1
        if cfg.verbose:
            print(
                f"  [poll {poll_count}] "
                f"{len(completed)}/{total_expected} COMPLETED, "
                f"{len(failed)}/{total_expected} FAILED, "
                f"{len(processing)}/{total_expected} PROCESSING"
            )

        if len(completed) + len(failed) >= total_expected:
            if failed:
                names = [d.get("originalFileName", "?") for d in failed]
                print(
                    f"[warn] {len(failed)} document(s) FAILED: {names}",
                    file=sys.stderr,
                )
                return False
            return True

        time.sleep(cfg.poll_interval)


def write_env_output(cfg: SeedConfig, token: str, member_id: int) -> None:
    """PKV_ACCESS_TOKEN과 PKV_MEMBER_ID를 env 파일에 기록한다."""
    lines: List[str] = []
    if cfg.env_output.exists():
        lines = cfg.env_output.read_text(encoding="utf-8").splitlines()

    # Replace or append
    token_written = False
    member_id_written = False
    for i, line in enumerate(lines):
        if line.startswith("PKV_ACCESS_TOKEN="):
            lines[i] = f"PKV_ACCESS_TOKEN={token}"
            token_written = True
        elif line.startswith("PKV_MEMBER_ID="):
            lines[i] = f"PKV_MEMBER_ID={member_id}"
            member_id_written = True

    if not token_written:
        lines.append(f"PKV_ACCESS_TOKEN={token}")
    if not member_id_written:
        lines.append(f"PKV_MEMBER_ID={member_id}")

    cfg.env_output.parent.mkdir(parents=True, exist_ok=True)
    cfg.env_output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def remove_env_keys(cfg: SeedConfig) -> None:
    """env 파일에서 PKV_ACCESS_TOKEN과 PKV_MEMBER_ID를 제거한다."""
    if not cfg.env_output.exists():
        return
    lines = cfg.env_output.read_text(encoding="utf-8").splitlines()
    filtered = [
        line
        for line in lines
        if not line.startswith("PKV_ACCESS_TOKEN=")
        and not line.startswith("PKV_MEMBER_ID=")
    ]
    cfg.env_output.write_text("\n".join(filtered) + "\n", encoding="utf-8")


# ---------------------------------------------------------------------------
# Cleanup flow
# ---------------------------------------------------------------------------


def cleanup(cfg: SeedConfig) -> int:
    """시딩 데이터를 삭제한다."""
    print("[cleanup] looking up eval member...")
    try:
        member_id = lookup_member_id(cfg)
    except RuntimeError:
        print("[cleanup] eval member not found, nothing to clean up.")
        remove_env_keys(cfg)
        return 0

    token = generate_access_token(member_id, cfg.email, cfg.jwt_secret)
    client = PkvApiClient(cfg.base_url, token)

    docs = client.get_documents()
    print(f"[cleanup] found {len(docs)} document(s)")

    deleted = 0
    for doc in docs:
        doc_id = doc.get("documentId") or doc.get("id")
        status = doc.get("status", "")
        name = doc.get("originalFileName", "?")
        if status in ("COMPLETED", "FAILED"):
            try:
                client.delete_document(int(doc_id))
                deleted += 1
                if cfg.verbose:
                    print(f"  deleted: {name} (id={doc_id})")
            except Exception as exc:
                print(f"  [warn] failed to delete {name}: {exc}", file=sys.stderr)

    print(f"[cleanup] deleted {deleted} document(s)")

    delete_member(cfg, member_id)
    print(f"[cleanup] soft-deleted eval member (id={member_id})")

    remove_env_keys(cfg)
    print(f"[cleanup] removed tokens from {cfg.env_output}")

    return 0


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main() -> int:
    args = parse_args()

    try:
        cfg = build_config(args)
    except Exception as exc:
        print(f"[error] config: {exc}", file=sys.stderr)
        return 1

    if cfg.cleanup:
        return cleanup(cfg)

    # Seed mode requires --docs-dir
    if not cfg.docs_dir:
        print("[error] --docs-dir is required for seeding.", file=sys.stderr)
        return 1

    if not cfg.docs_dir.is_dir():
        print(f"[error] --docs-dir not found: {cfg.docs_dir}", file=sys.stderr)
        return 1

    missing = find_missing_files(cfg.docs_dir)
    if missing:
        print(f"[error] missing PDF files in {cfg.docs_dir}:", file=sys.stderr)
        for name in missing:
            print(f"  - {name}", file=sys.stderr)
        return 1

    # Step 1: lookup eval member
    print("[1/6] looking up eval member...")
    try:
        member_id = lookup_member_id(cfg)
    except Exception as exc:
        print(f"[error] {exc}", file=sys.stderr)
        return 1
    print(f"       member_id={member_id} email={cfg.email}")

    # Step 2: generate JWT
    print("[2/6] generating access token...")
    token = generate_access_token(member_id, cfg.email, cfg.jwt_secret)
    print(f"       token expires in 24h")

    # Step 3: delete existing documents
    print("[3/6] deleting existing documents...")
    client = PkvApiClient(cfg.base_url, token)
    deleted_count = delete_existing_documents(cfg, client)
    print(f"       deleted {deleted_count} document(s)")

    # Step 4: upload documents
    print("[4/6] uploading documents...")
    try:
        newly_uploaded = upload_documents(cfg, client, cfg.docs_dir)
    except Exception as exc:
        print(f"[error] upload failed: {exc}", file=sys.stderr)
        return 1
    print(f"       uploaded {len(newly_uploaded)} document(s)")

    # Step 5: poll for completion
    print("[5/6] waiting for embeddings...")
    success = poll_until_complete(cfg, client, len(BOOK_FILES))
    if not success:
        print("[warn] not all documents completed successfully", file=sys.stderr)

    # Step 6: write env output
    print("[6/6] writing env output...")
    write_env_output(cfg, token, member_id)
    print(f"       written to {cfg.env_output}")

    print()
    print(f"PKV_ACCESS_TOKEN={token}")
    print(f"PKV_MEMBER_ID={member_id}")

    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())
