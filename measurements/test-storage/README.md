# test-storage

저장단 측정용 SUT(테스트 대상 시스템)다. 개발 서버와 같은 스택을 따로 띄워, 개발 서버의 DB와 파라미터를 건드리지 않고 잰다.

- 측정 중일 수 있으니 배포하거나 끄기 전에 문인호에게 묻는다.
- 명령은 Terraform을 apply한 컴퓨터의 레포 루트에서 실행한다. 상태 파일이 그 컴퓨터에만 있다.

## 구성

| 경로 | 내용 |
| --- | --- |
| `sut/terraform/` | EC2 m7g.large, RDS db.m7g.large(PostgreSQL 18.4, Single-AZ), 보안 그룹, IAM 역할, 파라미터, 도메인, 자동 중지 |
| `sut/scripts/` | 배포, exporter 사용자 만들기, 켜고 끄기 |
| `load-generator/k6/` | k6 스크립트 |
| `lib/common.sh` | 스크립트가 같이 쓰는 함수 |

- 자원 이름 접두는 `meterengine-test-storage`, 도메인은 `test-storage.meterengine.com`, 파라미터 경로는 `/meterengine/test-storage`다.
- SUT에는 [`deploy/compose.prod.yml`](../../deploy/compose.prod.yml) 스택을 개발 서버와 똑같이 올린다. 경보는 아무도 받지 않는 Slack 주소로 간다.
- 매일 04:00(서울)에 EC2와 RDS가 자동으로 꺼진다.
- VPC, 서브넷, 호스티드 존, ECR 이미지는 개발 서버와 같이 쓰고 읽기만 한다. 역할은 `/meterengine/prod`를 읽지 못한다.

## 사전 준비

- Terraform 1.16, AWS CLI, `session-manager-plugin`, `jq`, `git`, `curl`, `dig`.
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
- RDS 때문에 10-15분 걸린다. EC2는 부팅하면서 docker, compose, 레포, `/etc/meterengine/deploy.conf`를 준비한다.

### 2. 배포

```bash
measurements/test-storage/sut/scripts/sut-deploy.sh
```

- SHA를 빼면 ECR `meterengine-backend`와 `meterengine-frontend` 둘 다에 이미지가 있는 가장 최근 main 커밋을 배포한다.
- 배포 전에 ECR 이미지, DNS, RDS 상태, EC2 상태, `deploy.conf`를 확인하고 하나라도 틀리면 멈춘다.
- 성공하면 아래 두 줄이 나온다. 첫 배포는 Caddy가 인증서를 받는다(같은 도메인 주 5회 제한).

```
[sut-deploy] DB 확인: meterengine-test-storage-db.<...>.rds.amazonaws.com
[sut-deploy] https://test-storage.meterengine.com/usage 가 200 을 돌려줬다
```

### 3. exporter 사용자 만들기

```bash
measurements/test-storage/sut/scripts/create-exporter-user.sh
```

- SUT EC2 안에서 docker `postgres:18`로 SUT RDS에 `meterengine_exporter`(`pg_monitor`)를 만든다. 여러 번 돌려도 된다.
- 성공하면 `meterengine_exporter 로그인 가능, pg_monitor 멤버 확인`이 나온다.

## 다시 배포

```bash
measurements/test-storage/sut/scripts/sut-deploy.sh <SHA 40자>
```

- SUT에는 CD가 없다. main이 바뀌어도 직접 배포할 때까지 그대로다.
- ECR은 태그 이미지를 최근 10개만 남긴다. 같은 SHA로 다시 재려면 이미지가 남아 있는지 먼저 본다.

```bash
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-backend --image-ids imageTag=<SHA>
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-frontend --image-ids imageTag=<SHA>
```

- a4bdf42 이전 커밋은 `deploy.conf`를 읽지 않아 스크립트가 거절한다.

## 켜고 끄기

```bash
measurements/test-storage/sut/scripts/sut-start.sh
measurements/test-storage/sut/scripts/sut-stop.sh
```

- 켤 때는 RDS를 먼저 켜고 쓸 수 있게 된 뒤 EC2를 켠다. 끝에 `/usage`가 200이 될 때까지 기다린다.
- 끌 때는 EC2를 멈춘 뒤 RDS를 멈춘다. 지우지는 않는다.
- 꺼졌는지 확인한다. 둘 다 `stopped`면 꺼진 것이다.

```bash
aws ec2 describe-instances --region ap-northeast-2 \
  --instance-ids "$(terraform -chdir=measurements/test-storage/sut/terraform output -raw sut_instance_id)" \
  --query 'Reservations[0].Instances[0].State.Name' --output text
aws rds describe-db-instances --region ap-northeast-2 --db-instance-identifier meterengine-test-storage-db \
  --query 'DBInstances[0].DBInstanceStatus' --output text
```

- RDS는 꺼 둔 지 7일 뒤 저절로 켜진다. 자동 중지가 다음 04:00에 다시 끈다.
- 04:00를 넘겨 측정하는 날에는 자동 중지를 끄고, 끝나면 `-var` 없이 apply 해 다시 켠다. plan에서 바뀌는 것은 스케줄 state뿐이어야 한다.

```bash
terraform -chdir=measurements/test-storage/sut/terraform apply -var auto_stop_state=DISABLED
terraform -chdir=measurements/test-storage/sut/terraform apply
```

## 서버에 들어가기

SSH가 없어 SSM으로만 붙는다.

```bash
aws ssm start-session --region ap-northeast-2 \
  --target "$(terraform -chdir=measurements/test-storage/sut/terraform output -raw sut_instance_id)"
```

컨테이너와 로그를 보는 방법은 [`deploy/README.md`](../../deploy/README.md) "문제 해결"과 같다.

## Grafana

Grafana는 SUT 안 `127.0.0.1:3001`에만 열려 있어 포트 포워딩으로 본다. 포워딩 세션은 켜 둔 채로 브라우저를 쓴다.

```bash
aws ssm get-parameter --region ap-northeast-2 --name /meterengine/test-storage/grafana-admin-password \
  --with-decryption --query Parameter.Value --output text
aws ssm start-session --region ap-northeast-2 \
  --target "$(terraform -chdir=measurements/test-storage/sut/terraform output -raw sut_instance_id)" \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3001"],"localPortNumber":["3001"]}'
```

- http://localhost:3001 에 `admin`과 위 비밀번호로 들어간다.
- 저장단 대시보드는 `RDS 인스턴스` 변수 기본값이 개발 서버 DB다. 아래 주소로 SUT DB를 골라 연다.
  http://localhost:3001/d/meterengine-storage?var-db=meterengine-test-storage-db

## 비밀번호

DB 마스터, exporter, Grafana 비밀번호는 Terraform이 만들고 상태 파일에 남기지 않는다. 값은 Parameter Store에서 본다.

```bash
aws ssm get-parameter --region ap-northeast-2 --name /meterengine/test-storage/db-password \
  --with-decryption --query Parameter.Value --output text
```

DB 비밀번호를 바꿀 때는 측정이 없을 때 SUT를 켠 상태에서 한다.

1. `sut/terraform/parameters.tf`의 `password_version`을 하나 올린다.
2. apply 한다. RDS 마스터와 `db-password`, `db-exporter-password`가 바뀐다.
3. exporter 비밀번호를 맞추고, 지금 SHA로 다시 배포해 서버의 env 파일을 새 값으로 쓴다.

```bash
terraform -chdir=measurements/test-storage/sut/terraform apply
measurements/test-storage/sut/scripts/create-exporter-user.sh
measurements/test-storage/sut/scripts/sut-deploy.sh <지금 SHA>
```

- 지금 SHA는 SUT 안에서 `sudo git -C /opt/meterengine rev-parse HEAD`로 본다.
- Grafana는 데이터 볼륨을 처음 만들 때만 admin 비밀번호를 받는다. 바꿀 때는 `grafana_admin_password_version`을 올려 apply 하고 다시 배포한 뒤, SUT 안에서 아래를 실행한다.

```bash
sudo docker compose --env-file /etc/meterengine/prod.env -f /opt/meterengine/deploy/compose.prod.yml \
  exec -T grafana sh -c 'grafana cli admin reset-admin-password "$GF_SECURITY_ADMIN_PASSWORD"'
```

## 상태 파일과 지우기

- 상태 파일은 apply한 컴퓨터에만 있고 커밋하지 않는다. 잃으면 Terraform으로 지울 수 없으니 지우지 않는다.
- 다른 컴퓨터에서 apply 하지 않는다. 이름이 겹쳐 실패한다.
- 평소에는 지우지 않고 끈다. 다시 만들면 인증서를 새로 받는다. RDS는 최종 스냅숏 없이 지워진다.

```bash
terraform -chdir=measurements/test-storage/sut/terraform destroy
```

## 비용

서울 온디맨드, 2026-10 기준이다.

- 켜 둔 동안 시간당 약 $0.34(EC2 $0.10, RDS $0.23, 탄력적 IP $0.005).
- 꺼 둔 동안 월 약 $8(EBS, RDS 스토리지, 탄력적 IP).
- SUT RDS가 있는 동안 cloudwatch exporter의 CloudWatch API 호출이 RDS 한 대 몫만큼 늘어 월 약 $12.5가 더 든다. exporter 설정([`deploy/cloudwatch/config.yml`](../../deploy/cloudwatch/config.yml))이 계정의 RDS를 모두 읽기 때문이다.
