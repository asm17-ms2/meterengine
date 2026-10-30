# 시험 RDS 자동 재기동 감지

`test-slo`와 `test-storage`의 자동 재기동 중지만 소유하는 전용 Terraform root다. 기존 DB, EC2와 SUT 상태는 가져오거나 변경하지 않는다.
기존 S3 버킷의 `measurements/rds-auto-stop/terraform.tfstate`에 새 자원 상태를 둔다. Storage의 원래 로컬 상태 파일이 없어도 적용할 수 있다.

처리, 중복 방지와 실패 조건은 [모듈 설명](../modules/rds-auto-stop/README.md)을 따른다. DB는 data source로만 읽는다.

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

계획에는 두 환경의 `module.rds_auto_stop` 신규 자원만 있어야 한다. 기존 DB, EC2, 역할이나 스케줄 변경이 있으면 적용하지 않는다. 이벤트 규칙의 `state`가 바뀌는 계획은 그 변경을 의도한 PR이 아니면 적용하지 않는다. 사용자에게 계획을 보여주고 적용 승인을 받은 뒤 저장한 계획만 적용한다.

```bash
terraform -chdir=measurements/terraform/rds-auto-stop apply "$rds_plan_dir/rds-auto-stop.tfplan"
```

이 적용은 감지와 처리 자원만 배치한다. 수동 기동이나 가짜 이벤트를 실제 검증으로 대신하지 않는다.

## 실제 검증

실제 자동 재기동 뒤 실행 이력의 `StopAutomaticRestart`, `ConfirmStopped`가 성공하고 DB가 `stopped`인지 확인한다.

```bash
aws stepfunctions list-executions --region ap-northeast-2 \
  --state-machine-arn arn:aws:states:ap-northeast-2:928618307647:stateMachine:meterengine-test-slo-rds-auto-stop \
  --max-results 5
aws rds describe-db-instances --region ap-northeast-2 \
  --db-instance-identifier meterengine-test-slo-db \
  --query 'DBInstances[0].[DBInstanceStatus,AutomaticRestartTime]' --output json
```

`test-storage`는 위 이름의 `test-slo`를 바꿔 확인한다. 실패 경보의 `alarm_actions` 기본값은 비어 있어 외부 알림은 없다. 실행 이력과 CloudWatch 경보 상태로 확인한다.

## 복구

실패한 event-id는 재실행해도 선점되지 않는다. 중복 방지 기록을 지우지 말고 실패 원인과 실행 이력을 확인한다.

감지를 끄는 결정은 `variables.tf`의 `enabled` 기본값에서 그 환경을 false로 바꾸는 PR로 남긴다. `-var`로만 끄면 다음 일반 적용이 감지를 다시 켠다. 실행 중인 상태 기계가 없는지 확인하고, 머지된 코드로 `-var` 없이 만든 계획만 별도로 승인받아 적용한다. 중복 방지 테이블은 삭제 보호가 있다.

아래 `-var` 계획은 PR을 머지하기 전에 바뀔 자원을 미리 보는 용도로만 쓰고 적용하지 않는다. 새 셸에서도 계획 파일이 비공개 임시 폴더에 남게 폴더부터 만든다.

```bash
umask 077
rds_plan_dir="$(mktemp -d)"
terraform -chdir=measurements/terraform/rds-auto-stop plan -lock=false \
  -var='enabled={test-slo=false,test-storage=true}' \
  -out="$rds_plan_dir/disable-slo-detector.tfplan"
```

### 전달 실패 경보

EventBridge가 흐름에 넘기지 못한 이벤트는 환경마다 전달 실패 큐(DLQ)에 남고 경보가 울린다. 흐름은 발생한 지 15분이 지난 이벤트를 IgnoredEvent로 성공 처리해 DB를 끄지 않고, 15분 안에 다시 실행하면 정상 처리해 DB를 끌 수 있다. 어느 쪽이든 큐의 이벤트로 흐름을 다시 실행하지 않고 아래 절차를 따른다.

```bash
terraform -chdir=measurements/terraform/rds-auto-stop output dead_letter_queue_urls
aws sqs receive-message --region ap-northeast-2 --queue-url <큐 URL> \
  --attribute-names All --message-attribute-names All --max-number-of-messages 10
aws rds describe-db-instances --region ap-northeast-2 \
  --db-instance-identifier meterengine-test-slo-db \
  --query 'DBInstances[0].DBInstanceStatus' --output text
```

1. 메시지 본문과 오류 속성으로 전달 실패 원인을 본다.
2. DB가 켜져 있으면 다음 04:00 스케줄 중지를 기다리거나, 승인받은 뒤 `aws rds stop-db-instance`로 끈다. `start-execution`으로 흐름을 다시 실행하지 않는다.
3. 원인을 고친 뒤 `aws sqs delete-message`로 메시지를 지워 경보를 OK로 되돌린다. 지우지 않으면 경보가 메시지 보관 기간(최대 14일) 동안 ALARM에 머문다.

## 환경 빼기

SUT 하나를 지우기 전에 그 환경의 감지를 먼저 뺀다. 이 root는 두 SUT의 DB를 data source로 읽어, DB가 없어지면 남은 환경의 감지도 계획을 만들 수 없다.

1. `variables.tf`의 `environments`와 `enabled` 기본값에서 그 환경을 빼는 PR을 올리고 머지한다.
2. 그 환경의 중복 방지 테이블 `meterengine-<환경>-rds-auto-stop-claims`는 삭제 보호가 있다. 지워도 되는지 승인받은 뒤 `aws dynamodb update-table --region ap-northeast-2 --table-name <테이블> --no-deletion-protection-enabled`로 보호를 끈다.
3. 머지된 코드로 `-var` 없이 계획을 만들고, 그 환경의 `module.rds_auto_stop` 자원만 지워지는지 확인한 뒤 승인받아 적용한다.
4. 그다음 SUT를 지운다.

SUT를 다시 만들면 DB가 생긴 뒤 `environments`와 `enabled` 기본값에 그 환경을 다시 넣는 PR을 올리고, 계획에서 그 환경의 `module.rds_auto_stop` 자원만 더해지는지 확인한 뒤 승인받아 적용한다. 다시 넣기 전까지 그 환경은 자동 재기동 감지가 꺼져 있다.

