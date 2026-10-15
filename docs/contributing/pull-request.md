# PR

## 머지 조건

- 작성자 외 1명 이상의 approve. 보호 경로를 건드리면 `protected-reviewers` 팀원의 approve 둘.
  - 강제는 main 룰셋의 required reviewer 패턴이 하고, 리뷰 요청은 `.github/CODEOWNERS`가 한다.
- `ci.yml`의 `backend`, `frontend`가 통과해야 한다. 안 바뀐 쪽은 skip되어 통과로 친다.
- 리뷰 스레드를 모두 resolve해야 한다.
- main보다 뒤처졌으면 Update branch로 올린 뒤 머지한다.

## 크기

- 사람이 리뷰하기 쉬운 크기로 올린다. 추가된 줄 300줄이 상한이다.
  - 생성물(`backend/openapi.yaml`, 잠금 파일)과 삭제된 줄은 세지 않는다.

## 리뷰

- 코멘트 첫머리에 무게를 `p1:`~`p5:`로 붙인다. 무게의 뜻과 리뷰 상태는 [뱅크샐러드 코드리뷰 문화](https://blog.banksalad.com/tech/banksalad-code-review-culture/)를 따른다.
- 다른 사람 영역의 PR은 동의(consent)로 본다(CONTRIBUTING.md "결정"). Request changes는 one-way door 결정으로 내 영역이 깨질 때만 걸고, 풀리면 건 사람이 approve로 바꾼다. 그 밖에는 p4 이하로 달거나 달지 않는다.
- 스택이 작아 이어 읽는 편이 쉬우면 작성자가 모든 조각을 한 사람에게 배정할 수 있다.
