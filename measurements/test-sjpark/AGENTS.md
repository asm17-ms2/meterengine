# AGENTS.md

측정 환경 test-sjpark-m7g, test-sjpark-t4g를 다룰 때의 규칙이다. 구성과 절차는 [README.md](README.md).

- 모듈, 루트, 배포 설정을 바꾸거나 측정을 시작하기 전에 운영과 비교한다. 비교 대상: `deploy/terraform/modules/app_fleet`, `deploy/terraform/modules/service`, 운영 루트의 파라미터, 실제 운영 SSM 이름 목록, `deploy/environments/production.json`, 부팅 템플릿.
- 운영과의 차이가 생기거나 바뀌면 README "운영과 다른 곳" 표를 고치고, 보고서에 쓸지 검토할 대상으로 박성종에게 알린다.
- 두 루트(`sut/m7g`, `sut/t4g`)는 사양 값만 다르게 둔다.
- 측정 파라미터를 손으로 만들거나 바꾸지 않는다. 바꿀 값은 모듈에 넣는다.
