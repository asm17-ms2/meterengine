# 문서

- 한 내용은 한 곳에만 쓰고 다른 곳에서는 링크한다.
- 문서와 주석은 코드가 바뀌어도 틀리지 않게 쓴다.
  - 옆의 목록이나 코드를 세어 개수를 적지 않는다.
  - 줄 번호나 다른 파일의 주석을 가리키지 않는다.
- 금지 문자: 가운뎃점, 엠 대시, 엔 대시, 이모지, 둥근 따옴표. 하이픈, 쉼표, 콜론, 곧은 따옴표로 대체한다. 공식 고유명사는 예외.
- Jira 키는 레포 안의 파일에 쓰지 않는다. `@Schema`, `@Operation`의 description은 `backend/openapi.yaml`로 외부에 나간다.

## README 점검

- PR을 올리기 전에 자기 변경에 해당하는 줄만 본다.

| 이번 PR에서 한 일 | 같이 볼 곳 |
| --- | --- |
| 프론트 화면을 추가하거나 바꿨다 | `frontend/README.md`의 "화면" 표 |
| 마이그레이션이나 시드를 바꿨다 | `backend/README.md`의 마이그레이션 항목 |
| 컨트롤러나 DTO를 건드렸다 | `backend/README.md`의 API 문서 절 |
| 오류 code나 오류 응답 형식을 바꿨다 | `backend/README.md`의 오류 응답 절 |
| 실행 명령이나 사전 준비가 바뀌었다 | 루트 README "시작하기"와 해당 하위 README |
| 배포 구성이나 절차를 바꿨다 | `deploy/README.md` |
| CI 워크플로를 추가했거나 트리거를 바꿨다 | `.github/workflows/README.md` |
