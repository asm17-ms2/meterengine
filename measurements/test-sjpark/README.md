# test-sjpark

박성종이 측정에 쓰는 SUT(테스트 대상 시스템) 두 벌이다. `test-sjpark-m7g`와 `test-sjpark-t4g`는 운영과 같은 ALB, 오토스케일링 그룹 구조로 뜨고 사양 값만 다르다. 평소에는 앱 서버 0대, DB 꺼짐이고, 매일 04:00(서울)에 다시 꺼진다.

- **측정 중일 수 있으니 배포하거나 끄기 전에 박성종에게 묻는다.**
- 명령은 레포 루트에서 실행한다. `<사양>`에는 `m7g`나 `t4g`를 넣는다.
- Terraform 상태는 S3 `meterengine-tfstate-928618307647`에 있다. 새 컴퓨터에서는 `terraform -chdir=measurements/test-sjpark/sut/<사양> init`을 먼저 해야 스크립트가 `terraform output`을 읽는다.
- 모듈, 루트, 배포 설정을 바꾸기 전에 [`AGENTS.md`](AGENTS.md)를 따른다.
- m 계열 사양의 근거는 운영 가정을 정한 [Discussion #346](https://github.com/asm17-ms2/meterengine/discussions/346)이다.

## 목적

- m7g는 측정 기준이자 옮겨 갈 사양이고, t4g는 production 사양의 한계를 보는 환경이다.
- 측정으로 답하려는 질문: 왜 production과 다른 m에서 쟀는가, t4g의 한계와 m으로 옮길 기준, m의 한계와 그다음 시스템.

| 사양 | 앱 서버 | DB |
| --- | --- | --- |
| m7g | m7g.large | db.m7g.large |
| t4g | t4g.medium(CPU 크레딧 설정 없음) | db.t4g.small |

## 구성

| 경로 | 내용 |
| --- | --- |
| `sut/module/` | 측정 전용 모듈. 운영 `app_fleet`을 복사한 ALB, 오토스케일링 그룹, 확장 정책에 스냅샷 RDS, 파라미터, 자동 중지를 더했다 |
| `sut/m7g/`, `sut/t4g/` | 루트. 사양 값만 다르고 `deploy/tests`가 이를 확인한다 |
| `sut/scripts/` | 배포, 켜고 끄기, 앱 서버 목록, exporter 사용자 만들기. 첫 인자가 사양이다 |
| `lib/common.sh` | 스크립트가 같이 쓰는 함수 |
| [`loadgen/`](loadgen/README.md) | 부하 발생기 |
| [`k6/`](k6/README.md) | k6 스크립트 |
| [`scripts/deploy-report.sh`](scripts/deploy-report.sh) | 없어진 한 대 환경의 배포 중 실패 계산. 두 환경에는 쓰지 않는다 |

- 자원 이름 접두는 `meterengine-test-sjpark-<사양>`, 도메인은 `test-sjpark-<사양>.meterengine.com`, 파라미터 경로는 `/meterengine/test-sjpark-<사양>`이다.
- 두 DB는 같은 프로덕션 스냅샷에서 복원한다. 스냅샷은 두 루트의 `snapshot_identifier`이고, 바꾸면 DB가 그 스냅샷에서 다시 만들어진다.
- 앱 서버는 staging, production과 같은 미리 구운 이미지로 뜨고, 부팅 스크립트가 버전 파라미터 `app-version`의 커밋으로 배포한다. 부팅 템플릿은 운영 파일을 사본 없이 읽어, 그 파일이 바뀌면 다음 apply에서 서버가 바뀐다. 측정 중에는 apply 하지 않는다.
- VPC, 서브넷, RDS 서브넷 그룹, 호스티드 존, ECR 이미지는 개발 서버와 같이 쓴다. 호스티드 존에는 SUT 도메인과 인증서 검증 레코드를 더하고, 나머지는 읽기만 한다.
- 역할은 자기 파라미터 경로만 읽고 `/meterengine/prod`와 `/meterengine/production`은 읽지 못한다.
- 공용 모니터링 서버가 앱 서버와 DB를 긁는다. 앱 서버에는 태그 `Role=app`, `Environment=test-sjpark-<사양>`이 붙고 `Environment`가 `env` 라벨이 된다. 공용 모니터링 보안 그룹에서는 앱 서버의 8080(백엔드), 9100(node exporter), 9101(cAdvisor)과 DB 5432만 들인다. 측정 환경은 공용 Grafana 경보에서 빠진다.
- 자원에는 `Project=meterengine`, `Environment=test-sjpark-<사양>`, `Owner=sjparkx1129`, `ManagedBy=terraform` 태그가 붙는다. 자동 중지 예약(EventBridge Scheduler 스케줄, 오토스케일링 그룹 예약)은 태그를 붙일 수 없다.
- 자원 ID는 `terraform -chdir=measurements/test-sjpark/sut/<사양> output`으로 본다.

## 운영과 다른 곳

모듈에서 운영과 다른 곳에는 `# 운영과 다름(<구분>): <이유>` 주석이 붙고, 구분마다 아래 표에 행이 있어야 한다(`deploy/tests`가 확인한다). "보고서" 열은 측정 결과를 읽을 때 적을 후보다.

| 구분 | 운영 | 측정 | 이유 | 보고서 |
| --- | --- | --- | --- | --- |
| 인스턴스 유형 | t4g.medium 고정 | 변수 | 측정 목적 | 사양 |
| 서버 가용 영역 | 2a, 2b | 2a | DB와 같은 AZ로 네트워크 조건 고정 | 후보 |
| 최대 대수 | 3 | 변수 | 한 대 한계 측정 | 측정 조건 |
| ALB 삭제 보호 | 켬 | 끔 | 다시 만들 수 있게 | 없음 |
| RDS 생성 | 직접 | 스냅샷 복원 | 같은 데이터 | 측정 조건 |
| RDS 유지 보수 창 | 월 19:00 | 월 16:00 | 04:00 자동 중지와 겹치지 않게 | 없음 |
| RDS 삭제 보호, 최종 스냅샷 | 켬 | 끔 | 지울 수 있게 | 없음 |
| 성능 개선 도우미 키 | 운영 DB의 KMS 키 | AWS 기본 키 | 측정 뒤 성능 데이터는 버리므로 운영 키 사용 권한을 측정 역할에 더 주지 않는다 | 없음 |
| 파라미터 읽기 범위 | 자기 경로 밖 거부 | 자기 경로 밖 거부, 개발 서버와 운영 경로 거부를 따로 둔다 | 자기 경로 밖과 개발 서버, 운영 비밀을 이중으로 막는다 | 없음 |
| 자동 중지 | 없음 | 04:00 | 비용 | 없음 |
| webhook-enabled | 없음(켜짐) | false | 스냅샷의 실제 주소로 나가지 않게 | 후보 |
| 암호화 키 | 운영 키 | 환경별 새 키 | 운영 비밀 분리. 토스 자격을 풀지 못해 결제 호출이 실패로 끝남 | 후보 |
| Slack, oauth2 값 | 실제 값 | 받는 곳 없는 값 | 측정 경보가 팀에 가지 않게 | 없음 |
| 이미지 | main 이미지(meterengine) | develop 이미지(meterengine-staging) | develop 대상 실험과 비교 | 측정 조건 |
| 메모리 한도 | 768m | 768m | 사양만 다르게. m7g의 8GB를 앱이 다 쓰지 않음 | 후보 |

- 운영에 맞춘 것: RDS 엔진 18.4, gp3(400GiB 미만이라 기준값 3000 IOPS, 125MiB/s가 적용되고 따로 보내지 않는다), 스토리지 상한 50GB, postgresql 로그 내보내기, Database Insights standard, 백업 7일, Single-AZ.
- 수집 경로 예열 파라미터(`ingest-warmup-*`)는 운영에 없어 측정 모듈도 만들지 않는다. 예열은 돌지 않는다.

## 사전 준비

- Terraform 1.17.1, AWS CLI, `session-manager-plugin`, `jq`, `git`, `curl`.
- `ap-northeast-2`에 자원을 만들 수 있는 AWS 자격.

## 처음 띄우기

### 1. 만들기

```bash
terraform -chdir=measurements/test-sjpark/sut/<사양> init
terraform -chdir=measurements/test-sjpark/sut/<사양> plan -out=<사양>.tfplan
terraform -chdir=measurements/test-sjpark/sut/<사양> apply <사양>.tfplan
terraform -chdir=measurements/test-sjpark/sut/<사양> output
```

- `Plan:` 줄이 `N to add, 0 to change, 0 to destroy`가 아니면 apply 하지 않는다. 확인한 plan 그대로 적용하려고 저장한 plan을 쓴다.
- 앱 서버는 0대로 시작한다. RDS 스냅샷 복원 때문에 오래 걸린다.

### 2. 버전 기록

```bash
measurements/test-sjpark/sut/scripts/sut-deploy.sh <사양> <SHA 40자>
```

- 앱 서버가 0대라 버전 파라미터에 SHA만 쓰고 `앱 서버가 0대라 버전만 기록했다`가 나온다.
- 버전 파라미터가 커밋 SHA가 아니면 최소 대수를 1 이상으로 올리는 plan이 멈춘다.

### 3. 앱 서버 띄우기

"켜고 끄기"의 켜기 순서대로 한다. 서버가 부팅하며 2에서 기록한 커밋으로 배포한다.

### 4. exporter 사용자 만들기

```bash
measurements/test-sjpark/sut/scripts/create-exporter-user.sh <사양>
```

- 성공하면 `meterengine_exporter 로그인 가능, pg_monitor 멤버 확인`이 나온다.

## 다시 배포

```bash
measurements/test-sjpark/sut/scripts/sut-deploy.sh <사양> <SHA 40자>
```

- staging, production과 같은 `deploy/rollout.sh`로 배포한다. 새 서버가 대상 그룹에서 healthy가 된 뒤 옛 서버를 뺀다.
- 배포 전에 그 커밋에 `deploy/rollout.sh`가 있는지, ECR 이미지, RDS 상태를 보고 하나라도 틀리면 멈춘다. 서버의 `deploy.sh`는 DB 식별자를 `deploy/environments/test-sjpark-<사양>.json`과 대조한다.
- SHA를 빼면 `meterengine-staging-backend`, `meterengine-staging-frontend` 둘 다에 이미지가 있고 `deploy/rollout.sh`가 있는 가장 최근 develop 커밋을 쓴다.
- SUT에는 CD가 없다. develop이 바뀌어도 직접 배포할 때까지 그대로다.
- staging ECR은 태그 이미지를 최근 10개만 남기고, develop 머지마다 이미지가 올라와 빨리 지워진다. 같은 SHA로 다시 재려면 이미지가 남아 있는지 먼저 본다.

```bash
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-staging-backend --image-ids imageTag=<SHA>
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-staging-frontend --image-ids imageTag=<SHA>
```

### 보존 이미지

- 운영 ECR(`meterengine-backend`, `meterengine-frontend`)의 수명 주기 정책에는 `keep-`으로 시작하는 태그를 지우지 않는 규칙이 우선순위 1로 들어 있다. 없어진 한 대 환경이 쓰던 Caddy 시절 운영 이미지를 `keep-test-sjpark-<SHA 7자>` 태그로 남기려고 넣었다.
- 그다음 규칙(태그 이미지 최근 10개, 태그 없는 매니페스트 14일)은 태그 이름과 상관없이 잡으므로, 1번 규칙이 없으면 보존 태그도 지워진다. 앞 규칙이 잡은 이미지는 뒤 규칙이 지우지 못한다.
- 운영 ECR 정책은 Terraform 밖이라 손으로 고친다. 정책을 다시 쓸 때 이 규칙을 빼지 않는다. 두 환경은 staging ECR을 써서 이 규칙과 상관없다.

```bash
aws ecr get-lifecycle-policy --region ap-northeast-2 --repository-name meterengine-backend \
  --query lifecyclePolicyText --output text
```

```json
{"rulePriority": 1, "description": "keep- 태그 이미지는 지우지 않는다 (test-sjpark 보존)",
 "selection": {"tagStatus": "tagged", "tagPrefixList": ["keep-"], "countType": "imageCountMoreThan", "countNumber": 100},
 "action": {"type": "expire"}}
```

## 켜고 끄기

켜기 전에 RDS 자동 재기동을 처리하는 흐름이 돌고 있지 않은지 본다. 돌고 있으면 측정 도중에 그 흐름이 DB를 끌 수 있으니, 결과가 0이 될 때까지 기다린 뒤 켠다(흐름은 최대 2시간 돈다). "환경 빼기"로 감지를 뺀 뒤라면 `StateMachineDoesNotExist` 오류가 나고, 이때는 기다리지 않는다.

```bash
aws stepfunctions list-executions --region ap-northeast-2 \
  --state-machine-arn arn:aws:states:ap-northeast-2:928618307647:stateMachine:meterengine-test-sjpark-<사양>-rds-auto-stop \
  --status-filter RUNNING --query 'length(executions)'
```

`sut-start.sh`로 RDS를 켜고, 쓸 수 있게 된 뒤 앱 서버를 1대로 올린다. 대상 그룹이 `healthy`면 쓸 수 있다.

```bash
measurements/test-sjpark/sut/scripts/sut-start.sh <사양>
terraform -chdir=measurements/test-sjpark/sut/<사양> apply -var asg_min_size=1 -var asg_max_size=1
aws elbv2 describe-target-health --region ap-northeast-2 \
  --target-group-arn "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:$(terraform -chdir=measurements/test-sjpark/sut/<사양> output -raw target_group_arn_suffix)" \
  --query 'TargetHealthDescriptions[].TargetHealth.State' --output text
```

- 켜 둔 동안의 apply에는 모두 같은 `-var`를 붙인다. 빼면 0대로 돌아간다. 최대 대수는 최소 이상, 3 이하로 줄 수 있다.
- 헬스 체크 유예는 600초다. 늦으면 서버의 `/var/log/cloud-init-output.log`를 본다.

끌 때는 앱 서버를 0대로 내리고, 서버가 다 빠지면 RDS를 멈춘다. 지우지는 않는다.

```bash
measurements/test-sjpark/sut/scripts/sut-stop.sh <사양>
```

- 스크립트는 서버가 다 빠질 때까지 기다리지만 RDS는 중지 요청만 한다. 몇 분 뒤 아래가 `stopped`를 내면 꺼진 것이다.

```bash
aws rds describe-db-instances --region ap-northeast-2 --db-instance-identifier meterengine-test-sjpark-<사양>-db \
  --query 'DBInstances[0].DBInstanceStatus' --output text
```

- RDS는 꺼 둔 지 7일 뒤 저절로 켜진다. [RDS 자동 재기동 중지](../terraform/modules/rds-auto-stop/README.md)가 바로 다시 끄고, 실패해도 다음 04:00 자동 중지가 끈다.

04:00를 넘겨 측정하는 날에는 자동 중지를 끈다. plan에서 바뀌는 것은 RDS 중지 스케줄의 state(`DISABLED`)와 오토스케일링 그룹 04:00 예약 삭제뿐이어야 한다.

```bash
terraform -chdir=measurements/test-sjpark/sut/<사양> apply -var auto_stop_state=DISABLED -var asg_min_size=1 -var asg_max_size=1
```

측정이 끝나면 다시 켠다. plan에서 바뀌는 것은 스케줄 state(`ENABLED`)와 04:00 예약 생성뿐이어야 한다. 서버를 그대로 두려면 대수 `-var`를 붙이고, 빼면 서버도 0대로 내려간다.

```bash
terraform -chdir=measurements/test-sjpark/sut/<사양> apply -var asg_min_size=1 -var asg_max_size=1
```

## 서버에 들어가기

SSH가 없어 SSM으로만 붙는다. `sut-instances.sh`가 일하는 앱 서버 ID를 한 줄에 하나씩 찍는다.

```bash
aws ssm start-session --region ap-northeast-2 \
  --target "$(measurements/test-sjpark/sut/scripts/sut-instances.sh <사양> | head -n 1)"
```

컨테이너와 로그를 보는 방법은 [`deploy/README.md`](../../deploy/README.md) "문제 해결"과 같다.

## Grafana

공용 Grafana https://grafana.meterengine.com 에서 대시보드 맨 위 `환경`을 `test-sjpark-<사양>`으로 고른다. 도메인으로 열리기 전에는 [`deploy/README.md`](../../deploy/README.md) "공용 모니터링 서버"대로 9090을 포트 포워딩해 Prometheus에서 `env="test-sjpark-<사양>"`로 조회한다.

- 보이는 것: 백엔드 앱(8080), 서버(node exporter), 컨테이너(cAdvisor), PostgreSQL(postgres exporter), RDS와 ALB(CloudWatch) 지표.
- 앱 서버가 0대면 백엔드, node exporter, cAdvisor는 대상이 없다.

## 비밀번호

DB 마스터, exporter, Grafana, 첫 운영자 비밀번호와 프론트 API 키, 암호화 키는 Terraform이 만들고 상태 파일에 남기지 않는다. 값은 Parameter Store에서 본다.

```bash
aws ssm get-parameter --region ap-northeast-2 --name /meterengine/test-sjpark-<사양>/db-password \
  --with-decryption --query Parameter.Value --output text
```

- 운영자로 로그인할 때는 `operator-email`(`operator@test-sjpark-<사양>.meterengine.com`)과 `operator-password`를 쓴다.
- `password_version`은 `sut/module/parameters.tf`에 있어 올리면 두 환경이 같이 바뀐다. 바꾸는 순서는 [`test-storage/README.md`](../test-storage/README.md) "비밀번호"를 따르되, 두 루트에서 apply, `create-exporter-user.sh <사양>`, `sut-deploy.sh <사양> <지금 SHA>`를 한다.
- **암호화 키는 `password_version`을 올려도 바뀌지 않는다.** 바꾸면 DB에 저장한 암호문을 풀지 못하므로 DB를 새로 만들 때만 `crypto_encryption_key_version`을 올린다.

## 지우기

지우기 전에 [`rds-auto-stop` README](../terraform/rds-auto-stop/README.md) "환경 빼기"로 그 환경의 감지를 먼저 뺀다.

```bash
terraform -chdir=measurements/test-sjpark/sut/<사양> destroy
```

- 평소에는 지우지 않고 끈다. RDS는 최종 스냅샷 없이 지워진다.
- 부하 발생기는 [`loadgen/README.md`](loadgen/README.md)대로 따로 지운다.

## 비용

서울 온디맨드, 2026-10 기준으로 [`test-storage/README.md`](../test-storage/README.md) "비용"에 적힌 값에서 셌다. 가격 API가 막혀 직접 확인하지 못했다.

- 꺼 둔 동안 두 환경 월 약 $38(ALB 둘 시간당 $0.0225씩, RDS 스토리지 둘).
- 켜 둔 동안 m7g는 시간당 약 $0.34(앱 서버 한 대 $0.10, RDS $0.23, 공인 IP $0.005)에 ALB가 더해진다. test-storage와 같은 사양이다.
- t4g의 켜 둔 동안 시간당 값은 레포에 근거가 없어 적지 않는다.
