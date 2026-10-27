# 시험 RDS 자동 재기동 감지

`test-slo`와 `test-storage`의 자동 재기동 중지만 소유하는 전용 Terraform root다. 기존 DB, EC2와 SUT 상태는 가져오거나 변경하지 않는다.
기존 S3 버킷의 `measurements/rds-auto-stop/terraform.tfstate`에 새 자원 상태를 둔다. Storage의 원래 로컬 상태 파일이 없어도 적용할 수 있다.
기존 04:00 RDS 보호는 새 흐름이 실제 자동 기동을 다시 중지한 뒤에만 비활성화된다. EC2 정시 중지는 유지한다.

처리, 중복 방지와 실패 조건은 [모듈 설명](../modules/rds-auto-stop/README.md)을 따른다. AWS 공급자 6.69.0은 Scheduler data source를 제공하지 않아, 스케줄 이름을 고정 입력으로 쓰고 실행할 때 `GetSchedule`로 대상과 역할을 검증한다. DB와 기존 역할은 data source로만 읽는다.

## 계획과 적용

명령은 레포 루트에서 실행한다. 기존 SUT root를 apply하거나 상태를 import하지 않는다. 계획 파일은 외부에 공유하지 않는다.

```bash
umask 077
rds_plan_dir="$(mktemp -d)"
terraform -chdir=measurements/terraform/rds-auto-stop init -lockfile=readonly
terraform -chdir=measurements/terraform/rds-auto-stop plan -lock=false \
  -out="$rds_plan_dir/rds-auto-stop.tfplan"
terraform -chdir=measurements/terraform/rds-auto-stop show -no-color "$rds_plan_dir/rds-auto-stop.tfplan"
```

계획에는 두 환경의 `module.rds_auto_stop` 신규 자원만 있어야 한다. 기존 DB, EC2, 역할이나 스케줄 변경이 있으면 적용하지 않는다. 사용자에게 계획을 보여주고 적용 승인을 받은 뒤 저장한 계획만 적용한다.

```bash
terraform -chdir=measurements/terraform/rds-auto-stop apply "$rds_plan_dir/rds-auto-stop.tfplan"
```

이 적용은 감지와 처리 자원만 배치한다. 최초 실제 0154가 오기 전에는 기존 RDS 스케줄을 수동으로 끄지 않는다. 수동 기동이나 가짜 이벤트를 실제 검증으로 대신하지 않는다.

## 실제 검증

실제 자동 재기동 뒤 실행 이력의 `StopAutomaticRestart`, `ConfirmStopped`, `ConfirmLegacyDisabled`가 성공했는지 확인한다. RDS 스케줄은 `DISABLED`, EC2 스케줄은 기존 상태여야 한다.

```bash
aws stepfunctions list-executions --region ap-northeast-2 \
  --state-machine-arn arn:aws:states:ap-northeast-2:928618307647:stateMachine:meterengine-test-slo-rds-auto-stop \
  --max-results 5
aws rds describe-db-instances --region ap-northeast-2 \
  --db-instance-identifier meterengine-test-slo-db \
  --query 'DBInstances[0].[DBInstanceStatus,AutomaticRestartTime]' --output json
aws scheduler get-schedule --region ap-northeast-2 \
  --name meterengine-test-slo-stop-rds --group-name default \
  --query '{State:State,Target:Target,TimeZone:ScheduleExpressionTimezone}'
aws scheduler get-schedule --region ap-northeast-2 \
  --name meterengine-test-slo-stop-ec2 --group-name default --query State
```

`test-storage`는 위 이름의 `test-slo`를 바꿔 확인한다. 실패 경보의 `alarm_actions` 기본값은 비어 있어 외부 알림은 없다. 실행 이력과 CloudWatch 경보 상태로 확인한다.

## 복구

첫 성공 뒤에는 기존 RDS 스케줄이 꺼져 있을 수 있다. 이후 실행이 실패해도 자동으로 다시 켜지지 않으므로 현재 상태를 먼저 확인한다.

실패한 event-id는 재실행해도 선점되지 않는다. 중복 방지 기록을 지우지 말고 실패 원인과 실행 이력을 확인한다.

기존 보호로 돌아가려면 실행 중인 상태 기계가 없는지 확인한다. 아래 복구 입력을 검토하고 AWS 변경 승인을 받은 뒤 기존 RDS 스케줄을 다시 켠다. 대상, 재시도와 시간대는 보존한다.

```bash
aws scheduler get-schedule --region ap-northeast-2 \
  --name meterengine-test-slo-stop-rds --group-name default > "$rds_plan_dir/schedule-before.json"
jq 'with_entries(select(.key as $k | ["Name","GroupName","ScheduleExpression","ScheduleExpressionTimezone","FlexibleTimeWindow","Target","Description","StartDate","EndDate","KmsKeyArn","ActionAfterCompletion"] | index($k))) + {State:"ENABLED"}' \
  "$rds_plan_dir/schedule-before.json" > "$rds_plan_dir/schedule-enable.json"
aws scheduler update-schedule --region ap-northeast-2 \
  --cli-input-json "file://$rds_plan_dir/schedule-enable.json"
aws scheduler get-schedule --region ap-northeast-2 \
  --name meterengine-test-slo-stop-rds --group-name default --query State
```

`ENABLED`를 확인한 뒤 아래 조건으로 새 감지를 끄는 계획을 만들고 별도로 승인받아 적용한다. 중복 방지 테이블은 삭제 보호가 있다.

```bash
terraform -chdir=measurements/terraform/rds-auto-stop plan -lock=false \
  -var='enabled={test-slo=false,test-storage=true}' \
  -out="$rds_plan_dir/disable-slo-detector.tfplan"
```

기존 SUT 코드에도 RDS 스케줄의 `ignore_changes = [state]`를 유지한다. 이후 SUT apply가 운영 중 비활성화한 스케줄을 다시 켜지 않게 하기 위한 설정이다. SUT의 `auto_stop_state`는 EC2만 제어한다.
