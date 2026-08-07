# frontend

관리자 화면. Next.js + TypeScript.

create-next-app 스캐폴드에 App Router, Tailwind CSS, ESLint가 포함되어 있다. 이는 init 구성이지 확정 스택이 아니다. 상태 관리, UI 라이브러리(Tailwind 유지 여부 포함), 렌더링 방식 활용 범위 등 세부 스택은 MS2-46 착수 시점에 정한다. 화면은 API 계약을 기준으로 backend와 병렬 개발한다 (계약 문서 위치는 `docs/document-rules.md` 참조).

## 실행

Node.js 24+가 필요하다. 패키지 매니저는 pnpm이고, 버전은 `package.json`의 `packageManager` 필드로 고정한다.

```
corepack enable pnpm   # 최초 1회
pnpm install
pnpm dev               # http://localhost:3000
```

## 빌드와 lint

```
pnpm lint
pnpm build
```
