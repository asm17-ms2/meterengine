# PR

## 머지 조건

- 작성자 외 1명 이상의 approve. 보호 경로를 건드리면 `protected-reviewers` 팀원의 approve 둘.
  - 강제는 main 룰셋의 required reviewer 패턴이 하고, 리뷰 요청은 `.github/CODEOWNERS`가 한다.
- `ci.yml`의 `backend`, `frontend`, `demo`, `deploy`, `terraform`이 통과해야 한다. 안 바뀐 쪽은 skip되어 통과로 친다.
- 리뷰 스레드를 모두 resolve해야 한다.
- 머지는 Merge when ready로 merge queue에 넣는다. 큐가 최신 main과 앞선 PR을 합친 상태로 CI를 돌리고, 통과하면 squash로 머지한다. main을 따라잡지 않아도 된다.
  - GitHub 네이티브 스택은 `gh stack merge`로 넣는다. 아래 몇 층만 넣으려면 `gh stack merge <PR 번호>`.
  - 큐에서 CI가 실패하거나 충돌하면 그 PR이 큐에서 빠지고, GitHub 네이티브 스택이면 위층도 같이 빠진다. 고친 뒤 다시 넣는다.

## 크기

- 사람이 리뷰하기 쉬운 크기로 올린다. 추가된 줄 300줄이 상한이다.
  - 생성물(`backend/openapi.yaml`, 잠금 파일)과 삭제된 줄은 세지 않는다.

## 리뷰

- 코멘트 첫머리에 무게를 `p1:`~`p5:`로 붙인다. 무게의 뜻과 리뷰 상태는 [뱅크샐러드 코드리뷰 문화](https://blog.banksalad.com/tech/banksalad-code-review-culture/)를 따른다.
- 다른 사람 영역의 PR은 동의(consent)로 본다(CONTRIBUTING.md "결정"). Request changes는 one-way door 결정으로 내 영역이 깨질 때만 걸고, 풀리면 건 사람이 approve로 바꾼다. 그 밖에는 p4 이하로 달거나 달지 않는다.
- 스택이 작아 이어 읽는 편이 쉬우면 작성자가 모든 조각을 한 사람에게 배정할 수 있다.
