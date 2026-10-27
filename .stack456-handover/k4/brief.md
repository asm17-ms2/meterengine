# 묶음 k4: demo와 Lumen 예제, 환경 분리, 배포와 측정 인프라

- 자료: `.stack456-handover/k4/findings.json`. 지적 45건이고 앞선 검증에서 확인된 것 0건, 검증 전 45건이다.
- PR: #452 `feat/llm-service-demo`, #453 `feat/environment-isolation`, #455 `feat/rds-auto-stop`
- 파일 범위: `frontend/` 밖의 파일. `demo/`, `deploy/`, `measurements/`, `.github/`를 포함한다.
- 추가 검토: #452, #453, #455. 공통 지침 3번대로 한다.
- 수정 브랜치: `claude/s456-k4-<PR 번호>`. 보고 브랜치: `claude/s456-k4-report`.

## 이 묶음에서 먼저 볼 것

- `deploy/`, `measurements/`의 스크립트와 Terraform은 실제 AWS에 적용하지 않는다. 문법 검사, `terraform validate`, `terraform test`, 정적 검토까지만 한다.
- PR 번호를 정하지 못한 프론트 밖 지적도 이 묶음에 들어 있다. 고칠 PR은 `git log`와 각 브랜치 head를 보고 정한다.
