# RAGAS Evaluation Scripts

`ragas` 기반 LLM judge 평가 스크립트 모음입니다.

## 스크립트 목록

| 스크립트 | 메트릭 | 평가 대상 | API |
|---|---|---|---|
| `run_context_precision_eval.py` | ContextPrecision (무정답) | retrieval 품질 — 검색된 컨텍스트가 질문에 관련있는가 | `POST /api/threads/turns` |
| `run_faithfulness_eval.py` | Faithfulness (무정답) | 답변 충실도 — 답변이 컨텍스트에 근거하는가 (환각 탐지) | `POST /api/threads/turns` |
| `seed_eval_data.py` | — | eval 데이터 시딩 — 멤버 조회, JWT 생성, 기존 문서 삭제, 문서 업로드, 임베딩 대기 | `POST /api/documents/*`, `DELETE /api/documents/*` |

## Files

- 시딩: `scripts/ragas/seed_eval_data.py`
- 실행기: `scripts/ragas/run_context_precision_eval.py`, `scripts/ragas/run_faithfulness_eval.py`
- 의존성: `scripts/ragas/requirements.txt`
- 질문셋: `scripts/ragas/datasets/*.jsonl`
- 리포트: `scripts/ragas/reports/<run_id>/result.json`, `summary.md`
  - run_id 형식: `{timestamp}_{metric}_{prompt_label}` (예: `20260321_043648_faithfulness_baseline`, `20260321_043648_context_precision_v2-strict`)

## Dataset format (JSONL)

한 줄에 한 샘플:

```json
{"question_id":"q-001","question":"운영체제의 가상 메모리 개념을 설명해줘"}
```

필수 필드:

- `question_id` (중복 금지)
- `question`

## Required env vars

- `PKV_ACCESS_TOKEN`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_COLLECTION`

API key resolution:

- 우선 `RAGAS_OPENAI_API_KEY`
- 없으면 `CHAT_MODEL_API_KEY` 자동 fallback

권장 설정 파일:

- `scripts/ragas/.env.local` (자동 로드됨)
- 템플릿: `scripts/ragas/.env.example`

Optional:

- `PKV_MEMBER_ID` (없으면 `PKV_ACCESS_TOKEN` JWT payload의 `sub` 사용)
- `RAGAS_JUDGE_MODEL` (default: `gpt-4o-mini`)
- `PKV_BASE_URL` (default: `http://localhost:8080`)

## Run

테스트 실행 전 준비:

- `scripts/ragas/.env.example`를 참고해 `scripts/ragas/.env.local` 작성
- 필수값: `PKV_ACCESS_TOKEN` (수동 입력 또는 `seed_eval_data.py`로 자동 생성)

```bash
python3 -m venv scripts/ragas/.venv
source scripts/ragas/.venv/bin/activate
pip install -r scripts/ragas/requirements.txt
```

### Eval 데이터 시딩 (문서 업로드 + 토큰 생성)

eval 환경을 처음 세팅하거나 초기화할 때 사용합니다.

전제 조건:
- 인프라 실행 중 (MySQL, Kafka, Qdrant, S3, Spring Boot)
- Flyway V6 마이그레이션 적용 완료 (서버 최소 1회 시작)
- `.env.local`에 `JWT_SECRET` 설정 (서버와 동일한 값)

```bash
# 시딩: 기존 문서 삭제 → PDF 업로드 → 임베딩 대기 → .env.local에 토큰 기록
python3 scripts/ragas/seed_eval_data.py \
  --docs-dir ~/path/to/pdfs \
  --verbose

# 정리: 업로드된 문서 삭제 + 멤버 soft-delete
python3 scripts/ragas/seed_eval_data.py --cleanup --verbose
```

시딩은 매번 기존 문서를 삭제하고 새로 업로드합니다. 완료 후 `scripts/ragas/.env.local`에 `PKV_ACCESS_TOKEN`과 `PKV_MEMBER_ID`가 자동 기록되어 eval 스크립트를 바로 실행할 수 있습니다.

### Context Precision 평가 (retrieval 품질)

```bash
python3 scripts/ragas/run_context_precision_eval.py \
  --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
  --max-samples 50 \
  --threshold 0.75 \
  --base-url http://localhost:8080 \
  --report-dir scripts/ragas/reports \
  --prompt-label baseline \
  --verbose
```

### Faithfulness 평가 (답변 충실도 / 환각 탐지)

```bash
python3 scripts/ragas/run_faithfulness_eval.py \
  --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
  --max-samples 50 \
  --threshold 0.70 \
  --base-url http://localhost:8080 \
  --report-dir scripts/ragas/reports \
  --prompt-label baseline \
  --verbose
```

### 프롬프트 A/B 테스트

프롬프트 변경 전/후 점수를 비교해 개선 여부를 확인합니다.

```bash
# 1) baseline 측정
python3 scripts/ragas/run_faithfulness_eval.py \
  --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
  --prompt-label baseline --verbose

python3 scripts/ragas/run_context_precision_eval.py \
  --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
  --prompt-label baseline --verbose

# 2) 프롬프트 수정 (system.prompt.md 등) 후 서버 재시작

# 3) 변경된 프롬프트로 재측정
python3 scripts/ragas/run_faithfulness_eval.py \
  --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
  --prompt-label v2-strict --verbose

python3 scripts/ragas/run_context_precision_eval.py \
  --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
  --prompt-label v2-strict --verbose

# 4) reports/ 디렉토리에서 두 리포트의 mean score 비교
```

## Notes

- `RAGAS_OPENAI_API_KEY`는 평가 전용 키를 권장합니다.
- Qdrant payload는 `sourceChunkRef`/`text_segment` 키를 우선 사용하고, 스키마 차이를 위해 일부 fallback을 포함합니다.
- Faithfulness 평가는 질문당 LLM judge 호출 2회(claim 분해 + NLI 검증)가 발생합니다. 40개 질문 기준 약 80회 호출.
