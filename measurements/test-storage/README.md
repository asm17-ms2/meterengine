# test-storage

저장단 측정용 SUT(테스트 대상 시스템)다. 개발 서버와 같은 스택을 따로 띄워, 개발 서버의 DB와 파라미터를 건드리지 않고 잰다.

- 측정 중일 수 있으니 배포하거나 끄기 전에 문인호에게 묻는다.
- 명령은 Terraform을 apply한 컴퓨터의 레포 루트에서 실행한다. 상태 파일이 그 컴퓨터에만 있다.

## 구성

| 경로 | 내용 |
| --- | --- |
| `sut/terraform/` | ALB(ACM 인증서), 오토스케일링 그룹(m7g.large, 최소 0, 최대 3), 버전 파라미터, RDS db.m7g.large(PostgreSQL 18.4, Single-AZ), 보안 그룹, IAM 역할, 파라미터, 도메인, 자동 중지. 켰을 때만 MSK 클러스터(브로커 3대)와 컨슈머 서버 오토스케일링 그룹("카프카 경로" 참고) |
| `sut/scripts/` | 배포, exporter 사용자 만들기, 켜고 끄기, 앱 서버 목록, 부하 실험 중 CPU와 메모리 기록 켜기 |
| `load-generator/terraform/` | 부하 발생기 EC2 m7g.large, 보안 그룹, IAM 역할, 자동 중지 |
| `load-generator/k6/` | k6 스크립트 |
| `load-generator/scripts/` | 켜고 끄기, 실험 실행, 보낸 것과 저장된 것 대조 |
| [`scripts/sut-cpu-report.sh`](scripts/sut-cpu-report.sh) | 단계별, 컨테이너별 SUT CPU 표. 쓰는 법은 "부하 실험" |
| [`scripts/sut-memory-report.sh`](scripts/sut-memory-report.sh) | 단계별 SUT 메모리, 백엔드 OOM과 재시작, JVM 힙과 GC 표. 쓰는 법은 "부하 실험" |
| `lib/common.sh` | 스크립트가 같이 쓰는 함수 |

- 자원 이름 접두는 `meterengine-test-storage`, 도메인은 `test-storage.meterengine.com`, 파라미터 경로는 `/meterengine/test-storage`다.
- 도메인은 ALB를 가리킨다. 앱 서버는 오토스케일링 그룹이 staging, production과 같은 미리 구운 앱 서버 이미지로 띄우고, 부팅 스크립트가 버전 파라미터 `/meterengine/test-storage/app-version`의 커밋으로 배포한다. 앱과 수집 에이전트(node-exporter, cAdvisor)만 돈다.
- 부팅 스크립트는 staging, production과 같은 [`app-user-data.sh.tftpl`](../../deploy/terraform/modules/app_fleet/templates/app-user-data.sh.tftpl)을 사본 없이 읽는다. 그 파일이 바뀌면 다음 apply에서 시작 템플릿이 새 버전이 되고, 떠 있는 서버는 새 서버로 바뀐다. 측정 중에는 apply 하지 않는다.
- 이 환경에는 모니터링 서버가 없다. 공용 모니터링 서버가 앱 서버와 DB를 긁는다("Grafana" 참고).
- 매일 04:00(서울)에 RDS가 꺼지고 오토스케일링 그룹은 0대로 내려간다.
- VPC, 서브넷, 호스티드 존, ECR 이미지는 개발 서버와 같이 쓰고 읽기만 한다. 역할은 `/meterengine/prod`와 `/meterengine/production`을 읽지 못한다.

## 사전 준비

- Terraform 1.17.1, AWS CLI, `session-manager-plugin`, `jq`, `git`, `curl`. 상태 파일을 한 번 1.17.1로 쓰면 1.16으로는 읽지 못한다.
- `ap-northeast-2`에 자원을 만들 수 있는 AWS 자격.

```bash
aws sts get-caller-identity
terraform version
```

## 처음 띄우기

### 1. 만들기

```bash
terraform -chdir=measurements/test-storage/sut/terraform init
terraform -chdir=measurements/test-storage/sut/terraform plan
terraform -chdir=measurements/test-storage/sut/terraform apply
terraform -chdir=measurements/test-storage/sut/terraform output
```

- plan 마지막 줄이 `N to add, 0 to change, 0 to destroy`가 아니면 멈춘다.
- RDS 때문에 10-15분 걸린다. 앱 서버는 0대로 시작한다.

### 2. 버전 기록

```bash
measurements/test-storage/sut/scripts/sut-deploy.sh <SHA 40자>
```

- 앱 서버가 0대라 버전 파라미터에 SHA만 쓰고 끝난다. `앱 서버가 0대라 버전만 기록했다`가 나온다.
- 확인하는 것과 SHA를 뺐을 때 고르는 커밋은 "다시 배포"와 같다.

### 3. 앱 서버 띄우기

"켜고 끄기"의 켜기 순서대로 최소 대수를 1로 올린다. 서버가 부팅하며 2에서 기록한 커밋으로 배포한다.

### 4. exporter 사용자 만들기

```bash
measurements/test-storage/sut/scripts/create-exporter-user.sh
```

- 일하는 앱 서버 하나에서 docker `postgres:18`로 SUT RDS에 `meterengine_exporter`(`pg_monitor`)를 만든다. 여러 번 돌려도 된다.
- 성공하면 `meterengine_exporter 로그인 가능, pg_monitor 멤버 확인`이 나온다.

## 다시 배포

```bash
measurements/test-storage/sut/scripts/sut-deploy.sh <SHA 40자>
```

- staging, production과 같은 `deploy/rollout.sh`로 배포한다. 버전 파라미터에 SHA를 쓰고, 서버가 있으면 새 서버를 먼저 띄워 대상 그룹에서 healthy가 된 뒤 옛 서버를 뺀다. 실패하면 `rollout.sh`가 버전을 이전 값으로 되돌리고 서버를 다시 바꾼다.
- 배포 전에 그 커밋에 `deploy/rollout.sh`가 있는지(apply한 컴퓨터의 레포에서 `git fetch` 뒤 확인), ECR 이미지, RDS 상태를 보고 하나라도 틀리면 멈춘다. `rollout.sh`가 없는 옛 커밋은 이 구조의 서버로 뜨지 못해 거절한다.
- 서버의 `deploy.sh`가 DB 식별자를 `deploy/environments/test-storage.json`과 대조한다.
- 성공하면 아래 줄이 나온다. 서버가 여러 대면 한 대씩 바뀌어 더 걸린다.

```
[sut-deploy] https://test-storage.meterengine.com/usage -> https://test-storage.meterengine.com/usage 가 200 을 돌려줬다(1회 시도)
```

- SHA를 빼면 ECR 두 저장소 모두에 이미지가 있고 `deploy/rollout.sh`가 있는 가장 최근 develop 커밋을 쓴다.
- 저장소는 `deploy/environments/test-storage.json`의 `image_repository_prefix`로 정한다. staging 저장소(`meterengine-staging-backend`, `-frontend`)라 릴리스 전에 develop 커밋을 잰다. 옛 main 커밋은 이 값을 `meterengine`으로 되돌린 커밋으로 띄운다.
- SUT에는 CD가 없다. develop이 바뀌어도 직접 배포할 때까지 그대로다.
- ECR은 태그 이미지를 최근 10개만 남긴다. 같은 SHA로 다시 재려면 이미지가 남아 있는지 먼저 본다.

```bash
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-backend --image-ids imageTag=<SHA>
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-frontend --image-ids imageTag=<SHA>
```

- 버전 파라미터가 커밋 SHA가 아니면(처음 만든 값 `unset`) `asg_min_size`를 1 이상으로 올리는 plan이 멈춘다. 서버가 부팅 중 실패해 교체만 되풀이하지 않게 막는다.

## 켜고 끄기

켜기 전에 RDS 자동 재기동을 처리하는 흐름이 돌고 있지 않은지 확인한다. 돌고 있으면 측정 도중에 그 흐름이 DB를 끌 수 있으니, 결과가 0이 될 때까지 기다린 뒤 켠다(흐름은 최대 2시간 돈다).

```bash
aws stepfunctions list-executions --region ap-northeast-2 \
  --state-machine-arn arn:aws:states:ap-northeast-2:928618307647:stateMachine:meterengine-test-storage-rds-auto-stop \
  --status-filter RUNNING --query 'length(executions)'
```

"환경 빼기"로 이 환경의 감지를 뺀 뒤라면 상태 머신이 없어 0 대신 `StateMachineDoesNotExist` 오류가 난다. 이때는 DB를 끌 흐름이 없으니 기다리지 않고 켠다.

앱 서버는 오토스케일링 그룹이 띄운다. 04:00 예약이 최소와 원하는 대수를 0으로 내려 평소에는 0대다. 켤 때는 `sut-start.sh`로 RDS를 켜고 쓸 수 있게 된 뒤 최소를 1로 올린다. 두 대상 그룹이 모두 `healthy`면 쓸 수 있다.

```bash
measurements/test-storage/sut/scripts/sut-start.sh
terraform -chdir=measurements/test-storage/sut/terraform apply -var asg_min_size=1
for tg in backend frontend; do
  aws elbv2 describe-target-health --region ap-northeast-2 \
    --target-group-arn "arn:aws:elasticloadbalancing:ap-northeast-2:928618307647:$(terraform -chdir=measurements/test-storage/sut/terraform output -raw ${tg}_target_group_arn_suffix)" \
    --query 'TargetHealthDescriptions[].TargetHealth.State' --output text
done
```

- 서버는 미리 구운 이미지로 떠 레포를 받고 배포까지 마친 뒤에 `healthy`가 된다. 헬스 체크 유예는 600초다. 늦으면 서버의 `/var/log/cloud-init-output.log`를 본다.
- 켜 둔 동안의 apply에는 모두 `-var asg_min_size=1`을 붙인다. 빼면 최소가 0으로 돌아간다.
- 끌 때는 `sut-stop.sh`로 최소와 원하는 대수를 0으로 내리고, 서버가 다 빠지면 RDS를 멈춘다. 지우지는 않는다. 최소 0은 Terraform 기본값이라 다음 `-var` 없는 apply와 어긋나지 않는다.

```bash
measurements/test-storage/sut/scripts/sut-stop.sh
```

- RDS는 꺼 둔 지 7일 뒤 저절로 켜진다. [RDS 자동 재기동 중지](../terraform/modules/rds-auto-stop/README.md)가 이를 감지해 바로 다시 끈다. 이 흐름이 실패해도 다음 04:00 자동 중지가 끈다.
- 04:00를 넘겨 측정하는 날에는 자동 중지를 끄고, 측정이 끝나면 `-var` 없는 apply로 다시 켠다. 자동 중지를 끌 때 plan에서 바뀌는 것은 스케줄 state와 ASG 0대 예약 삭제뿐이어야 한다.

```bash
terraform -chdir=measurements/test-storage/sut/terraform apply -var auto_stop_state=DISABLED -var asg_min_size=1
```

### RDS Multi-AZ

장애 주입 실험 날에만 켠다. RDS가 켜져 있어야 바꿀 수 있다.

```bash
terraform -chdir=measurements/test-storage/sut/terraform apply -var db_multi_az=true
terraform -chdir=measurements/test-storage/sut/terraform apply
```

- 켤 때 plan에서 RDS는 `multi_az`만 바뀌고 destroy가 없어야 한다. 스케줄러 권한 정책이 `known after apply`로 함께 뜨는 것은 RDS ARN을 다시 읽어서이고 내용은 같다.
- 대기 인스턴스를 만드는 동안에도 RDS는 쓸 수 있다. `MultiAZ`가 `True`가 된 뒤 실험한다.
- 장애를 주입하면 주 인스턴스가 다른 AZ로 넘어간다. 한 번 더 주입해 EC2와 같은 `ap-northeast-2a`로 되돌린 뒤 끈다. 끄면 그때 주 인스턴스가 있던 AZ에 남는다.
- 끌 때도 plan에 destroy가 없어야 한다. 있으면 apply 하지 않는다.

```bash
aws rds describe-db-instances --region ap-northeast-2 --db-instance-identifier meterengine-test-storage-db \
  --query 'DBInstances[0].[DBInstanceStatus,MultiAZ,AvailabilityZone,SecondaryAvailabilityZone]' --output text
```

## 서버에 들어가기

SSH가 없어 SSM으로만 붙는다. `sut-instances.sh`가 일하는 앱 서버 ID를 한 줄에 하나씩 찍는다. 여러 대면 `head` 대신 원하는 서버 ID를 준다.

```bash
aws ssm start-session --region ap-northeast-2 \
  --target "$(measurements/test-storage/sut/scripts/sut-instances.sh | head -n 1)"
```

컨테이너와 로그를 보는 방법은 [`deploy/README.md`](../../deploy/README.md) "문제 해결"과 같다.

## Grafana

공용 모니터링 서버가 앱 서버와 DB를 긁는다. 공용 Grafana에서 `env`를 `test-storage`로 골라 본다.

공용 Grafana가 도메인으로 열리기 전에는 공용 서버의 Prometheus만 포트 포워딩으로 본다. http://localhost:9090 에서 `up{env="test-storage"}`가 1인지 본다. 앱 서버가 0대면 대상이 없다.

```bash
aws ssm start-session --region ap-northeast-2 \
  --target "$(aws ec2 describe-instances --region ap-northeast-2 \
    --filters Name=tag:Name,Values=meterengine-monitoring Name=instance-state-name,Values=running \
    --query 'Reservations[0].Instances[0].InstanceId' --output text)" \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["9090"],"localPortNumber":["9090"]}'
```

## 비밀번호

DB 마스터, exporter, Grafana, 첫 운영자 비밀번호와 프론트 API 키, 암호화 키는 Terraform이 만들고 상태 파일에 남기지 않는다. 값은 Parameter Store에서 본다.

```bash
aws ssm get-parameter --region ap-northeast-2 --name /meterengine/test-storage/db-password \
  --with-decryption --query Parameter.Value --output text
```

- 운영자로 로그인할 때는 `operator-email`(`operator@test-storage.meterengine.com`)과 `operator-password`를 쓴다.
- **암호화 키는 `password_version`을 올려도 바뀌지 않는다.** 바꾸면 DB에 저장한 암호문을 풀지 못하므로 DB를 새로 만들 때만 `crypto_encryption_key_version`을 올린다.

DB 비밀번호를 바꿀 때는 측정이 없을 때 SUT를 켠 상태에서 한다.

1. `sut/terraform/parameters.tf`의 `password_version`을 하나 올린다.
2. apply 한다. RDS 마스터와 `db-password`, `db-exporter-password`, `operator-password`, `frontend-api-key`가 바뀐다. 다시 배포하면 백엔드가 새 운영자 비밀번호와 새 프론트 키를 넣고, 옛 키는 폐기되지 않고 남는다.
3. exporter 비밀번호를 맞추고, 지금 SHA로 다시 배포해 서버의 env 파일을 새 값으로 쓴다.

```bash
terraform -chdir=measurements/test-storage/sut/terraform apply
measurements/test-storage/sut/scripts/create-exporter-user.sh
measurements/test-storage/sut/scripts/sut-deploy.sh <지금 SHA>
```

- 지금 SHA는 버전 파라미터에서 본다. 같은 SHA로 다시 배포해도 서버를 새로 바꾼다.

```bash
aws ssm get-parameter --region ap-northeast-2 --name /meterengine/test-storage/app-version \
  --query Parameter.Value --output text
```

- exporter 비밀번호를 바꾸면 공용 모니터링 서버의 `start.sh`를 다시 돌려야 공용 postgres-exporter가 새 값을 읽는다([`deploy/README.md`](../../deploy/README.md) "모니터링").

## 부하 발생기

SUT를 만든 뒤에 만든다. SUT RDS 보안 그룹에 부하 발생기가 붙는 5432 규칙을 더한다.

```bash
terraform -chdir=measurements/test-storage/load-generator/terraform init
terraform -chdir=measurements/test-storage/load-generator/terraform plan
terraform -chdir=measurements/test-storage/load-generator/terraform apply
```

- plan 마지막 줄이 `11 to add, 0 to change, 0 to destroy`가 아니면 멈춘다.
- 첫 부팅 때 레포를 `/opt/meterengine`에 받고 k6, psql, jq를 설치한다.
- 파라미터는 `/meterengine/test-storage` 아래만 읽는다. 매일 04:00에 자동으로 꺼진다.

켜고 접속한다. 실험이 끝나면 끈다.

```bash
measurements/test-storage/load-generator/scripts/lg-start.sh
aws ssm start-session --region ap-northeast-2 \
  --target "$(terraform -chdir=measurements/test-storage/load-generator/terraform output -raw load_generator_instance_id)"
measurements/test-storage/load-generator/scripts/lg-stop.sh
```

## API 키

- `run-ingest-steps.sh`와 `run-fault.sh`는 `API_KEY` 환경변수가 없으면 시작하지 않는다. 대조(`reconcile.sh`)는 키를 쓰지 않는다.
- test-storage 전용 키를 쓴다. 운영자로 로그인한 세션에서 SUT 도입사에 `ingest`, `read` 스코프 키를 발급한다(`POST /v1/api-keys`, 절차는 [`backend/README.md`](../../backend/README.md#배포-환경의-운영-키-발급)). 원문은 발급 응답에만 나온다.
- 셸 기록에 남지 않게 아래 실험 명령의 `read`로 입력받는다. 세션을 새로 열면 다시 입력한다.
- 백엔드 예열 값(`ingest-warmup-api-key`, `ingest-warmup-organization-id`, `ingest-warmup-customer-id`)은 Terraform이 만들지 않는다. 실험 키와 다른 예열 도입사를 [`deploy/README.md`](../../deploy/README.md#수집-경로-예열) "수집 경로 예열" 절차로 만들고 `/meterengine/test-storage/` 아래에 넣은 뒤 `sut-deploy.sh`로 다시 배포한다. 요청 수만 바꿔 다시 잴 때는 `ingest-warmup-requests`를 넣고 다시 배포한다.
- RDS를 새로 만들면 예열 도입사와 키도 사라진다. 다시 만들어 예열 값을 고칠 때까지 부팅마다 401 WARN이 남고 예열 없이 잰다. 측정 전에 백엔드 로그에 `Ingest warm-up finished` 줄이 있는지 본다.

## 부하 실험

이벤트를 보내는 속도를 올려 가며 초당 몇 건에서 어디가 먼저 무너지는지 본다.

부하를 걸기 전에 apply한 컴퓨터에서 SUT 자원 기록을 켠다. 여기서 정한 RUN_ID를 부하 발생기에서도 그대로 쓴다. 비우면 날짜로 정해져 SUT 기록과 이어지지 않는다.

```bash
export RUN_ID=<날짜>-ingest
measurements/test-storage/sut/scripts/start-resource-recording.sh "$RUN_ID"
```

- 그때 일하는 앱 서버마다 기록을 켠다. 켠 뒤 늘어난 서버는 기록하지 않으므로, 확장을 재는 실험은 Grafana와 CloudWatch로 본다.
- 컨테이너마다, 그리고 SUT 전체의 CPU 누적값은 SUT의 `/var/tmp/results/<RUN_ID>-sut-cpu.tsv`에, 메모리 값과 백엔드 JVM의 힙과 GC 값은 `<RUN_ID>-sut-memory.tsv`에 10초마다 쌓인다. 90분 뒤 스스로 멈추고, 길이는 `RECORD_SECONDS`(초)로 바꾼다.
- JVM 값은 SUT 안에서 백엔드 컨테이너 IP의 `/actuator/prometheus`를 10초마다 한 번씩 읽는다. 백엔드 요청 로그에 이 경로가 10초에 한 줄씩 남는다.
- 출력에 cgroup 드라이버와 커널의 IRQ 시간 계산 설정(`irq_time_accounting`)이 찍힌다. 같은 내용과 인스턴스 ID가 `<RUN_ID>-sut-info.txt`에 남는다.
- 출력에 `백엔드 JVM 값을 읽지 못했다`가 나오면 첫 회차에 힙과 GC를 읽지 못한 것이다. 기록은 계속되고, 뒤 회차도 읽지 못한 단계는 메모리 표의 힙과 GC 열이 `없음`이 된다. 그때는 아래 메모리 보고서 설명 끝의 Prometheus 질의로 본다.
- 일찍 멈추거나 다른 기록을 켜려면 SUT에서 `sudo systemctl stop meterengine-resource-record-<RUN_ID>`로 끈다.

실험은 부하 발생기에서 root로 실행한다.

```bash
sudo -i
cloud-init status --wait
git -C /opt/meterengine pull --ff-only
cd /opt/meterengine/measurements/test-storage/load-generator
export RUN_ID=<RUN_ID>
read -rsp 'API_KEY: ' API_KEY
export API_KEY
scripts/run-ingest-steps.sh
```

- `cloud-init status`가 `done`이 아니면 `/var/log/cloud-init-output.log`를 본다.
- 초당 100, 250, 500, 1,000부터 4,000건까지 6분씩 보낸다. 오류율이 0.01%를 넘거나 VU를 다 쓰면 멈춘다. VU 최대치는 단계 속도와 같아서(최소 500), 500건 이상 단계에서는 응답이 평균 1초를 넘으면 VU를 다 쓴다. 못 보낸 요청(dropped)만으로는 멈추지 않는다.
- 결과는 부하 발생기의 `load-generator/k6/results/<RUN_ID>/`에 남고, 결과 표는 `<RUN_ID>-steps.tsv`다.
- 결과 표의 `grafana` 칸 주소로 그 구간의 이벤트 수집 대시보드를, `grafana_db` 칸 주소로 SUT DB를 고른 DB 대시보드를 연다. 응답 시간은 이벤트 수집 대시보드에서 구간 끝 시각의 값을 읽는다.
- 앱 자원 대시보드도 같은 구간으로 본다. 대시보드 위쪽 `MeterEngine 대시보드` 메뉴로 옮겨 가면 구간이 그대로 유지된다.
- 부하 발생기 CPU가 80%를 넘은 구간은 결과로 쓰지 않는다(`<RUN_ID>-load-generator-cpu.tsv`).

실험 뒤 보낸 것과 저장된 것을 대조한다. 보낸 이벤트의 1%는 같은 id로 다시 보내므로, 단계마다 `유실 0건, 재전송 N건 중 duplicate N건`이 나와야 한다.

```bash
for f in k6/results/<RUN_ID>/*-ingest-r*.json; do scripts/reconcile.sh "$f"; done
```

결과는 apply한 컴퓨터의 `work/`로 가져온다. 부하 발생기에서 결과 폴더를 묶어 잠깐 연다.

```bash
mkdir -p /tmp/results && tar czf /tmp/results/<RUN_ID>.tgz -C k6/results <RUN_ID>
python3 -m http.server 8000 --bind 127.0.0.1 --directory /tmp/results
```

apply한 컴퓨터에서 포워딩을 켜 두고 다른 터미널에서 받은 뒤, 부하 발생기의 서버를 Ctrl+C로 끈다.

```bash
aws ssm start-session --region ap-northeast-2 \
  --target "$(terraform -chdir=measurements/test-storage/load-generator/terraform output -raw load_generator_instance_id)" \
  --document-name AWS-StartPortForwardingSession --parameters '{"portNumber":["8000"],"localPortNumber":["8000"]}'
curl -fo work/<RUN_ID>.tgz http://localhost:8000/<RUN_ID>.tgz
tar xzf work/<RUN_ID>.tgz -C work
```

SUT 기록 `<RUN_ID>-sut-cpu.tsv`, `<RUN_ID>-sut-memory.tsv`, `<RUN_ID>-sut-info.txt`는 "RDS 장애 주입 실험" 5의 백엔드 로그와 같은 방법으로 `work/<RUN_ID>/`에 받는다.

단계별 SUT CPU는 보고서로 나눠 본다.

```bash
measurements/test-storage/scripts/sut-cpu-report.sh work/<RUN_ID> work/<RUN_ID>/<RUN_ID>-sut-cpu.tsv
```

- 값은 SUT 전체 CPU 대비 %이고, 단계 시작 1분을 뺀 구간에서 잰다. 표는 `<RUN_ID>-sut-cpu-report.tsv`로도 남는다.
- `ec2_cpu`는 Grafana의 EC2 CPU처럼 idle만 뺀 값이고, `containers`, `outside`, `iowait`, `steal`을 더한 값과 같다.
- `backend`는 백엔드 컨테이너 값이다(옛 기록의 블루와 그린은 더한다). 컨테이너 안 모든 프로세스를 세므로 Grafana의 JVM CPU와 조금 다를 수 있다.
- 그 밖의 컨테이너는 compose 서비스마다 열이 하나씩 생긴다. HTTPS는 ALB가 맡아 SUT CPU에 들지 않는다. 패킷을 받고 보내는 커널 처리는 여기 들어가지 않고 `softirq`, `outside`에 들어간다.
- `outside`는 컨테이너 밖에서 쓴 CPU다. 커널 네트워크 처리, dockerd, SSM 에이전트, 기록 자신이 들어가고, `docker.service`, `sampler` 같은 시스템 단위 열은 그 일부다.
- `softirq`, `irq`는 SUT 전체 값이다. info 파일의 `irq_time_accounting`이 `CONFIG_IRQ_TIME_ACCOUNTING=y`일 때만 컨테이너 값에 섞이지 않고 `outside`에 들어간다.
- `note`가 `없음`이 아닌 단계는 그 사유를 보고 읽는다. `멈춤`이 있는 단계는 멈춘 컨테이너가 마지막 표본 뒤 최대 10초 동안 쓴 CPU가 `outside`에 들어간다.

단계별 SUT 메모리, 백엔드가 죽었는지, JVM 힙과 GC는 메모리 보고서로 본다.

```bash
measurements/test-storage/scripts/sut-memory-report.sh work/<RUN_ID> work/<RUN_ID>/<RUN_ID>-sut-memory.tsv
```

- 표는 `<RUN_ID>-sut-memory-report.tsv`로도 남는다. 값은 MiB다. [기준선](https://github.com/asm17-ms2/meterengine/discussions/379)의 GB는 10억 바이트라 EC2 2.4GB/8.1GB는 약 2,289/7,725MiB, 힙 0.22GB/2.0GB는 약 210/1,907MiB다.
- 최고값과 최저값은 단계 시작부터 끝까지 잰다. 횟수는 앞 단계가 끝난 바로 뒤부터 세므로(첫 단계는 단계 시작부터) 단계 사이 몇 초 동안 생긴 일은 다음 단계에 들어간다. 마지막 `전체` 줄은 기록 처음부터 끝까지라 예열 중에 생긴 일도 잡는다.
- 백엔드가 메모리 한도로 죽었는지는 `oom_kill`, `backend_restarts`, `backend_limit_hits`로 가린다. `oom_kill`은 SUT 전체에서 OOM으로 죽은 프로세스 수라 백엔드 밖의 것도 센다.
  - `oom_kill`과 `backend_restarts`가 둘 다 0보다 크면 커널이 메모리가 모자라 백엔드를 죽였고 다시 떴을 가능성이 크다. SUT에서 `sudo journalctl -k | grep -i 'killed process'`로 본 죽은 프로세스가 `java`면 맞다. note의 `컨테이너 OOM`은 컨테이너 안에서 java 말고 다른 프로세스가 죽었을 때만 붙고, java가 죽으면 cgroup이 함께 사라져 붙지 않는다.
  - `oom_kill`만 0보다 크면 백엔드 밖의 프로세스가 죽었다. 위 `journalctl -k`로 어느 프로세스인지 본다.
  - `backend_restarts`만 0보다 크면 메모리가 아닌 이유로 다시 떴다. 백엔드 로그를 본다.
  - `backend_limit_hits`만 0보다 크면 한도에 닿아 커널이 캐시를 비웠고 죽지는 않았다.
  - 셋 다 0인데 응답이 무너졌으면 원인은 메모리 한도가 아니다. `jvm_read_failed`가 0보다 크면 백엔드가 2초 안에 답하지 못할 만큼 바빴거나 멈춘 것이니 CPU 표와 GC 열을 본다.
- `backend_peak_mib`는 백엔드 컨테이너의 최대 메모리이고(옛 기록은 블루와 그린 중 높은 쪽) `backend_peak_pct`는 그 컨테이너 한도(`backend_limit_mib`, 배포 설정 `BACKEND_MEM_LIMIT`, [`deploy/README.md`](../../deploy/README.md)) 대비다. 페이지 캐시가 들어 있어, 비울 수 없는 몫은 `backend_anon_max_mib`로 본다.
- note의 `최고값 하한`은 그 백엔드가 구간보다 앞(대개 기동 때)에 더 높았다는 뜻이고, 이때 최고값은 10초 표본의 최고값이라 실제보다 낮을 수 있다. 다른 컨테이너 열(`<이름>_peak_mib`)도 같은 방법으로 재지만 이 note는 백엔드에만 붙는다.
- 백엔드가 OOM 등으로 다시 뜬 단계는 note에 `재시작 전 최고값 못 잼`이 붙는다. 다시 뜨면 cgroup이 새로 생겨, 그 단계의 최고값과 `backend_limit_hits`는 죽은 컨테이너의 마지막 10초 값이 빠진 하한이다.
- `host_used_max_mib`는 EC2 전체 메모리에서 남은 메모리(MemAvailable)를 뺀 값의 최고값으로, 기준선의 EC2 메모리와 같은 값이다. 호스트 값, anon, 힙은 10초 표본이라 그 사이 잠깐 오른 값은 놓칠 수 있다.
- `heap_max_mib`는 컨테이너 한도에 `BACKEND_MAX_RAM_PERCENTAGE`를 곱한 값이어야 한다. `full_gc`가 0보다 크고 `heap_used_max_pct`가 100에 가까우면 컨테이너 한도가 아니라 Java 힙이 모자란 것이다.
- `gc_pause_ms_per_s`는 GC로 멈춘 시간의 초당 평균이고 CPU처럼 단계 시작 1분을 뺀다. `gc_pause_max_ms`는 가장 긴 한 번이다. JVM이 최근 2분 안의 최댓값만 내주므로 앞 단계 값이 섞이지 않게 단계 시작 2분 뒤 표본만 쓴다. 둘 다 기준선과 같은 `jvm_gc_pause` 값이다.
- `gc_concurrent_ms_per_s`는 G1의 Remark, Cleanup처럼 동시 GC 단계 중에 잠깐 멈춘 시간이다. 기준선 값에 들지 않아 따로 둔다.
- 구간 안에서 새로 뜬 백엔드는 첫 몇 번 읽지 못해도 기동 때 GC부터 센다. note의 `JVM 앞 값 없음`은 구간 앞부터 있던 JVM을 구간 중간에야 처음 읽었다는 뜻이고, 그 전의 GC가 빠져 GC 열과 `full_gc`가 실제보다 낮을 수 있다.
- note의 `JVM 값 구간 끝 전에 끊김`은 단계 끝 무렵 JVM이 답하지 않았다는 뜻이다. GC 초당 값은 마지막으로 읽은 때까지만 나눠 구하고, 그 뒤의 GC는 모른다.
- JVM 값을 기록에서 읽지 못했으면 결과 표 `grafana` 칸의 구간을 Grafana Explore에 두고 아래 질의로 같은 값을 본다. 기준선도 SUT Prometheus 값이다.

```promql
sum by (instance) (jvm_memory_used_bytes{area="heap"}) / 1048576
1000 * sum by (instance) (rate(jvm_gc_pause_seconds_sum[1m]))
1000 * max by (instance) (jvm_gc_pause_seconds_max)
```

## RDS 장애 주입 실험

초당 500건을 보내는 동안 RDS를 다른 AZ로 두 번 넘겨, 수집이 끊긴 시간과 받지 못한 건수를 잰다.

- "켜고 끄기"의 "RDS Multi-AZ" 절대로 Multi-AZ를 켠 뒤에 한다.
- 부하 발생기는 04:00 자동 중지를 끌 수 없다. 부하 30분, 대조, 결과 모으기, Multi-AZ 끄기가 04:00 전에 끝나도록 02:30 전에 부하를 시작한다.

### 1. 부하 시작 (부하 발생기)

```bash
sudo -i
cloud-init status --wait
git -C /opt/meterengine pull --ff-only
cd /opt/meterengine/measurements/test-storage/load-generator
export RUN_ID=<날짜>-fault
read -rsp 'API_KEY: ' API_KEY
export API_KEY
setsid nohup scripts/run-fault.sh > /root/$RUN_ID.log 2>&1 < /dev/null &
tail -f /root/$RUN_ID.log
```

- 30분 동안 초당 500건을 보낸다. 앱 탐침(초당 5건, 시간 제한 3초)과 1초 DB 탐침이 함께 돈다.
- 둘째 줄에 주입 가능 시각(시작 3분 뒤)과 끝나는 시각이 찍힌다. 앞 3분은 비교 기준 구간이다. 두 번째 주입은 끝나기 10분 전까지 한다.
- 주입 전에 `k6/results/$RUN_ID/$RUN_ID-ingest-app_probe.console.log`의 줄이 늘고 있는지 본다. 앱 탐침이 멈추면 앱 몫을 잴 수 없다.
- 중간에 멈출 때는 `kill -- -"$(pgrep -of scripts/run-fault.sh)"`로 k6까지 함께 끈다.

### 2. 백엔드 로그 받기 (SUT, 부하 시작 직후)

로그는 30MB가 차면 지워지므로 주입 뒤에 받기 시작하면 첫 예외가 남지 않는다. 서버에는 "서버에 들어가기"대로 붙고, 서버가 여러 대면 대마다 받는다.

```bash
sudo -i
mkdir -p /var/tmp/results
setsid nohup timeout 2400 docker compose -p meterengine-prod logs -f --timestamps --since 1m backend \
  > /var/tmp/results/<RUN_ID>-backend.log 2>&1 < /dev/null &
```

### 3. 장애 주입 (apply한 컴퓨터, 두 번)

"RDS Multi-AZ" 절의 확인 명령이 첫 번째는 `available True ap-northeast-2a ap-northeast-2b`일 때, 두 번째는 대기 인스턴스가 다시 붙어 `available True ap-northeast-2b ap-northeast-2a`일 때 넘긴다.

```bash
aws rds reboot-db-instance --region ap-northeast-2 --db-instance-identifier meterengine-test-storage-db \
  --force-failover --query 'DBInstance.DBInstanceStatus' --output text
```

넣은 뒤 이벤트에 `Multi-AZ instance failover completed`가 찍혔는지 본다. 최근에 넘긴 인스턴스는 명령을 받아도 넘기지 않을 수 있다(RDS-EVENT-0034).

```bash
aws rds describe-events --region ap-northeast-2 --source-type db-instance \
  --source-identifier meterengine-test-storage-db --duration 10 --query 'Events[].[Date,Message]' --output text
```

### 4. 대조 (부하 발생기, 부하가 끝난 뒤)

새 세션이면 `sudo -i` 뒤 1의 `cd`와 `export RUN_ID`를 다시 한다. 두 단계 모두 `유실 0건`이 나와야 한다.

```bash
for s in load app_probe; do scripts/reconcile.sh k6/results/$RUN_ID/$RUN_ID-ingest-$s.json; done
cp /root/$RUN_ID.log k6/results/$RUN_ID/
```

### 5. 결과 모으기 (apply한 컴퓨터)

부하 발생기 결과는 "부하 실험"과 같은 방법으로 받는다. RDS 이벤트는 전환(failover)과 대기 복구(recovery)를 함께 여기서 저장한다.

```bash
mkdir -p work/<RUN_ID>
aws rds describe-events --region ap-northeast-2 --source-type db-instance \
  --source-identifier meterengine-test-storage-db --event-categories failover recovery --duration 180 \
  --query 'Events[].[Date,Message]' --output text > work/<RUN_ID>/<RUN_ID>-rds-events.tsv
```

백엔드 로그는 SUT에서 `python3 -m http.server 8000 --bind 127.0.0.1 --directory /var/tmp/results`를 띄우고, 부하 발생기 포워딩을 끈 뒤 받는다.

```bash
aws ssm start-session --region ap-northeast-2 \
  --target <2에서 로그를 받은 서버 ID> \
  --document-name AWS-StartPortForwardingSession --parameters '{"portNumber":["8000"],"localPortNumber":["8000"]}'
curl -fo work/<RUN_ID>/<RUN_ID>-backend.log http://localhost:8000/<RUN_ID>-backend.log
```

### 6. 결과 읽기

- 요청 줄은 `REQ id 보낸시각ms 걸린ms status error_code duplicate 재전송여부`다. 끝난 순서로 쌓이므로 `grep -o 'REQ [^"]*' <콘솔 파일> | sort -k3,3n`처럼 보낸 시각으로 정렬해 읽는다.
- `<RUN_ID>-db-probe.tsv`도 끝난 순서로 쌓이므로 첫 칸으로 정렬한다. DB가 돌아온 시각은 주입마다 실패 구간 뒤 `ok` 칸이 처음 1인 줄의 `sent_ms`에 `elapsed_ms`를 더한 값이다.
- RDS 몫은 DB 탐침이 실패한 구간이고, 앱 몫은 DB가 돌아온 뒤 앱 탐침이 다시 정상이 될 때까지다. 앱 몫에는 앱이 DB에 다시 붙는 시간과 장애 동안 쌓인 요청을 비우는 시간이 함께 들어 있다.
- load 단계는 VU 최대치가 500이라 응답이 평균 1초를 넘으면 VU를 다 쓰고, 그 뒤 요청은 REQ 줄 없이 못 보냄으로만 센다. 받지 못한 건수는 실패와 못 보냄을 더해 읽고, 끊긴 시간은 탐침으로 읽는다.

### 7. 정리 (apply한 컴퓨터, 03:30 전에 시작)

- "RDS Multi-AZ" 절의 확인 명령으로 주 인스턴스가 `ap-northeast-2a`인지 본다.
- "RDS Multi-AZ" 절대로 `-var` 없이 apply 해 끈다. plan에 destroy가 없어야 한다.
- `lg-stop.sh`와 `sut-stop.sh`로 끈다.

## 카프카 경로

수집 API가 요청 안에서 DB에 저장하지 않고 카프카에 발행하고, 컨슈머 서버가 읽어 DB에 넣는 경로를 잰다(MS2-519). 평소에는 꺼 둔다.

| 무엇 | 내용 |
| --- | --- |
| MSK 클러스터 | `kafka.m7g.large` 브로커 3대, 가용 영역 세 곳(2a, 2b, 2c). 카프카 3.9, ZooKeeper 방식. TLS(9094 포트)만 열고 인증은 하지 않는다 |
| 수집 서버 | 기존 오토스케일링 그룹. `ServerRole=ingest` 태그가 붙어 발행만 한다 |
| 컨슈머 서버 | 오토스케일링 그룹 `meterengine-test-storage-consumer`. `ServerRole=consumer`, 대수 고정, ALB에 붙지 않는다 |
| 서버가 읽는 값 | 파라미터 `event-write-path`, `kafka-bootstrap-servers`, `kafka-security-protocol`, `kafka-create-topics`. 켰을 때만 있다 |
| 토픽 | `event.ingest`(파티션 6, 복제 3, 보관 7일), `event.dlq`(파티션 1, 복제 3, 보관 30일). 먼저 뜬 backend가 만든다 |

- `deploy.sh`가 서버의 `ServerRole` 태그를 읽어 backend에 `METERENGINE_ROLE`로 넘긴다. 태그가 없거나 `all`이면 한 서버가 전부 맡는다.
- 인증이 없는 것은 이 계정이 속한 조직의 정책이 IAM 인증에 쓰는 `kafka-cluster` 권한을 막기 때문이다. 브로커에 붙을 수 있는 쪽은 보안 그룹이 수집 서버와 컨슈머 서버로 좁힌다. 붙은 서버는 모든 토픽을 읽고 쓸 수 있다.
- ZooKeeper 방식인 것은 staging, production이 쓸 `kafka.t3.small`이 KRaft를 고를 수 없어서다. KRaft로 재려면 `-var kafka_version=3.9.x.kraft`를 준다.
- 켠 동안에는 예약 작업(집계, 인보이스 확정)을 도는 서버가 없다. 수집과 저장만 잰다.
- 2c 프라이빗 서브넷(`10.0.160.0/20`)은 이 루트가 만들고, 카프카를 꺼도 남는다. 서브넷은 비용이 없다.

### 켜기

클러스터를 만드는 데 30분쯤 걸린다.

```bash
terraform -chdir=measurements/test-storage/sut/terraform apply \
  -var kafka_enabled=true -var asg_min_size=1 -var consumer_count=2
```

- 서버 없이 클러스터만 먼저 만들려면 `-var kafka_enabled=true`만 준다.
- 이미 떠 있던 서버는 태그와 파라미터를 다시 읽지 않는다. 0대로 내렸다가 다시 띄운다.
- `consumer_count`는 6까지다. `event.ingest`의 파티션이 6개라 그보다 많으면 남는 서버가 논다.

### 끄기

**MSK는 멈출 수 없다.** 04:00 예약은 서버만 0대로 내리고 클러스터는 그대로 과금된다. 실험이 끝나면 직접 지운다. 토픽과 메시지도 같이 지워진다.

```bash
terraform -chdir=measurements/test-storage/sut/terraform apply
```

- 변수를 주지 않으면 `kafka_enabled=false`라 클러스터와 카프카 파라미터가 지워지고 서버는 0대가 된다.
- 서버를 남긴 채(`-var asg_min_size=1`) 끄지 않는다. 떠 있는 서버는 지워진 브로커에 계속 발행하려 해 503을 낸다.

## 상태 파일과 지우기

- 상태 파일은 apply한 컴퓨터에만 있고 커밋하지 않는다. 잃으면 Terraform으로 지울 수 없으니 지우지 않는다.
- 다른 컴퓨터에서 apply 하지 않는다. 이름이 겹쳐 실패한다.
- 평소에는 지우지 않고 끈다. 인증서는 ACM이 DNS로 검증해 다시 만들어도 발급 횟수 제한에 걸리지 않는다. RDS는 최종 스냅숏 없이 지워진다.
- 공용 모니터링 보안 그룹(`meterengine-monitoring`)을 이름으로 찾는다. 그 그룹이 없으면 plan과 destroy가 멈추므로, 공용 모니터링을 지우기 전에 이 환경을 먼저 지운다.

지우기 전에 [`rds-auto-stop` README](../terraform/rds-auto-stop/README.md) "환경 빼기"로 test-storage의 자동 재기동 감지를 먼저 뺀다.

```bash
terraform -chdir=measurements/test-storage/load-generator/terraform destroy
terraform -chdir=measurements/test-storage/sut/terraform destroy
```

- 부하 발생기를 먼저 지운다. SUT RDS 보안 그룹에 부하 발생기 규칙이 붙어 있다.

## 비용

서울 온디맨드, 2026-10 기준이다.

- 켜 둔 동안 시간당 약 $0.34(앱 서버 한 대 $0.10, RDS $0.23, 공인 IP $0.005). 앱 서버가 늘면 한 대에 약 $0.10씩 더 든다. RDS Multi-AZ를 켠 동안은 RDS 몫이 약 두 배다.
- 꺼 둔 동안 앱 서버는 0대라 EC2, EBS, 공인 IP 비용이 없다. RDS 스토리지 월 약 $3에 ALB 고정비 월 약 $16(시간당 $0.0225)이 더 든다. ALB는 끌 수 없어 켜고 끄기와 상관없이 나간다.
- 부하 발생기는 켜 둔 동안 시간당 약 $0.11(EC2 $0.10, 공인 IP $0.005), 꺼 둔 동안 월 약 $2(EBS).
- 카프카를 켠 동안 MSK 브로커 3대와 저장소가 더 든다. 2026-11-04에 잡은 값은 20시간에 약 $15다. 컨슈머 서버는 한 대에 시간당 약 $0.10이다.
- SUT RDS가 있는 동안 cloudwatch exporter의 CloudWatch API 호출이 RDS 한 대 몫만큼 늘어 월 약 $17이 더 든다. exporter 설정([`deploy/cloudwatch/config.yml`](../../deploy/cloudwatch/config.yml))이 계정의 RDS를 모두 읽기 때문이다.
