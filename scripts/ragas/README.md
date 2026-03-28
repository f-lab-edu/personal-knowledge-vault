# RAGAS Retrieval Eval

`ragas` 기반 retrieval 품질 평가 파이프라인. Context Precision(무정답)과 Faithfulness 두 메트릭을 지원한다.

## Scope

- 대상 API: `POST /api/chat/messages`
- 세션 전략: 질문마다 `sessionId=null` (독립 평가)
- 컨텍스트 복원: `chat_history_sources.source_chunk_ref` → Qdrant full chunk
- 점수 대상: `COMPLETED` + full chunk 복원 성공 샘플만
- 제외 집계: `excluded_failed`, `excluded_irrelevant`, `excluded_context_missing`

## Files

| 파일 | 설명 |
|------|------|
| `eval_common.py` | 공통 설정, CLI 파서, 유틸리티 |
| `run_benchmark.py` | 멀티라운드 벤치마크 러너 (주력) |
| `run_eval.py` | 단일 메트릭 원샷 평가 |
| `run_context_precision_eval.py` | Context Precision scorer |
| `run_faithfulness_eval.py` | Faithfulness scorer |
| `seed_eval_data.py` | 평가용 질문셋 생성 |
| `datasets/retrieval_eval.jsonl` | 질문셋 |
| `reports/` | 실행 결과 저장 디렉토리 |

## Dataset format (JSONL)

```json
{"question_id":"q-001","question":"운영체제의 가상 메모리 개념을 설명해줘"}
```

필수 필드: `question_id` (중복 금지), `question`

## 환경 설정

`scripts/ragas/.env.example`을 참고해 `scripts/ragas/.env.local`을 작성한다.

필수:

- `PKV_ACCESS_TOKEN` — JWT 토큰
- `CHAT_MODEL_API_KEY` — OpenAI API key (또는 `RAGAS_OPENAI_API_KEY`)
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_COLLECTION`

선택:

- `PKV_MEMBER_ID` — 생략 시 JWT의 `sub` 사용
- `RAGAS_JUDGE_MODEL` — 기본값 `gpt-4o-mini`
- `PKV_BASE_URL` — 기본값 `http://localhost:8080`

## 실행

### 초기 셋업

```bash
python3 -m venv scripts/ragas/.venv
source scripts/ragas/.venv/bin/activate
pip install -r scripts/ragas/requirements.txt
```

### 벤치마크 (멀티라운드)

```bash
cd /Users/han/Desktop/pkv/personal-knowledge-vault && source scripts/ragas/.venv/bin/activate && python scripts/ragas/run_benchmark.py --dataset scripts/ragas/datasets/retrieval_eval.jsonl --rounds 3 --metrics all --prompt-label <라벨이름> --verbose
```

- `--rounds` — 라운드 수 (기본 3)
- `--metrics` — `all`, `context_precision`, `faithfulness`
- `--prompt-label` — 리포트 디렉토리에 붙는 라벨
- `--verbose` — 상세 로그

결과는 `reports/<timestamp>_benchmark_<라벨>/round{N}_{metric}/result.json`에 저장된다.

### 단일 평가 (원샷)

```bash
python scripts/ragas/run_eval.py \
  --dataset scripts/ragas/datasets/retrieval_eval.jsonl \
  --max-samples 50 \
  --threshold 0.75 \
  --verbose
```

## Notes

- `RAGAS_OPENAI_API_KEY`가 있으면 `CHAT_MODEL_API_KEY`보다 우선 사용된다.
- Qdrant payload는 `sourceChunkRef`/`text_segment` 키를 우선 사용하고, 스키마 차이를 위해 fallback을 포함한다.
