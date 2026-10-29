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
