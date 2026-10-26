# 주석과 javadoc

- 주석을 쓴다면 한두 줄로 간결하게 쓴다.
  - 자리와 내용은 묻지 않는다. 함수 본문 안, 설정 파일, javadoc, TODO도 같다.
  - 썩지 않게 쓰는 기준은 `documents.md`를 따른다.
- 이미 적용된 `V__` 마이그레이션의 주석은 달거나 고치지 않는다. 체크섬이 바뀌어 그 DB의 기동이 실패한다.

## 주석이 아닌 것

- 도구 지시문: `@SuppressWarnings`, `// eslint-disable-next-line`.
- `@Schema`, `@Operation`, `@Parameter`, `@ApiResponse`의 description. API 계약이라 `backend/openapi.yaml`로 나간다. Jira 키는 넣지 않는다.
- 파일을 나누는 구분자: `// --- 등록 ---`. 레이블은 명사구 한 줄이다.

## 주석 대신 쓰는 곳

| 쓰고 싶은 것 | 가는 곳 |
| --- | --- |
| 두 줄을 넘는 설계 이유 | Discussion이나 커밋 본문 |
| 이 변경을 왜 했는지 | 커밋 메시지와 PR 본문 |
| 어기면 실패해야 하는 제약 | 테스트나 CI 검사 |
