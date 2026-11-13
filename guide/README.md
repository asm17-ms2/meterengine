# guide

MeterEngine을 자사 서비스 과금에 쓰는 조직의 개발자와 운영자를 위한 온보딩 가이드와 API 참조다. Mintlify로 만든다. 이 README는 Mintlify가 기본으로 무시해 사이트에 나오지 않는다.

## 미리보기

Node 20.17 이상이 필요하다.

```bash
cd guide
npx mint dev --port 3333
```

- 3000은 frontend `pnpm dev`가 쓰므로 다른 포트를 쓴다.
- 브라우저에서 http://localhost:3333 을 연다.
- 설정과 링크 검사: `npx mint validate`, `npx mint broken-links`.
- 금지 문자, 레포 경로, 내부 링크 검사: 레포 루트에서 `python3 -I guide/scripts/lint_guide.py`(표준 라이브러리만 쓴다).

## 구조

| 경로 | 내용 |
| --- | --- |
| `docs.json` | 사이트 설정. 탭 "가이드"와 "API 참조", 탐색 순서, 시험 화면 설정 |
| `*.mdx`, `*/` | 가이드 페이지. 경로는 docs.json의 pages와 같다 |
| `openapi/overlays/meterengine.yaml` | API 참조에만 필요한 변경(OpenAPI Overlay 1.0). 생성 파일 |
| `scripts/` | overlay 생성과 MDX 검사 스크립트 |
| `_meta/canonical.md` | 여러 페이지에 걸치는 사실의 정본 페이지 표 |
| `_meta/style.md` | 문체 규칙과 화면 표기 대응 |
| `.mintignore` | `_meta/`와 `scripts/`를 게시에서 뺀다 |

## API 참조

- API 참조는 main 브랜치의 `backend/openapi.yaml`을 주소로 읽어 만든다. 사본을 두지 않으므로 백엔드 PR이 따로 할 일은 없다. main을 읽는 것은 가이드가 production에 나간 동작을 설명하기 때문이다.
- 문서에만 필요한 변경은 overlay에 둔다. operation이 늘거나 이름이 바뀌면 레포 루트에서 overlay를 다시 만든다.

  ```bash
  python3 -I guide/scripts/make_overlay.py
  ```

  - `servers`를 `https://meterengine.com` 하나로 바꾼다. 경로가 모두 `/v1/`로 시작하므로 `/v1`을 붙이지 않는다.
  - 운영자 전용, 콘솔 세션, 가입과 로그인과 초대 수락, MeterEngine 화면용 공개 경로, 화면 보조 options에 `x-excluded`를 붙여 페이지를 만들지 않는다.
  - 콘솔 로그인으로만 되는 관리 경로와 토스페이먼츠 웹훅 수신 경로에 `x-hidden`을 붙여 페이지는 만들되 탐색에서 숨긴다.
  - 최상위 `tags`를 만들고 `x-group`으로 한국어 그룹 이름을 붙인다.
- 설명, 예시, 필요 권한처럼 정본이 코드에 있는 것은 overlay로 덮지 않고 백엔드(컨트롤러와 DTO)에서 고친다.
- 시험 화면(playground)은 proxy를 켠다. 백엔드에 CORS 설정이 없어 브라우저 직접 호출이 안 된다. 그래서 입력한 키가 Mintlify 서버를 지나며, 소개 페이지가 테스트 환경 키만 쓰라고 안내한다.

## 배포

- 공개 주소는 https://docs.meterengine.com 이다. Mintlify 기본 주소 https://meterengine.mintlify.site 도 같은 사이트를 보인다.
- Mintlify GitHub App이 이 레포의 main을 읽어 배포한다. Git Settings의 "docs.json is in a subdirectory"는 `/guide`다. develop이 main에 머지될 때 production 배포와 함께 공개된다.
- 도메인 레코드(TXT 두 개와 CNAME)는 `deploy/terraform/production/dns.tf`가 정한다. TXT 값은 Mintlify 대시보드의 Custom domain 화면에 나온 값과 같아야 한다.
- Mintlify 웹 편집기는 main으로 직접 PR을 열어 브랜치 규칙과 부딪히므로 쓰지 않는다.
- 본문은 문서 도메인을 쓰지 않고 절대 경로 링크만 쓴다.

## 확인 필요 주석

- 팀이 아직 정하지 않은 것에 기대는 문장 앞에는 MDX 주석 `{/* 확인 필요: 내용 */}`이 있다. 공개 화면에는 나오지 않는다.
- 모아 보기: `grep -rn "확인 필요" guide --include=*.mdx`.
- 정해지면 문장을 맞추고 주석을 지운다.

## 알려진 문제

- API 참조의 엔드포인트 그룹이 overlay `tags` 순서가 아니라 스펙에서 태그가 처음 나오는 순서로 나온다. 맞추려면 docs.json에서 그룹마다 openapi 페이지를 직접 나열해야 한다.
- `navigation.languages`에 ko 하나만 둔 설정이 언어 선택 상자를 보이는지, 시험 화면의 인증 입력란과 `Bearer ` 접두사 처리는 브라우저로 보지 않았다.
