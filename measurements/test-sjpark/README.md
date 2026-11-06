# test-sjpark

박성종이 측정에 쓰는 SUT(테스트 대상 시스템)다. 개발 서버와 같은 스택을 따로 띄워 개발 서버의 DB와 파라미터를 건드리지 않고 잰다. 측정할 때만 켜고, 매일 04:00(서울)에 자동으로 꺼진다.

- **측정 중일 수 있으니 배포하거나 끄기 전에 박성종에게 묻는다.**
- 명령은 레포 루트에서 실행한다. Terraform 상태는 S3 `meterengine-tfstate-928618307647`에 있어 어느 컴퓨터에서든 같다. 새 컴퓨터에서는 `terraform -chdir=measurements/test-sjpark/sut/terraform init`을 먼저 한 번 해야 스크립트와 아래 명령이 `terraform output`을 읽는다.
- 사양은 운영 가정(T 계열을 빼고 m 계열)을 따른다. 근거는 [Discussion #346](https://github.com/asm17-ms2/meterengine/discussions/346).

## 구성

| 경로 | 내용 |
| --- | --- |
| `sut/terraform/` | EC2 m7g.large, RDS db.m7g.large(PostgreSQL 18.4, Single-AZ), 보안 그룹, 공용 모니터링 수집 규칙, IAM 역할, 파라미터, 도메인, 자동 중지 |
| `sut/scripts/` | 배포, exporter 사용자 만들기, 켜고 끄기 |
| `lib/common.sh` | 스크립트가 같이 쓰는 함수 |
| [`loadgen/`](loadgen/README.md) | 부하 발생기 |
| [`k6/`](k6/README.md) | k6 스크립트 |
| [`scripts/deploy-report.sh`](scripts/deploy-report.sh) | 배포 중 실패 계산. 쓰는 법은 k6 README "배포 중 실패 측정" |

- 자원 이름 접두는 `meterengine-test-sjpark`, 도메인은 `test-sjpark.meterengine.com`, 파라미터 경로는 `/meterengine/test-sjpark`다.
- EC2 역할은 Parameter Store에서 자기 경로만 읽는다. `/meterengine/prod`와 `/meterengine/production`은 Deny로 막혀 있다.
- VPC, 서브넷, RDS 서브넷 그룹, 호스티드 존, ECR 이미지는 개발 서버와 같이 쓴다. 호스티드 존에는 SUT 도메인 레코드를 더하고, 나머지는 읽기만 한다.
- 감시는 공용 모니터링 서버가 한다. 서버에는 태그 `Role=app`, `Environment=test-sjpark`이 붙고, 공용 모니터링 보안 그룹에서 오는 9100(node exporter), 9101(cAdvisor)과 DB 5432만 들인다. 측정 환경이라 공용 Grafana 경보에서 빠진다. 보는 법은 "Grafana".
- 자원에는 `Project=meterengine`, `Owner=sjparkx1129`, `Purpose=sut` 태그가 붙는다. 자동 중지 스케줄(EventBridge Scheduler)은 태그를 붙일 수 없다.
- 자원 ID는 `terraform -chdir=measurements/test-sjpark/sut/terraform output`으로 본다.

## 사전 준비

- Terraform 1.16, AWS CLI, `session-manager-plugin`, `jq`, `git`, `curl`, `dig`.
- `ap-northeast-2`에 자원을 만들 수 있는 AWS 자격.

## 처음 띄우기

### 1. 만들기

```bash
terraform -chdir=measurements/test-sjpark/sut/terraform init
terraform -chdir=measurements/test-sjpark/sut/terraform plan -out=sut.tfplan
terraform -chdir=measurements/test-sjpark/sut/terraform apply sut.tfplan
terraform -chdir=measurements/test-sjpark/sut/terraform output
```

- `Plan:` 줄이 `N to add, 0 to change, 0 to destroy`가 아니면 멈춘다.
- RDS 때문에 15분까지 걸린다. EC2는 부팅하면서 docker, compose, 레포, `/etc/meterengine/deploy.conf`를 준비한다.

### 2. 배포

```bash
measurements/test-sjpark/sut/scripts/sut-deploy.sh
```

- SHA를 빼면 ECR `meterengine-backend`와 `meterengine-frontend` 둘 다에 이미지가 있는 main 커밋 가운데, `deploy/caddy/Caddyfile`과 `deploy/environments/test-sjpark.json`이 있고 `deploy/compose.prod.yml`이 호스트 9100을 여는 가장 최근 커밋을 배포한다. 고르는 법은 "다시 배포".
- 배포 전에 ECR 이미지, DNS, RDS 상태, EC2 상태를 확인하고 하나라도 틀리면 멈춘다. 서버의 `deploy.sh`도 DB 식별자를 `deploy/environments/test-sjpark.json`과 대조한다. 환경 파일이 없는 옛 커밋은 `deploy.conf`를 대조한다. EC2 준비가 끝나기 전이면 원격 명령이 실패하니 몇 분 뒤 다시 돌린다.
- 성공하면 아래 줄이 나온다. 첫 배포는 Caddy가 인증서를 받는다(같은 도메인 주 5회 제한).

```
[sut-deploy] DB 확인: meterengine-test-sjpark-db.<...>.rds.amazonaws.com
[sut-deploy] https://test-sjpark.meterengine.com/usage -> https://test-sjpark.meterengine.com/usage 가 200 을 돌려줬다(1회 시도)
```

### 3. exporter 사용자 만들기

```bash
measurements/test-sjpark/sut/scripts/create-exporter-user.sh
```

- 성공하면 `meterengine_exporter 로그인 가능, pg_monitor 멤버 확인`이 나온다.

### 4. 배포 시각 기록 켜기

- 이 SUT는 배포 중 실패를 재므로 `/etc/meterengine/deploy.conf`에 `DEPLOY_TIMING=on`을 더한다. 방법은 [`k6/README.md`](k6/README.md) "배포 중 실패 측정"의 "준비" 절.

## 다시 배포

```bash
measurements/test-sjpark/sut/scripts/sut-deploy.sh <SHA 40자>
```

- 배포 로그의 `파라미터 경로:` 줄이 `/meterengine/test-sjpark`인지 본다. `DB:` 줄은 스크립트가 `meterengine-test-sjpark-db`인지 확인하고 다르면 멈춘다.
- SUT에는 CD가 없다. main이 바뀌어도 직접 배포할 때까지 그대로다.
- 이 SUT는 HTTPS를 서버의 Caddy가 맡는다. 운영 배포에서 Caddy를 걷어낸 커밋 이후는 이 서버에서 뜨지 못해 스크립트가 고르지 않는다. 그래서 이 서버는 Caddy가 남은 마지막 운영 릴리스에 고정된다.
  - 공용 모니터링이 긁는 호스트 9100, 9101이 없는 옛 커밋도 고르지 않는다.
  - SHA를 빼면 최신 커밋부터 보며 Caddyfile, 환경 파일, 9100 포트 가운데 하나라도 없는 커밋을 건너뛰고, 건너뛴 이유를 로그에 남긴다.
  - SHA를 주면 서버에 명령을 보내기 전에 운영자 체크아웃에서 같은 조건을 확인하고, 맞지 않으면 멈춘다. 서버의 저장소는 바뀌지 않는다.
- Caddy가 남은 커밋의 환경 파일은 서버 안 Prometheus와 Grafana(`monitoring` 프로필)를 켠다. 스크립트는 배포 때 서버 사본의 프로필을 `agents`로 바꿔 띄우지 않고, 배포가 끝나면 남은 서버 안 감시 컨테이너를 따로 내린다. 볼륨(`prometheus-data`, `grafana-data`)은 지우지 않는다.
- `deploy.sh`를 스크립트 없이 직접 돌리면 그 커밋의 환경 파일대로 서버 안 감시가 다시 뜬다. 되돌릴 때가 아니면 `sut-deploy.sh`로만 배포한다.
- ECR은 태그 이미지를 최근 10개만 남긴다. 같은 SHA로 다시 재려면 이미지가 남아 있는지 먼저 본다.

```bash
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-backend --image-ids imageTag=<SHA>
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-frontend --image-ids imageTag=<SHA>
```

- a4bdf42 이전 커밋은 `deploy.conf`를 읽지 않아 스크립트가 거절한다.

### 보존 이미지

- Caddy가 있는 마지막 운영 릴리스의 두 이미지를 ECR에 남겨 둔다.
- 태그만 더 붙여서는 남지 않는다. 두 저장소(`meterengine-backend`, `meterengine-frontend`)의 수명 주기 규칙 1은 태그 이름과 상관없이(`tagPatternList ["*"]`) 태그 있는 이미지를 최근 10개만 남긴다.
- 그래서 `keep-`으로 시작하는 태그를 먼저 잡는 규칙을 우선순위 1로 넣고, 지금 두 규칙은 우선순위를 하나씩 뒤로 민다. 앞 규칙이 잡은 이미지는 뒤 규칙이 지우지 못한다. 정책을 다시 쓸 때 이 규칙을 빼지 않는다.
- 운영 ECR은 Terraform 밖이라 손으로 한다. 아래 순서를 두 저장소에 한 번씩 한다.

남길 커밋은 다음을 모두 맞아야 한다. 맞는 커밋 가운데 가장 최근 것을 고른다.

- main에 있는 운영 릴리스다. CD는 main 커밋 SHA로만 이미지를 올린다.
- `deploy/compose.prod.yml`에 공용 감시 포트 9100이 있다. 공용 모니터링 서버를 세운 커밋 이후다.
- `deploy/caddy/Caddyfile`이 있다. Caddy를 걷어낸 커밋 이전이다.

```bash
SHA=<SHA 40자>
git fetch --quiet origin
git merge-base --is-ancestor "$SHA" origin/main && echo "main에 있음"
git grep -q -F '"9100:9100"' "$SHA" -- deploy/compose.prod.yml && echo "9100 있음"
git cat-file -e "$SHA:deploy/caddy/Caddyfile" && echo "Caddyfile 있음"
```

- 세 줄이 다 나와야 한다. 두 저장소에 이 SHA 이미지가 아직 있는지는 위 `describe-images`로 본다.

규칙을 넣는다. 먼저 지금 정책이 위 두 규칙 그대로인지 보고, 다르면 아래 파일의 2번과 3번 규칙을 지금 값에 맞춘다.

```bash
REPOSITORY=meterengine-backend  # 두 번째는 meterengine-frontend
aws ecr get-lifecycle-policy --region ap-northeast-2 --repository-name "$REPOSITORY" \
  --query lifecyclePolicyText --output text
cat > /tmp/keep-lifecycle.json <<'EOF'
{"rules": [
  {"rulePriority": 1, "description": "keep- 태그 이미지는 지우지 않는다 (test-sjpark 보존)",
   "selection": {"tagStatus": "tagged", "tagPrefixList": ["keep-"], "countType": "imageCountMoreThan", "countNumber": 100},
   "action": {"type": "expire"}},
  {"rulePriority": 2, "description": "태그 있는 이미지는 최근 10개만 남긴다 (배포 10회분)",
   "selection": {"tagStatus": "tagged", "tagPatternList": ["*"], "countType": "imageCountMoreThan", "countNumber": 10},
   "action": {"type": "expire"}},
  {"rulePriority": 3, "description": "참조되지 않는 매니페스트를 14일 후 정리",
   "selection": {"tagStatus": "untagged", "countType": "sinceImagePushed", "countUnit": "days", "countNumber": 14},
   "action": {"type": "expire"}}
]}
EOF
aws ecr start-lifecycle-policy-preview --region ap-northeast-2 --repository-name "$REPOSITORY" \
  --lifecycle-policy-text file:///tmp/keep-lifecycle.json --query status --output text
aws ecr get-lifecycle-policy-preview --region ap-northeast-2 --repository-name "$REPOSITORY" --query status --output text
```

- 마지막 명령이 `COMPLETE`를 낼 때까지 다시 돌린다. 오류 없이 끝나면 정책을 넣는다.

```bash
aws ecr put-lifecycle-policy --region ap-northeast-2 --repository-name "$REPOSITORY" \
  --lifecycle-policy-text file:///tmp/keep-lifecycle.json --query repositoryName --output text
```

태그를 붙이고, 수명 주기 미리보기에서 그 이미지가 지울 목록에 없는지 본다.

```bash
aws ecr batch-get-image --region ap-northeast-2 --repository-name "$REPOSITORY" --image-ids imageTag="$SHA" \
  --accepted-media-types application/vnd.oci.image.index.v1+json application/vnd.docker.distribution.manifest.list.v2+json \
    application/vnd.oci.image.manifest.v1+json application/vnd.docker.distribution.manifest.v2+json \
  --output json > /tmp/image.json
jq -j '.images[0].imageManifest' /tmp/image.json > /tmp/manifest.json
aws ecr put-image --region ap-northeast-2 --repository-name "$REPOSITORY" --image-tag "keep-test-sjpark-${SHA:0:7}" \
  --image-digest "$(jq -r '.images[0].imageId.imageDigest' /tmp/image.json)" \
  --image-manifest-media-type "$(jq -r '.images[0].imageManifestMediaType' /tmp/image.json)" \
  --image-manifest file:///tmp/manifest.json --query 'image.imageId.imageDigest' --output text
aws ecr start-lifecycle-policy-preview --region ap-northeast-2 --repository-name "$REPOSITORY" --query status --output text
aws ecr get-lifecycle-policy-preview --region ap-northeast-2 --repository-name "$REPOSITORY" \
  --query "[status, length(previewResults[?imageTags && contains(join(',', imageTags), 'keep-')])]" --output text
```

- 매니페스트는 받은 바이트 그대로 넣는다. `--output text`로 받으면 바이트가 바뀌어 같은 이미지가 아닌 새 digest에 태그가 붙는다. `--image-digest`가 다르면 `put-image`가 거절한다.
- `COMPLETE`와 `0`이 나와야 한다. 0이 아니면 정책에 1번 규칙이 들어갔는지 다시 본다.

고를 커밋이 없다고 스크립트가 멈추면 이 태그의 이미지를 찾는다.

```bash
aws ecr describe-images --region ap-northeast-2 --repository-name meterengine-backend \
  --query "imageDetails[?imageTags && contains(join(',', imageTags), 'keep-test-sjpark-')].imageTags" --output text
```

- 나온 40자 SHA를 `sut-deploy.sh <SHA 40자>`로 준다.

## 켜고 끄기

켜기 전에 RDS 자동 재기동을 처리하는 흐름이 돌고 있지 않은지 확인한다. 돌고 있으면 측정 도중에 그 흐름이 DB를 끌 수 있으니, 결과가 0이 될 때까지 기다린 뒤 켠다(흐름은 최대 2시간 돈다).

```bash
aws stepfunctions list-executions --region ap-northeast-2 \
  --state-machine-arn arn:aws:states:ap-northeast-2:928618307647:stateMachine:meterengine-test-sjpark-rds-auto-stop \
  --status-filter RUNNING --query 'length(executions)'
```

"환경 빼기"로 이 환경의 감지를 뺀 뒤라면 상태 머신이 없어 0 대신 `StateMachineDoesNotExist` 오류가 난다. 이때는 DB를 끌 흐름이 없으니 기다리지 않고 켠다.

```bash
measurements/test-sjpark/sut/scripts/sut-start.sh
measurements/test-sjpark/sut/scripts/sut-stop.sh
```

- 켤 때는 RDS를 먼저 켜고 쓸 수 있게 된 뒤 EC2를 켜고, `/usage`가 리다이렉트를 따라가 200이 될 때까지 기다린다.
- 끌 때는 EC2를 멈춘 뒤 RDS를 멈춘다. 지우지는 않는다.
- 꺼졌는지 확인한다. 둘 다 `stopped`면 꺼진 것이다.

```bash
aws ec2 describe-instances --region ap-northeast-2 \
  --instance-ids "$(terraform -chdir=measurements/test-sjpark/sut/terraform output -raw sut_instance_id)" \
  --query 'Reservations[0].Instances[0].State.Name' --output text
aws rds describe-db-instances --region ap-northeast-2 --db-instance-identifier meterengine-test-sjpark-db \
  --query 'DBInstances[0].DBInstanceStatus' --output text
```

- RDS는 꺼 둔 지 7일 뒤 저절로 켜진다. [RDS 자동 재기동 중지](../terraform/modules/rds-auto-stop/README.md)가 이를 감지해 바로 다시 끈다. 이 흐름이 실패해도 다음 04:00 자동 중지가 끈다.
- 04:00를 넘겨 측정하는 날에는 자동 중지를 끄고, 끝나면 `-var` 없이 apply 해 다시 켠다. plan에서 바뀌는 것은 스케줄 state뿐이어야 한다.

```bash
terraform -chdir=measurements/test-sjpark/sut/terraform apply -var auto_stop_state=DISABLED
terraform -chdir=measurements/test-sjpark/sut/terraform apply
```

- RDS Multi-AZ는 [`test-storage/README.md`](../test-storage/README.md) "RDS Multi-AZ"를 따르되 test-storage를 test-sjpark로 바꾼다.

## 서버에 들어가기

SSH가 없어 SSM으로만 붙는다. 컨테이너와 로그를 보는 방법은 [`deploy/README.md`](../../deploy/README.md) "문제 해결"과 같다.

```bash
aws ssm start-session --region ap-northeast-2 \
  --target "$(terraform -chdir=measurements/test-sjpark/sut/terraform output -raw sut_instance_id)"
```

## Grafana

공용 Grafana https://grafana.meterengine.com 에서 대시보드 맨 위 `환경`을 `test-sjpark`으로 고른다. 도메인으로 열리기 전에는 [`deploy/README.md`](../../deploy/README.md) "공용 모니터링 서버"대로 9090을 포트 포워딩해 Prometheus에서 `env="test-sjpark"`로 조회한다.

- 보이는 것: 서버(node exporter), 컨테이너(cAdvisor), PostgreSQL(postgres exporter), RDS(CloudWatch) 지표.
- 보이지 않는 것: 백엔드 앱 지표와 Caddy 지표. 백엔드가 블루그린이라 호스트에 8080이 없어 공용 모니터링이 긁지 않는다.
- **서버 안 감시를 내린 뒤의 측정은 그 전과 기준선이 다르다.** 서버 안 Prometheus, Grafana, exporter가 쓰던 CPU와 메모리가 빠진다. 앞뒤 결과를 비교할 때 적어 둔다.

### 서버 안 감시로 되돌리기

Caddy가 남은 마지막 릴리스 SHA로 `deploy.sh`를 직접 돌리면 그 커밋의 환경 파일대로 서버 안 감시가 남은 볼륨의 옛 기록과 함께 다시 뜬다. "서버에 들어가기"로 붙어 root로 [`deploy/README.md`](../../deploy/README.md) "수동 배포"의 잠금 형식으로 부른다.

```bash
(exec 8>/etc/meterengine/deploy-command.lock && flock -n 8 && cd /opt/meterengine && git fetch --quiet origin 8>&- 9>&- && git checkout --quiet --force <SHA> 8>&- 9>&- && ./deploy/deploy.sh test-sjpark <SHA>)
```

- SUT 안 `127.0.0.1:3001`에만 열리고, 들어가는 법은 [`test-storage/README.md`](../test-storage/README.md) "Grafana"를 따르되 test-storage를 test-sjpark로 바꾼다. 비밀번호는 `/meterengine/test-sjpark/grafana-admin-password`다.
- 다음 `sut-deploy.sh`가 다시 내린다. 계속 쓰려면 이 감시 분리를 되돌리는 PR을 올린다.

## 비밀번호

DB 마스터, exporter, Grafana, 첫 운영자 비밀번호와 프론트 API 키, 암호화 키는 Terraform이 만들고 상태 파일에 남기지 않는다. 값은 Parameter Store에서 본다.

```bash
aws ssm get-parameter --region ap-northeast-2 --name /meterengine/test-sjpark/db-password \
  --with-decryption --query Parameter.Value --output text
```

- 운영자로 로그인할 때는 `operator-email`(`operator@test-sjpark.meterengine.com`)과 `operator-password`를 쓴다.
- **암호화 키는 `password_version`을 올려도 바뀌지 않는다.** 바꾸면 DB에 저장한 암호문을 풀지 못하므로 DB를 새로 만들 때만 `crypto_encryption_key_version`을 올린다.

바꾸는 절차는 [`test-storage/README.md`](../test-storage/README.md) "비밀번호"를 따르되 test-storage를 test-sjpark로 바꾼다.

## 지우기

지우기 전에 [`rds-auto-stop` README](../terraform/rds-auto-stop/README.md) "환경 빼기"로 test-sjpark의 자동 재기동 감지를 먼저 뺀다.

```bash
terraform -chdir=measurements/test-sjpark/sut/terraform destroy
```

- 평소에는 지우지 않고 끈다. 다시 만들면 인증서를 새로 받고 탄력적 IP가 바뀐다. 도메인은 Terraform이 새 IP로 고친다.
- RDS는 최종 스냅숏 없이 지워진다.
- 부하 발생기는 [`loadgen/README.md`](loadgen/README.md)대로 따로 지운다.

## 비용

서울 온디맨드, 2026-10 기준이다.

- 켜 둔 동안 시간당 약 $0.34(EC2 $0.10, RDS $0.23, 탄력적 IP $0.005).
- 꺼 둔 동안 월 약 $8(EBS, RDS 스토리지, 탄력적 IP).
