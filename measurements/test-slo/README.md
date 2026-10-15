# test-slo

박성종이 측정에 쓰는 SUT(테스트 대상 시스템)다. 개발 서버와 같은 스택을 따로 띄워 개발 서버의 DB와 파라미터를 건드리지 않고 잰다. 측정할 때만 켜고, 매일 04:00(서울)에 자동으로 꺼진다.

- **측정 중일 수 있으니 배포하거나 끄기 전에 박성종에게 묻는다.**
- 사양은 운영 가정(T 계열을 빼고 m 계열)을 따른다. 근거는 [Discussion #346](https://github.com/asm17-ms2/meterengine/discussions/346).

## 구성

| 리소스 | 이름 | ID와 사양 |
| --- | --- | --- |
| EC2 | `meterengine-test-slo-server` | `i-02147d0eac9d1bafd`. m7g.large, Amazon Linux 2023 arm64, gp3 20GB, SSH 키 없음 |
| RDS | `meterengine-test-slo-db` | db.m7g.large Single-AZ, PostgreSQL 18.4, gp3 20GB, 퍼블릭 접근 없음 |
| 보안 그룹 | `meterengine-test-slo-ec2-sg`, `-rds-sg` | `sg-02856ccedd2e1a3fc`, `sg-032a3bdcf686dda08`. EC2는 80, 443. RDS는 EC2 보안 그룹에서 5432만 |
| IAM 역할 | `meterengine-test-slo-ec2-role` | Parameter Store는 `/meterengine/test-slo/*`만 읽는다 |
| IAM 역할 | `meterengine-test-slo-scheduler-role` | 이 SUT의 RDS와 EC2를 중지하는 권한만 있다 |
| 도메인 | `test-slo.meterengine.com` | 탄력적 IP `3.37.213.167`(`eipalloc-0333b38a54e9f607f`). 껐다 켜도 그대로다 |
| 자동 중지 | `meterengine-test-slo-stop-rds`, `-stop-ec2` | EventBridge Scheduler, 매일 04:00 Asia/Seoul |

- VPC, 서브넷, RDS 서브넷 그룹, 호스티드 존, ECR 이미지는 개발 서버와 같이 쓴다. 보안 그룹과 IAM 역할은 따로라 서로의 DB와 파라미터에 닿지 못한다.
- 리소스에는 `Owner=sjparkx1129`, `Purpose=sut` 태그가 붙어 있다. 스케줄은 태그를 붙일 수 없다.

## 켜고 끄기

- **RDS를 먼저 켜고 available이 된 뒤 EC2를 켠다.** RDS는 켜는 데 8분 안팎 걸린다. EC2가 먼저 뜨면 백엔드가 DB를 기다리며 재시작을 반복한다.

```bash
aws rds start-db-instance --db-instance-identifier meterengine-test-slo-db
aws rds wait db-instance-available --db-instance-identifier meterengine-test-slo-db
aws ec2 start-instances --instance-ids i-02147d0eac9d1bafd
```

```bash
aws ec2 stop-instances --instance-ids i-02147d0eac9d1bafd
aws rds stop-db-instance --db-instance-identifier meterengine-test-slo-db
```

- RDS는 꺼 둔 지 7일이 지나면 저절로 켜지는데, 다음 04:00에 다시 꺼진다.
- **04:00를 넘기는 측정을 하는 날에는 자동 중지를 끄고, 끝나면 다시 켠다.** `jq`가 필요하다.

```bash
for t in rds ec2; do
  aws scheduler get-schedule --name meterengine-test-slo-stop-$t --output json \
    | jq '{Name, ScheduleExpression, ScheduleExpressionTimezone, FlexibleTimeWindow, Target, State: "DISABLED"}' \
    > /tmp/stop-$t.json
  aws scheduler update-schedule --cli-input-json file:///tmp/stop-$t.json
done
# 다시 켤 때는 State를 "ENABLED"로 바꿔 같은 명령을 실행한다.
```

- 켜 둔 동안은 시간당 약 $0.34, 꺼 둔 동안은 스토리지와 탄력적 IP로 월 $8 안팎이 든다(서울 온디맨드, 2026-10 기준).

## 배포

- [`deploy/README.md`](../../deploy/README.md) "수동 배포"대로 하되 인스턴스 ID만 `i-02147d0eac9d1bafd`를 쓴다.
- 배포 로그의 `파라미터 경로:` 줄이 `/meterengine/test-slo`, `DB:` 줄이 `meterengine-test-slo-db`인지 본다. 다르면 개발 서버의 DB에 붙은 것이다.
- Grafana는 "대시보드 보기"와 같이 포트 포워딩으로 본다. 비밀번호는 `/meterengine/test-slo/grafana-admin-password`다.

## 다시 만들기

지운 뒤 다시 만들 때 쓴다. 새로 나온 ID로 위 표를 고친다.

```bash
ACCOUNT=928618307647
VPC=vpc-07498f0672b7f3c9e
SUBNET=subnet-0348a18c1d399c193        # meterengine-subnet-public1-ap-northeast-2a
ZONE=Z08728621CQ5R1K37A7N1             # meterengine.com
P=/meterengine/test-slo
TAGS="Key=Owner,Value=sjparkx1129 Key=Purpose,Value=sut"
TAG_LIST="{Key=Owner,Value=sjparkx1129},{Key=Purpose,Value=sut}"
```

### 1. EC2 역할

권한은 개발 서버 역할과 같고 Parameter Store 경로만 좁힌다.

```bash
aws iam create-role --role-name meterengine-test-slo-ec2-role --tags $TAGS \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
aws iam attach-role-policy --role-name meterengine-test-slo-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

cat > /tmp/ec2-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {"Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*"},
    {"Effect": "Allow",
     "Action": ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage"],
     "Resource": ["arn:aws:ecr:ap-northeast-2:$ACCOUNT:repository/meterengine-backend",
                  "arn:aws:ecr:ap-northeast-2:$ACCOUNT:repository/meterengine-frontend"]},
    {"Effect": "Allow",
     "Action": ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"],
     "Resource": ["arn:aws:ssm:ap-northeast-2:$ACCOUNT:parameter$P",
                  "arn:aws:ssm:ap-northeast-2:$ACCOUNT:parameter$P/*"]},
    {"Effect": "Allow",
     "Action": ["cloudwatch:GetMetricData", "cloudwatch:GetMetricStatistics", "cloudwatch:ListMetrics"],
     "Resource": "*"}
  ]
}
EOF
aws iam put-role-policy --role-name meterengine-test-slo-ec2-role \
  --policy-name meterengine-test-slo-ec2-policy --policy-document file:///tmp/ec2-policy.json

aws iam create-instance-profile --instance-profile-name meterengine-test-slo-ec2-role
aws iam add-role-to-instance-profile --instance-profile-name meterengine-test-slo-ec2-role \
  --role-name meterengine-test-slo-ec2-role
```

### 2. 보안 그룹

```bash
EC2_SG=$(aws ec2 create-security-group --group-name meterengine-test-slo-ec2-sg \
  --description "meterengine test-slo EC2" --vpc-id $VPC \
  --tag-specifications "ResourceType=security-group,Tags=[{Key=Name,Value=meterengine-test-slo-ec2-sg},$TAG_LIST]" \
  --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id $EC2_SG --ip-permissions \
  'IpProtocol=tcp,FromPort=80,ToPort=80,IpRanges=[{CidrIp=0.0.0.0/0}]' \
  'IpProtocol=tcp,FromPort=443,ToPort=443,IpRanges=[{CidrIp=0.0.0.0/0}]'

RDS_SG=$(aws ec2 create-security-group --group-name meterengine-test-slo-rds-sg \
  --description "meterengine test-slo RDS" --vpc-id $VPC \
  --tag-specifications "ResourceType=security-group,Tags=[{Key=Name,Value=meterengine-test-slo-rds-sg},$TAG_LIST]" \
  --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id $RDS_SG \
  --protocol tcp --port 5432 --source-group $EC2_SG
```

### 3. 파라미터

- 이름과 쓰이는 곳은 [`deploy/README.md`](../../deploy/README.md) "설정값"과 같다. `db-host`는 5단계에서 넣는다.
- Slack 웹훅은 없는 주소로 둬 경보가 팀 채널로 가지 않게 한다.

```bash
aws ssm put-parameter --name $P/db-port --type String --value 5432
aws ssm put-parameter --name $P/db-name --type String --value meterengine
aws ssm put-parameter --name $P/db-username --type String --value meterengine
aws ssm put-parameter --name $P/organization-id --type String --value d7cee55d-8c82-4afc-b996-6749d8b26a4e
aws ssm put-parameter --name $P/organization-name --type String --value SUT
aws ssm put-parameter --name $P/slack-webhook-url --type SecureString \
  --value https://hooks.slack.com/services/DUMMY/DUMMY/DUMMY
for k in db-password db-exporter-password grafana-admin-password; do
  aws ssm put-parameter --name $P/$k --type SecureString --value "$(openssl rand -hex 24)"
done
```

### 4. RDS

10분 안팎 걸린다. 기다리는 동안 5단계를 해도 된다.

```bash
aws rds create-db-instance \
  --db-instance-identifier meterengine-test-slo-db \
  --db-instance-class db.m7g.large \
  --engine postgres --engine-version 18.4 \
  --allocated-storage 20 --storage-type gp3 --storage-encrypted \
  --db-name meterengine --master-username meterengine \
  --master-user-password "$(aws ssm get-parameter --name $P/db-password --with-decryption --query Parameter.Value --output text)" \
  --db-subnet-group-name meterengine-rds-subnet-private \
  --vpc-security-group-ids $RDS_SG \
  --availability-zone ap-northeast-2a \
  --no-multi-az --no-publicly-accessible \
  --backup-retention-period 1 --no-deletion-protection --copy-tags-to-snapshot \
  --enable-performance-insights --performance-insights-retention-period 7 \
  --tags Key=Name,Value=meterengine-test-slo-db $TAGS
aws rds wait db-instance-available --db-instance-identifier meterengine-test-slo-db
```

### 5. EC2와 탄력적 IP

- 서브넷이 공인 IP를 자동으로 주지 않아, 탄력적 IP를 붙이기 전까지 SSM 에이전트가 등록되지 않는다.

```bash
AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 \
  --query Parameter.Value --output text)
INSTANCE_ID=$(aws ec2 run-instances \
  --image-id $AMI --instance-type m7g.large \
  --subnet-id $SUBNET --security-group-ids $EC2_SG \
  --iam-instance-profile Name=meterengine-test-slo-ec2-role \
  --metadata-options HttpTokens=required,HttpEndpoint=enabled \
  --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=20,VolumeType=gp3,Encrypted=true,DeleteOnTermination=true}' \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=meterengine-test-slo-server},$TAG_LIST]" \
                       "ResourceType=volume,Tags=[{Key=Name,Value=meterengine-test-slo-server},$TAG_LIST]" \
  --query 'Instances[0].InstanceId' --output text)
aws ec2 wait instance-running --instance-ids $INSTANCE_ID

read ALLOC_ID EIP < <(aws ec2 allocate-address --domain vpc \
  --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Name,Value=meterengine-test-slo},$TAG_LIST]" \
  --query '[AllocationId,PublicIp]' --output text)
aws ec2 associate-address --instance-id $INSTANCE_ID --allocation-id $ALLOC_ID

aws ssm put-parameter --name $P/db-host --type String \
  --value "$(aws rds describe-db-instances --db-instance-identifier meterengine-test-slo-db \
    --query 'DBInstances[0].Endpoint.Address' --output text)"
```

### 6. 도메인

```bash
aws route53 change-resource-record-sets --hosted-zone-id $ZONE --change-batch \
  "{\"Changes\":[{\"Action\":\"CREATE\",\"ResourceRecordSet\":{\"Name\":\"test-slo.meterengine.com\",\"Type\":\"A\",\"TTL\":300,\"ResourceRecords\":[{\"Value\":\"$EIP\"}]}}]}"
```

### 7. 서버 셋업과 첫 배포

1. [`deploy/README.md`](../../deploy/README.md) "서버 1회 셋업"을 그대로 하고, `deploy.conf`는 아래처럼 쓴다.

```bash
cat > /etc/meterengine/deploy.conf <<'EOF'
PARAM_PATH=/meterengine/test-slo
SITE_DOMAIN=test-slo.meterengine.com
SITE_ALIASES=
EOF
```

2. "모니터링"의 postgres exporter 사용자를 서버 안에서 만든다. RDS가 퍼블릭이 아니라서다.

```bash
P=/meterengine/test-slo
get() { aws ssm get-parameter --region ap-northeast-2 --name "$P/$1" --with-decryption --query Parameter.Value --output text; }
docker run --rm -e PGPASSWORD="$(get db-password)" postgres:18 \
  psql "host=$(get db-host) user=meterengine dbname=meterengine sslmode=require" -v ON_ERROR_STOP=1 \
  -c "CREATE USER meterengine_exporter WITH PASSWORD '$(get db-exporter-password)'" \
  -c "GRANT pg_monitor TO meterengine_exporter"
```

3. "배포"대로 main의 최신 커밋을 배포한다.

### 8. 자동 중지

```bash
aws iam create-role --role-name meterengine-test-slo-scheduler-role --tags $TAGS \
  --assume-role-policy-document "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"scheduler.amazonaws.com\"},\"Action\":\"sts:AssumeRole\",\"Condition\":{\"StringEquals\":{\"aws:SourceAccount\":\"$ACCOUNT\"}}}]}"
aws iam put-role-policy --role-name meterengine-test-slo-scheduler-role --policy-name meterengine-test-slo-stop \
  --policy-document "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"rds:StopDBInstance\",\"Resource\":\"arn:aws:rds:ap-northeast-2:$ACCOUNT:db:meterengine-test-slo-db\"},{\"Effect\":\"Allow\",\"Action\":\"ec2:StopInstances\",\"Resource\":\"arn:aws:ec2:ap-northeast-2:$ACCOUNT:instance/$INSTANCE_ID\"}]}"

ROLE_ARN=arn:aws:iam::$ACCOUNT:role/meterengine-test-slo-scheduler-role
aws scheduler create-schedule --name meterengine-test-slo-stop-rds \
  --schedule-expression 'cron(0 4 * * ? *)' --schedule-expression-timezone Asia/Seoul \
  --flexible-time-window Mode=OFF \
  --target "{\"Arn\":\"arn:aws:scheduler:::aws-sdk:rds:stopDBInstance\",\"RoleArn\":\"$ROLE_ARN\",\"Input\":\"{\\\"DbInstanceIdentifier\\\":\\\"meterengine-test-slo-db\\\"}\",\"RetryPolicy\":{\"MaximumRetryAttempts\":0}}"
aws scheduler create-schedule --name meterengine-test-slo-stop-ec2 \
  --schedule-expression 'cron(0 4 * * ? *)' --schedule-expression-timezone Asia/Seoul \
  --flexible-time-window Mode=OFF \
  --target "{\"Arn\":\"arn:aws:scheduler:::aws-sdk:ec2:stopInstances\",\"RoleArn\":\"$ROLE_ARN\",\"Input\":\"{\\\"InstanceIds\\\":[\\\"$INSTANCE_ID\\\"]}\",\"RetryPolicy\":{\"MaximumRetryAttempts\":0}}"
```

- 이미 꺼져 있을 때 실행되면 실패로 남지만 해는 없다.
- 제대로 걸렸는지는 `--schedule-expression 'at(<몇 분 뒤 시각>)' --action-after-completion DELETE`로 한 번만 도는 스케줄을 같은 대상에 걸어 실제로 꺼지는지 본다.

## 지우기

만든 순서의 반대로 지운다. ID는 위 표의 것이다.

```bash
aws scheduler delete-schedule --name meterengine-test-slo-stop-rds
aws scheduler delete-schedule --name meterengine-test-slo-stop-ec2
aws iam delete-role-policy --role-name meterengine-test-slo-scheduler-role --policy-name meterengine-test-slo-stop
aws iam delete-role --role-name meterengine-test-slo-scheduler-role

aws route53 change-resource-record-sets --hosted-zone-id Z08728621CQ5R1K37A7N1 --change-batch \
  '{"Changes":[{"Action":"DELETE","ResourceRecordSet":{"Name":"test-slo.meterengine.com","Type":"A","TTL":300,"ResourceRecords":[{"Value":"3.37.213.167"}]}}]}'

aws ec2 terminate-instances --instance-ids i-02147d0eac9d1bafd
aws ec2 wait instance-terminated --instance-ids i-02147d0eac9d1bafd
aws ec2 release-address --allocation-id eipalloc-0333b38a54e9f607f

aws rds delete-db-instance --db-instance-identifier meterengine-test-slo-db \
  --skip-final-snapshot --delete-automated-backups
aws rds wait db-instance-deleted --db-instance-identifier meterengine-test-slo-db

aws ec2 delete-security-group --group-id sg-032a3bdcf686dda08
aws ec2 delete-security-group --group-id sg-02856ccedd2e1a3fc

aws iam remove-role-from-instance-profile --instance-profile-name meterengine-test-slo-ec2-role \
  --role-name meterengine-test-slo-ec2-role
aws iam delete-instance-profile --instance-profile-name meterengine-test-slo-ec2-role
aws iam delete-role-policy --role-name meterengine-test-slo-ec2-role --policy-name meterengine-test-slo-ec2-policy
aws iam detach-role-policy --role-name meterengine-test-slo-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam delete-role --role-name meterengine-test-slo-ec2-role

aws ssm delete-parameters --names $(aws ssm get-parameters-by-path --path /meterengine/test-slo --query 'Parameters[].Name' --output text)
```
