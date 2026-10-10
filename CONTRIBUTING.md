# CONTRIBUTING

## 결정

- 도메인마다 담당자를 둔다. 담당 영역 안의 정책과 설계는 담당자가 정한다.
- 결정은 합의(consensus)가 아닌 동의(consent)로 한다([둘의 차이](https://circleforward.us/what-is-the-difference-between-consent-and-consensus/)). 다른 사람 영역의 결정은 내 영역에 큰 문제가 생길 위험이 없으면 넘긴다. 다르게 하자는 말은 제안이지 막을 이유가 아니다.
  - 막을 수 있는 것은 one-way door 결정뿐이다([one-way, two-way door](https://aws.amazon.com/executive-insights/content/how-amazon-defines-and-operationalizes-a-day-1-culture/)). two-way door에 대한 의견은 제안이다.
  - 반대는 내 영역에서 무엇이 깨지는지로 말한다. 담당자는 설득하지 않고 그 위험을 줄이도록 계획을 고치고, 위험이 줄면 반대한 사람이 반대를 거둔다. 그래도 안 풀리면 팀 회의에서 정한다.
- 아래는 GitHub Discussions에서 먼저 이야기한다. 나머지는 올리지 않아도 된다.
  - 영역 사이의 계약(API로 주고받는 데이터)
  - 기능 범위(MVP)를 바꾸는 것
  - 팀 전체가 쓰는 도구
- Discussion은 무엇을 왜 하는지 몇 줄을 먼저 쓰고 근거와 대안은 접어 둔다. 댓글을 기다리지 않고 구현해도 된다.

## 기록 위치

| 무엇 | 어디 |
| --- | --- |
| API 계약 | `backend/openapi.yaml` (컨트롤러와 DTO에서 자동 생성) |
| 스프린트 목표와 작업 | Jira(MS2 프로젝트) |
| 회의록, 일정, 브레인스토밍 | Miro |
| 실행법과 운영 절차 | 각 디렉터리의 `README.md` |
| 팀 규칙 | `docs/contributing/` |

- `docs/contributing/git-workflow.md`: 브랜치, 커밋 메시지, 머지 방식.
- `docs/contributing/pull-request.md`: 머지 조건, PR 크기, 리뷰.
- `docs/contributing/documents.md`: 금지 문자, README 점검.
- `docs/contributing/naming.md`: 이름 규칙.
- `docs/contributing/error-handling.md`: 오류 code, 문구, 예외와 핸들러의 자리.
- `docs/contributing/comments.md`: 주석과 javadoc.
