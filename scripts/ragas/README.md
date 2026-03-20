# RAGAS Retrieval Eval (No Reference)

`ragas` 기반으로 retrieval context precision(무정답 LLM judge)을 원샷으로 평가합니다.

## Scope

- 대상 API: `POST /api/chat/messages`
- 세션 전략: 질문마다 `sessionId=null` (독립 평가)
- 컨텍스트 복원: `chat_history_sources.source_chunk_ref` -> Qdrant full chunk
- 점수 대상: `COMPLETED` + full chunk 복원 성공 샘플만
- 제외 집계: `excluded_failed`, `excluded_irrelevant`, `excluded_context_missing`

## Files

- 실행기: `scripts/ragas/run_eval.py`
- 의존성: `scripts/ragas/requirements.txt`
- 질문셋: `scripts/ragas/datasets/*.jsonl`
- 리포트: `scripts/ragas/reports/<run_id>/result.json`, `summary.md`

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
- 필수값: `PKV_ACCESS_TOKEN`

```bash
python3 -m venv scripts/ragas/.venv
source scripts/ragas/.venv/bin/activate
pip install -r scripts/ragas/requirements.txt

python3 scripts/ragas/run_eval.py \
  --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
  --max-samples 50 \
  --threshold 0.75 \
  --base-url http://localhost:8080 \
  --report-dir scripts/ragas/reports \
  --verbose
```

## Notes

- `RAGAS_OPENAI_API_KEY`는 평가 전용 키를 권장합니다.
- Qdrant payload는 `sourceChunkRef`/`text_segment` 키를 우선 사용하고, 스키마 차이를 위해 일부 fallback을 포함합니다.
