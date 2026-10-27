# RDS 자동 재기동 중지

이 모듈은 RDS의 7일 중지 제한으로 발생하는 `RDS-EVENT-0154`만 감지해, 기동이 끝난 뒤 다시 중지한다. 수동 기동 이벤트는 제외한다.
새 흐름이 직접 중지하고 `stopped`를 확인한 뒤에만 해당 DB의 기존 04:00 RDS 스케줄을 비활성화한다. EC2 스케줄은 유지한다.
적용과 실제 자동 재기동 검증은 별도다. 검증 전에는 기존 RDS 스케줄이 계속 동작한다.

## 처리와 보호

- DB별 EventBridge 규칙은 계정, 리전, DB ARN, 식별자, 이벤트 ID를 고정한다. Standard Step Functions도 같은 조건과 15분 이내 이벤트인지 검사한다.
- DynamoDB 조건부 쓰기로 DB와 event-id를 한 번만 선점한다. 기록은 7일 TTL을 쓰며, 실패해도 지우지 않는다. 지연된 중복 이벤트로 이후 수동 기동을 다시 끄지 않는다.
- `DescribeEvents`에서 같은 DB와 발생 시각의 실제 자동 기동 문구를 확인한다. 그 이후 중지 기록이 있으면 이전 이벤트로 처리한다. 이 API 응답에는 `EventID`가 없으므로 검사하지 않는다. 다음 페이지가 있으면 불완전한 조회로 보고 중지하지 않는다.
- 30초마다 상태를 조회하며 전체 기한은 2시간이다. `available`에서 한 번만 중지를 요청한다. 이미 중지된 경우, 중지 거절, 확인 실패와 시간 초과는 기존 스케줄을 끄지 않는다.
- 기존 스케줄의 대상과 DB 식별자를 확인한 뒤, `GetSchedule`의 쓰기 가능한 필드를 보존해 `State`만 바꾼다. 비활성화 결과도 다시 읽는다.
- SUT의 기존 RDS 스케줄은 `ignore_changes = [state]`로 운영 상태를 보존한다. 이후 Terraform apply가 비활성화된 스케줄을 다시 켜지 않는다. `auto_stop_state`는 EC2만 제어한다.
- 실행 실패, EventBridge 전달 실패, DLQ 미처리 메시지는 CloudWatch 경보로 본다. `alarm_actions`가 비어 있으면 외부 알림은 없다. 실행 로그는 30일, DLQ 메시지는 14일 보존한다.

첫 성공으로 기존 스케줄이 비활성화된 뒤의 실패는 그 스케줄을 자동으로 다시 켜지 않는다. 현재 상태를 확인하고 전용 root의 복구 절차로 대응한다.

RDS의 EventBridge 전달은 best effort다. 이벤트가 누락되면 자동 중지가 실행되지 않을 수 있다. 기존 스케줄을 끈 뒤에는 실행 이력과 RDS 상태를 함께 확인한다.

적용과 복구는 [전용 Terraform root](../../rds-auto-stop/README.md)를 따른다. 기존 SUT 상태를 가져오거나 변경하지 않는다.

## 로컬 검사

`tests/workflow.test.mjs`는 실제 ASL의 JSONata 2.0.6 표현식과 전이를 실행하고 SDK 응답만 대체한다. AWS SDK 응답 형상은 [AWS의 RDS 중지 예제](https://repost.aws/knowledge-center/rds-stop-seven-days-step-functions)를 따른다. 로컬 시험과 정의 검증은 실제 AWS 중지 성공을 증명하지 않는다.

```bash
npm --prefix measurements/terraform/modules/rds-auto-stop/tests ci --ignore-scripts
npm --prefix measurements/terraform/modules/rds-auto-stop/tests test
terraform fmt -check -recursive measurements/terraform/modules/rds-auto-stop
```

Terraform mock 시험은 AWS 자원을 만들지 않는다. SUT 잠금 파일의 공급자 버전을 써서 임시 폴더에서 실행한다.

```bash
rds_test_dir="$(mktemp -d)"
cp measurements/terraform/modules/rds-auto-stop/*.tf measurements/terraform/modules/rds-auto-stop/*.json "$rds_test_dir/"
cp measurements/test-slo/sut/terraform/.terraform.lock.hcl "$rds_test_dir/"
mkdir "$rds_test_dir/tests"
cp measurements/terraform/modules/rds-auto-stop/tests/*.tftest.hcl "$rds_test_dir/tests/"
terraform -chdir="$rds_test_dir" init -backend=false
terraform -chdir="$rds_test_dir" test
```

근거: [RDS 이벤트 목록](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_Events.Messages.html), [RDS EventBridge 형상](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-cloud-watch-events.html), [스케줄 수정 시 필드 보존](https://docs.aws.amazon.com/scheduler/latest/UserGuide/managing-schedule-state.html), [Terraform 운영 상태 보존](https://developer.hashicorp.com/terraform/language/meta-arguments/lifecycle#ignore_changes).
