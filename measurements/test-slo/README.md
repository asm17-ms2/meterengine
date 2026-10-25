# test-slo

박성종이 측정에 쓰는 SUT(테스트 대상 시스템)다. 개발 서버와 같은 스택을 따로 띄워 개발 서버의 DB와 파라미터를 건드리지 않고 잰다. 측정할 때만 켜고, 매일 04:00(서울)에 자동으로 꺼진다.

- **측정 중일 수 있으니 배포하거나 끄기 전에 박성종에게 묻는다.**
- 명령은 레포 루트에서 실행한다. Terraform 상태는 S3 `meterengine-tfstate-928618307647`에 있어 어느 컴퓨터에서든 같다. 새 컴퓨터에서는 `terraform -chdir=measurements/test-slo/sut/terraform init`을 먼저 한 번 해야 스크립트와 아래 명령이 `terraform output`을 읽는다.
- 사양은 운영 가정(T 계열을 빼고 m 계열)을 따른다. 근거는 [Discussion #346](https://github.com/asm17-ms2/meterengine/discussions/346).

## 구성

| 경로 | 내용 |
| --- | --- |
| `sut/terraform/` | EC2 m7g.large, RDS db.m7g.large(PostgreSQL 18.4, Single-AZ), 보안 그룹, IAM 역할, 파라미터, 도메인, 자동 중지 |
| `sut/scripts/` | 배포, exporter 사용자 만들기, 켜고 끄기 |
| `lib/common.sh` | 스크립트가 같이 쓰는 함수 |
| [`loadgen/`](loadgen/README.md) | 부하 발생기 |
| [`k6/`](k6/README.md) | k6 스크립트 |
| [`scripts/deploy-report.sh`](scripts/deploy-report.sh) | 배포 중 실패 계산. 쓰는 법은 k6 README "배포 중 실패 측정" |

- 자원 이름 접두는 `meterengine-test-slo`, 도메인은 `test-slo.meterengine.com`, 파라미터 경로는 `/meterengine/test-slo`다.
- EC2 역할은 Parameter Store에서 자기 경로만 읽는다. `/meterengine/prod`는 Deny로 막혀 있다.
- VPC, 서브넷, RDS 서브넷 그룹, 호스티드 존, ECR 이미지는 개발 서버와 같이 쓴다. 호스티드 존에는 SUT 도메인 레코드를 더하고, 나머지는 읽기만 한다.
- Slack 웹훅은 아무도 받지 않는 주소라 SUT 경보가 팀 채널로 가지 않는다.
- 자원에는 `Project=meterengine`, `Owner=sjparkx1129`, `Purpose=sut` 태그가 붙는다. 자동 중지 스케줄(EventBridge Scheduler)은 태그를 붙일 수 없다.
- 자원 ID는 `terraform -chdir=measurements/test-slo/sut/terraform output`으로 본다.

## 사전 준비

- Terraform 1.16, AWS CLI, `session-manager-plugin`, `jq`, `git`, `curl`, `dig`.
- `ap-northeast-2`에 자원을 만들 수 있는 AWS 자격.

## 처음 띄우기

### 1. 만들기

```bash
terraform -chdir=measurements/test-slo/sut/terraform init
terraform -chdir=measurements/test-slo/sut/terraform plan -out=sut.tfplan
terraform -chdir=measurements/test-slo/sut/terraform apply sut.tfplan
terraform -chdir=measurements/test-slo/sut/terraform output
```

- `Plan:` 줄이 `N to add, 0 to change, 0 to destroy`가 아니면 멈춘다.
- RDS 때문에 15분까지 걸린다. EC2는 부팅하면서 docker, compose, 레포, `/etc/meterengine/deploy.conf`를 준비한다.

### 2. 배포

```bash
measurements/test-slo/sut/scripts/sut-deploy.sh
```

- SHA를 빼면 ECR `meterengine-backend`와 `meterengine-frontend` 둘 다에 이미지가 있는 가장 최근 main 커밋을 배포한다.
- 배포 전에 ECR 이미지, DNS, RDS 상태, EC2 상태, `deploy.conf`를 확인하고 하나라도 틀리면 멈춘다. EC2 준비가 끝나기 전이면 원격 명령이 실패하니 몇 분 뒤 다시 돌린다.
- 성공하면 아래 줄이 나온다. 첫 배포는 Caddy가 인증서를 받는다(같은 도메인 주 5회 제한).

```
[sut-deploy] DB 확인: meterengine-test-slo-db.<...>.rds.amazonaws.com
[sut-deploy] https://test-slo.meterengine.com/usage 가 200 을 돌려줬다
```

### 3. exporter 사용자 만들기

```bash
measurements/test-slo/sut/scripts/create-exporter-user.sh
```

- 성공하면 `meterengine_exporter 로그인 가능, pg_monitor 멤버 확인`이 나온다.

### 4. 배포 시각 기록 켜기

- 이 SUT는 배포 중 실패를 재므로 `/etc/meterengine/deploy.conf`에 `DEPLOY_TIMING=on`을 더한다. 방법은 [`k6/README.md`](k6/README.md) "배포 중 실패 측정"의 "준비" 절.

## 다시 배포

```bash
measurements/test-slo/sut/scripts/sut-deploy.sh <SHA 40자>
```

- 배포 로그의 `파라미터 경로:` 줄이 `/meterengine/test-slo`인지 본다. `DB:` 줄은 스크립트가 `meterengine-test-slo-db`인지 확인하고 다르면 멈춘다.
- SUT에는 CD가 없다. main이 바뀌어도 직접 배포할 때까지 그대로다.
- ECR은 태그 이미지를 최근 10개만 남긴다. 같은 SHA로 다시 재려면 이미지가 남아 있는지 먼저 본다.

```bash
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-backend --image-ids imageTag=<SHA>
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-frontend --image-ids imageTag=<SHA>
```

- a4bdf42 이전 커밋은 `deploy.conf`를 읽지 않아 스크립트가 거절한다.

## 켜고 끄기

```bash
measurements/test-slo/sut/scripts/sut-start.sh
measurements/test-slo/sut/scripts/sut-stop.sh
```

- 켤 때는 RDS를 먼저 켜고 쓸 수 있게 된 뒤 EC2를 켜고, `/usage`가 200이 될 때까지 기다린다.
- 끌 때는 EC2를 멈춘 뒤 RDS를 멈춘다. 지우지는 않는다.
- 꺼졌는지 확인한다. 둘 다 `stopped`면 꺼진 것이다.

```bash
aws ec2 describe-instances --region ap-northeast-2 \
  --instance-ids "$(terraform -chdir=measurements/test-slo/sut/terraform output -raw sut_instance_id)" \
  --query 'Reservations[0].Instances[0].State.Name' --output text
aws rds describe-db-instances --region ap-northeast-2 --db-instance-identifier meterengine-test-slo-db \
  --query 'DBInstances[0].DBInstanceStatus' --output text
```

- RDS는 꺼 둔 지 7일 뒤 저절로 켜진다. 자동 중지가 다음 04:00에 다시 끈다.
- 04:00를 넘겨 측정하는 날에는 자동 중지를 끄고, 끝나면 `-var` 없이 apply 해 다시 켠다. plan에서 바뀌는 것은 스케줄 state뿐이어야 한다.

```bash
terraform -chdir=measurements/test-slo/sut/terraform apply -var auto_stop_state=DISABLED
terraform -chdir=measurements/test-slo/sut/terraform apply
```

- RDS Multi-AZ는 [`test-storage/README.md`](../test-storage/README.md) "RDS Multi-AZ"를 따르되 test-storage를 test-slo로 바꾼다.

## 서버에 들어가기

SSH가 없어 SSM으로만 붙는다. 컨테이너와 로그를 보는 방법은 [`deploy/README.md`](../../deploy/README.md) "문제 해결"과 같다.

```bash
aws ssm start-session --region ap-northeast-2 \
  --target "$(terraform -chdir=measurements/test-slo/sut/terraform output -raw sut_instance_id)"
```

## Grafana

SUT 안 `127.0.0.1:3001`에만 열려 있어 포트 포워딩으로 본다. 들어가는 법은 [`test-storage/README.md`](../test-storage/README.md) "Grafana"를 따르되 test-storage를 test-slo로 바꾼다.

```bash
aws ssm get-parameter --region ap-northeast-2 --name /meterengine/test-slo/grafana-admin-password \
  --with-decryption --query Parameter.Value --output text
aws ssm start-session --region ap-northeast-2 \
  --target "$(terraform -chdir=measurements/test-slo/sut/terraform output -raw sut_instance_id)" \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3001"],"localPortNumber":["3001"]}'
```

## 비밀번호

DB 마스터, exporter, Grafana 비밀번호는 Terraform이 만들고 상태 파일에 남기지 않는다. 값은 Parameter Store에서 본다.

```bash
aws ssm get-parameter --region ap-northeast-2 --name /meterengine/test-slo/db-password \
  --with-decryption --query Parameter.Value --output text
```

바꾸는 절차는 [`test-storage/README.md`](../test-storage/README.md) "비밀번호"를 따르되 test-storage를 test-slo로 바꾼다.

## 지우기

```bash
terraform -chdir=measurements/test-slo/sut/terraform destroy
```

- 평소에는 지우지 않고 끈다. 다시 만들면 인증서를 새로 받고 탄력적 IP가 바뀐다. 도메인은 Terraform이 새 IP로 고친다.
- RDS는 최종 스냅숏 없이 지워진다.
- 부하 발생기는 [`loadgen/README.md`](loadgen/README.md)대로 따로 지운다.

## 비용

서울 온디맨드, 2026-10 기준이다.

- 켜 둔 동안 시간당 약 $0.34(EC2 $0.10, RDS $0.23, 탄력적 IP $0.005).
- 꺼 둔 동안 월 약 $8(EBS, RDS 스토리지, 탄력적 IP).
