# 개인 문서 검색 서비스

내가 올린 문서 기반으로 AI가 답을 찾아주고, 출처까지 알려주는 RAG 기반 문서 검색 서비스입니다.

## 목표

- 사용자가 업로드한 문서(PDF, TXT, MD)에서 질문에 대한 답변과 출처를 제공
- 멀티턴 대화를 통한 맥락 유지
- 질문/답변 히스토리 관리

## 로컬 실행

- 환경 변수: `.env.local` (샘플은 `.env.example`)
- 인프라 + 앱 실행:
  ```bash
  docker compose --profile infra --profile app up -d
  ```
- 모니터링 포함:
  ```bash
  docker compose --profile infra --profile app --profile monitoring up -d
  ```

### 주요 컴포넌트

| 컴포넌트          | 역할 |
|---------------|------|
| API Server    | REST API, 인증, 질의응답 |
| Worker Server | 문서 파싱, 청킹, 임베딩 |
| MySQL         | 메타데이터, 히스토리 |
| S3            | 원본 파일 저장 |
| Qdrant        | 벡터 임베딩 저장 |
| Redis Stack   | 시맨틱 캐싱 |
