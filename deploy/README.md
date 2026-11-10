# 배포

구성과 배포, 롤백, 서버 접속, 모니터링 확인 절차다.

## 구성

develop 머지는 staging에, main 머지는 production에 배포한다. 서버마다 다른 값은 `deploy/environments/<환경>.json`이 정한다. 비밀값은 넣지 않는다.

| 환경 | 언제 | 도메인 | 프로필 |
| --- | --- | --- | --- |
| staging | develop 머지 | staging.meterengine.com | agents |
| production | main 머지 | meterengine.com | agents |
| test-storage | `sut-deploy.sh`가 `rollout.sh`를 부른다 | test-storage.meterengine.com | agents |
| test-sjpark-m7g, test-sjpark-t4g | `sut-deploy.sh <사양>`이 `rollout.sh`를 부른다 | test-sjpark-<사양>.meterengine.com | agents |

| 프로필 | 서비스 |
| --- | --- |
| 없음(항상) | backend, frontend |
| agents | node-exporter, cAdvisor |

- `deploy.sh`는 env 파일과 컨테이너를 건드리기 전에 Parameter Store `db-host`의 RDS 식별자를 환경 파일과 대조하고, staging과 production은 이 서버가 환경 파일의 오토스케일링 그룹(`autoscaling_group_name`) 서버인지도 인스턴스 메타데이터 태그로 대조한다. 다르면 멈춘다.

아래 그림과 항목은 production 기준이다.

```
인터넷 --443--> [ALB meterengine-production-alb] --/v1/*--> [backend:8080] --> RDS PostgreSQL
                  (인증서, http는 https로 301,     \               ^
                   www는 apex로 301)                \--그 외--> [frontend:3000] (서버사이드 호출은 같은 서버의 backend로)

[오토스케일링 그룹 meterengine-production-app]  서버마다 backend, frontend, node-exporter:9100, cadvisor:9101
                                                부팅 때 버전 파라미터의 커밋으로 배포한다

[공용 모니터링 서버 meterengine-monitoring] --scrape--> 서버마다 8080, 9100, 9101과 환경별 DB --경보--> Slack
     ^
     \--3000-- 운영 ALB (grafana.meterengine.com)
```

- 도메인: https://meterengine.com (Route 53 호스티드 존). apex, www, grafana 레코드는 ALB 별칭이고 Terraform이 관리한다. 아래 "도메인" 참조.
- 앱 서버: 오토스케일링 그룹 `meterengine-production-app`(t4g.medium, arm64, Amazon Linux 2023, ap-northeast-2). "앱 서버 오토스케일링 그룹" 참조.
- DB: RDS `meterengine-db` (db.t4g.small, PostgreSQL 18, 단일 AZ).
- staging은 ALB, 오토스케일링 그룹 `meterengine-staging-app`, DB `meterengine-staging-db`(db.t4g.small)를 따로 쓴다.
- production과 staging은 실사용자가 없는 흐름 확인용이다. t4g 계열은 CPU 크레딧을 넘겨 쓰면 요금이 붙어, 부하 시험은 측정 환경(test-sjpark-m7g, test-sjpark-t4g, test-storage)에서만 한다.
- 이미지: ECR `meterengine-backend`, `meterengine-frontend`(staging은 `meterengine-staging-backend`, `meterengine-staging-frontend`). 태그는 배포한 커밋의 git SHA 40자.
  - 레지스트리 `928618307647.dkr.ecr.ap-northeast-2.amazonaws.com`.
- 설정과 비밀값: SSM Parameter Store `/meterengine/<환경>/*`.
- 인터넷에 열린 포트는 ALB의 80과 443이다. 앱 서버 보안 그룹은 ALB에서 오는 3000, 8080과 공용 모니터링 서버에서 오는 8080, 9100, 9101만 들인다. SSH 포트는 열려 있지 않고 접속은 SSM으로 한다.
- 백엔드와 프론트는 고정 대역의 `proxy` 네트워크에 붙는다. 이유는 "공개 인증의 실제 IP" 참조.
- compose 프로젝트 이름은 `meterengine-prod`다.

| 파일 | 내용 |
| --- | --- |
| `compose.prod.yml` | 앱 서버의 스택. backend, frontend와 agents 프로필의 node exporter, cAdvisor |
| `deploy.sh` | 서버 한 대에 커밋 하나를 띄운다. 부팅 스크립트와 사람이 서버 안에서 root로 부른다 |
| `rollout.sh` | 버전 파라미터에 SHA를 쓰고 오토스케일링 그룹 서버를 새로 바꾼다. CD와 사람이 서버 밖에서 부른다 |
| `environment.py`, `environments/` | 환경별 값과 이 서버가 그 환경의 서버인지 확인 |
| `server/` | 서버마다 systemd로 도는 10초 CPU 지표 스크립트와 유닛 |
| `compose.monitoring.yml`, `monitoring/` | 공용 모니터링 서버의 스택, 수집 설정, 기동 스크립트, 읽을 파라미터 이름 목록. "공용 모니터링 서버" 참조 |
| `cloudwatch/config.yml` | 공용 모니터링 서버의 cloudwatch exporter가 읽을 RDS와 ALB 지표 목록 |
| `grafana/provisioning/`, `grafana/dashboards/` | Grafana 데이터 소스, 대시보드, 경보의 정본 |
| `terraform/` | staging, production, 공용 모니터링의 Terraform 루트와 모듈 |
| `terraform/modules/app_fleet/image/` | 앱 서버 이미지에 넣을 것을 설치하는 스크립트와 그 이미지를 굽는 스크립트. "앱 서버 이미지" 참조 |

- 루트의 `docker-compose.yml`은 로컬 개발용이고 배포 스택과 무관하다.
- Grafana의 데이터 소스, 대시보드, 경보는 파일이 정본이다. 공용 모니터링 서버의 `start.sh`가 반영한다.

### 도메인

- 도메인 레코드는 staging `terraform/staging/dns.tf`, production `terraform/production/dns.tf`(apex, www, grafana)가 정한다. production의 세 레코드는 손으로 만든 것을 import 블록으로 가져온다.
- 레코드는 ALB 별칭이고 삭제 보호가 걸려 있다. ALB를 가리키므로 앱 서버를 최소 1대로 둔다.

## 사전 준비

배포를 직접 돌리거나 서버에 붙기 전에 갖춰야 하는 것이다.

- 로컬에 AWS CLI와 `session-manager-plugin`을 설치하고 `ap-northeast-2` 자격을 설정한다.
- `/meterengine/production/*` 파라미터가 먼저 등록되어 있어야 한다.
  - **compose나 `deploy.sh`가 값이 없으면 배포를 멈추므로, 값을 쓰는 구성이 파라미터 없이 머지되면 그 뒤로 뜨는 서버가 모두 실패한다.** 필수 값 목록은 `environment.py`에 있다. 등록 명령은 "설정값" 참조.
- Grafana의 GitHub 로그인은 공용 모니터링 서버의 Grafana가 직접 한다. 준비는 "공용 모니터링 서버" 참조.

### 서버 준비

앱 서버는 손으로 셋업하지 않는다. 미리 구운 이미지로 뜨고 부팅 스크립트가 배포까지 한다("앱 서버 오토스케일링 그룹").

- 부팅 스크립트가 `/etc/meterengine/instance.env`에 `ENVIRONMENT`, `PARAMETER_PATH`, `APP_VERSION_PARAMETER`, `AWS_REGION`을 적는다.
- `/etc/meterengine/prod.env`는 배포할 때마다 `deploy.sh`가 Parameter Store 값으로 새로 쓴다.
- `deploy/environments/<환경>.json`의 값이다.
  - `autoscaling_group_name`은 staging과 production에만 적고, `meterengine-<환경>-app`이어야 한다.
  - 프론트는 `https://<site_domain>`에서 온 공개 인보이스 조회와 결제 재시도만 받는다. compose가 이 값을 `METERENGINE_PUBLIC_ORIGIN`으로 넣는다.
  - `site_aliases`는 지금 배포에서 쓰지 않는다. 별칭 리다이렉트는 ALB 규칙(Terraform)이 정한다.
  - `image_repository_prefix`: ECR 저장소 이름 앞부분. `<값>-backend`, `<값>-frontend`에서 이미지를 받는다.
  - `backend_mem_limit`: 백엔드 컨테이너의 메모리 한도. `backend_max_ram_percentage`: 그 한도 중 JVM 힙의 최대 비율(%).
  - `compose_profiles`: 비우거나 `agents`.

## 배포

### 자동 배포

- develop에 머지되면 `.github/workflows/cd-staging.yml`이 staging에, main에 머지되면 `cd-production.yml`이 production에 배포까지 끝낸다. 따로 할 일은 없다. 내용은 둘 다 `deploy.yml`에 있다.
  - `backend/`, `frontend/`, `deploy/`, 그 워크플로 파일 중 하나라도 바뀐 머지만 배포한다. 문서나 demo만 바뀐 머지는 배포하지 않는다.
  - 배포 한 번이 서버 교체라, `deploy/` 안에서도 앱 서버가 읽지 않는 파일(Terraform, 시험, 문서, 공용 모니터링의 `monitoring/`, `compose.monitoring.yml`, `grafana/`, `cloudwatch/`)만 바뀐 머지는 배포하지 않는다.

```
머지    -> build (backend) | build (frontend)   ubuntu-24.04-arm 러너에서 병렬
        -> ECR push (태그 = 커밋 SHA)
        -> deploy: 그 커밋의 환경 파일에서 오토스케일링 그룹과 파라미터 경로를 읽고, 두 이미지가 ECR에 있는지 본다
        -> rollout.sh <그룹> <파라미터 경로>/app-version <SHA>: 버전 파라미터에 SHA를 쓰고 서버를 새로 바꾼다
        -> ALB 주소로 붙어 https://<site_domain>/usage 를 리다이렉트까지 따라가 마지막 응답이 200인지 외부에서 확인
```

- 최소 대수가 0인 그룹은 버전만 기록하고(`recorded_only=true`) 외부 확인을 건너뛴다. 새 서버가 뜰 때 이 값을 읽는다.
- Caddy와 블루그린으로 도는 커밋(`deploy/caddy/Caddyfile`이 있는 커밋)은 오토스케일링 그룹 서버에서 뜨지 않아 거절한다.
- **AWS 역할의 신뢰 정책은 GitHub Environment(`production`, `staging`) 한정이고, 각 Environment는 자기 브랜치에서만 돈다. 다른 브랜치에서 실행하면 막힌다.** PR 단계에서는 CD를 시험할 수 없다.
- 같은 SHA의 이미지가 ECR에 이미 있으면 빌드를 건너뛴다.
- `rollout.sh`는 진행 중인 새로 고침이 있으면 쓰기 전에 멈춘다. 이 배포가 실패하면 버전 파라미터를 이전 SHA로 되돌리고 서버를 다시 바꾼 뒤 실패로 끝난다. 다른 새로 고침이 끼어들면 파라미터를 그대로 두고 실패로 끝난다.
- Actions 로그에는 새로 고침 사유와 최근 오토스케일링 활동만 찍힌다. 서버 안 배포 로그는 서버에 붙어 본다 ("문제 해결" 참조).
- CD가 도는 동안 Terraform apply하지 않는다. 시작 템플릿이 바뀌는 apply는 진행 중인 새로 고침을 취소할 수 있다.

### 수동 배포

- 관리자 자격으로 로컬에서 CD와 같은 스크립트를 부른다. 두 이미지가 ECR에 먼저 있어야 한다. CD가 돌고 있지 않을 때 한다.

```bash
deploy/rollout.sh meterengine-<환경>-app /meterengine/<환경>/app-version <git-sha>
```

- 서버 한 대만 같은 버전으로 다시 띄울 때(파라미터 값을 고친 뒤 등)는 그 서버에 SSM 세션으로 붙어 root로 아래를 실행한다. checkout 전에 잠금을 잡아, 다른 배포가 돌고 있으면 checkout 전에 종료한다.
  - `<git-sha>`는 버전 파라미터의 값을 쓴다. 다른 값을 주면 그 서버만 다른 버전이 되고, 다음 교체 때 버전 파라미터의 값으로 돌아간다.

```bash
(exec 8>/etc/meterengine/deploy-command.lock && flock -n 8 && cd /opt/meterengine && git fetch --quiet origin 8>&- 9>&- && git checkout --quiet --force <git-sha> 8>&- 9>&- && ./deploy/deploy.sh <환경> <git-sha>)
```

- 세 번째 인자로 체크아웃할 git ref를 따로 줄 수 있다. 그때는 앞의 `git checkout`에도 그 ref를 준다. 주지 않으면 이미지와 같은 SHA로 맞춘다.

- 서버 안에서 다시 띄우면 그 서버는 컨테이너를 바꾸는 동안 요청을 받지 못한다. 서버가 한 대면 그동안 끊긴다.

- `deploy.sh`가 하는 일의 순서다. 먼저 환경 파일을 서버와 대조하고, 틀리면 아무것도 바꾸지 않고 멈춘다.

1. `/opt/meterengine`을 그 커밋(세 번째 인자를 주면 그 ref)으로 맞춘다.
2. Parameter Store `/meterengine/<환경>/*`를 읽어 `/etc/meterengine/prod.env`를 새로 만든다 (권한 600). 필요한 값이 빠져 있으면 기존 파일을 두고 멈춘다.
3. `db-host`의 RDS 식별자를 환경 파일과 대조하고 `DB:` 줄로 남긴다.
4. ECR에 로그인해 backend와 frontend 이미지를 받고 `up -d --remove-orphans`로 띄운다.
5. 백엔드 `/actuator/health`(DB 포함)가 UP인지 최대 180초 기다리고, 그 컨테이너의 이미지가 새 SHA인지 확인한다.
6. 프론트 `/health`가 ok를 돌려주는지 최대 90초 기다린다.
7. agents 프로필이 켜진 환경은 node exporter와 cAdvisor를 띄운다. 실패해도 경고만 남기고 앱은 그대로 둔다.
8. `deploy/server/cpu-metric.service`를 `meterengine-cpu-metric.service`로 설치하고 enable과 restart를 한다. checkout으로 바뀐 스크립트를 읽게 하려고 매번 다시 띄운다. 실패해도 경고만 남긴다.
9. 336시간보다 오래된 이미지를 정리한다.

- 어느 단계든 실패하면 컨테이너 로그나 실패 줄을 찍고 0이 아닌 코드로 끝난다. 부팅 중이면 서버가 healthy가 되지 못해 오토스케일링 그룹이 서버를 바꾼다.
- `deploy.sh`는 한 번에 하나만 돈다. 앞 배포가 돌고 있으면(`/etc/meterengine/deploy-command.lock`) 바로 실패한다.

## 롤백

- 이미지 태그가 커밋 SHA라 롤백은 이전 SHA로 다시 배포하는 것이다. 그 이미지는 ECR에 이미 있어 빌드가 필요 없다. 배포와 같이 서버를 그 SHA로 새로 바꾼다.
- **Caddy와 블루그린으로 도는 커밋으로는 되돌릴 수 없다.** 오토스케일링 그룹 서버에서 뜨지 않아 CD가 거절한다.
- **DB 마이그레이션은 되돌아가지 않는다. Flyway는 앞으로만 간다.** 스키마를 바꾼 배포를 되돌리면 옛 코드가 새 스키마 위에서 도는 상태가 된다.

### 되돌릴 SHA 찾기

```bash
aws ecr describe-images --repository-name meterengine-backend \
  --query 'reverse(sort_by(imageDetails,&imagePushedAt))[].[imageTags[0],imagePushedAt]' --output text
```

### Actions에서 롤백

1. GitHub > Actions > CD production(staging은 CD staging) > Run workflow.
2. **Use workflow from은 main(staging은 develop)으로 둔다.** 다른 브랜치를 고르면 GitHub Environment가 막는다.
3. `image_tag`에 되돌릴 커밋 SHA 40자를 넣고 실행한다.

- 빌드 job은 건너뛰고 배포만 다시 돈다.
- 새 서버는 그 커밋의 저장소로 뜨므로 compose 파일까지 그 시점 상태로 돌아간다.

### 손으로 롤백

Actions에 들어갈 수 없을 때 관리자 자격으로 "수동 배포"의 `rollout.sh`를 되돌릴 SHA로 부른다.

## DB 백업과 복구

- DB는 RDS `meterengine-db`다. 자동 백업이 있어 보존 기간 안의 어느 시각으로든 새 인스턴스에 복원할 수 있다. 원본은 그대로 남는다.
- **백업은 DB만 되살린다. `crypto-encryption-key`가 그 시각의 값과 다르면 복원한 토스페이먼츠 키, 빌링키, 웹훅 비밀값을 풀지 못한다.** "암호화 키 맞추기" 참조.

### 설정 확인

```bash
aws ssm get-parameter --name /meterengine/production/db-host --query Parameter.Value --output text
aws rds describe-db-instances --db-instance-identifier meterengine-db \
  --query 'DBInstances[0].{endpoint:Endpoint.Address,retentionDays:BackupRetentionPeriod,backupWindowUtc:PreferredBackupWindow,latestRestorable:LatestRestorableTime,deletionProtection:DeletionProtection,multiAz:MultiAZ,encrypted:StorageEncrypted,subnetGroup:DBSubnetGroup.DBSubnetGroupName,securityGroups:VpcSecurityGroups[].VpcSecurityGroupId,maxStorage:MaxAllocatedStorage,performanceInsights:PerformanceInsightsEnabled,logExports:EnabledCloudwatchLogsExports,copyTags:CopyTagsToSnapshot}'
aws rds describe-db-instance-automated-backups --db-instance-identifier meterengine-db \
  --query 'DBInstanceAutomatedBackups[0].RestoreWindow'
aws rds describe-db-snapshots --db-instance-identifier meterengine-db --snapshot-type manual \
  --query 'DBSnapshots[].[DBSnapshotIdentifier,SnapshotCreateTime,Status]' --output text
```

- `endpoint`가 `db-host` 값과 같아야 한다.
- 보존 기간은 7일 이상, 삭제 방지와 암호화는 켜져 있어야 한다.
- `maxStorage`는 50, `performanceInsights`와 `copyTags`는 true, `logExports`는 postgresql이어야 한다. 복원한 인스턴스는 이 값을 원본에서 물려받지 않아 "복원"의 명령으로 맞춘다.
- 보존 기간을 바꾸는 명령이다. **0에서 양수로, 양수에서 0으로 바꾸면 DB가 멈춘다.** 양수끼리 바꾸면 멈추지 않는다.

```bash
aws rds modify-db-instance --db-instance-identifier meterengine-db --backup-retention-period <일수> --apply-immediately
aws rds modify-db-instance --db-instance-identifier meterengine-db --deletion-protection
```

### 위험한 배포 전 스냅숏

- 인증 방식처럼 되돌리기 어려운 변경이나 데이터의 뜻을 바꾸는 마이그레이션을 배포하기 직전에 찍는다.
- 수동 스냅숏은 보존 기간이 지나도 지울 때까지 남는다.

```bash
aws rds create-db-snapshot --db-instance-identifier meterengine-db \
  --db-snapshot-identifier meterengine-db-before-<설명>-<YYYYMMDD>
aws rds wait db-snapshot-available --db-snapshot-identifier meterengine-db-before-<설명>-<YYYYMMDD>
```

### 복원

- **복원본에는 복원 시각 뒤에 원본에 쓰인 데이터가 없다.** 복원 시각부터 5의 배포로 백엔드가 복원본에 붙을 때까지 원본에 들어온 이벤트, 인보이스, 결제 시도가 복원본에서 빠진다.
- 먼저 main 머지를 멈추고 Actions에서 CD가 돌고 있지 않은지 본다. CD는 main HEAD를 그때의 `db-host`로 배포해서, 전환 전이면 백엔드가 원본 DB로 다시 뜨고 전환 뒤면 3에서 피한 마이그레이션이 복원본에 적용된다. 5를 마칠 때까지 멈추고, 6의 PR만 넣는다.
- 원본이 살아 있는 채로 되돌린다면 1 전에 앱 서버마다 붙어 `sudo -i` 한 뒤 백엔드를 멈춘다. 그러면 빠지는 구간이 멈춘 시각에서 끝난다. 그동안 API는 응답하지 않고, 5의 배포가 다시 띄운다.

```bash
for service in backend backend-blue backend-green; do
  for id in $(docker ps -q --filter label=com.docker.compose.project=meterengine-prod --filter "label=com.docker.compose.service=$service"); do
    docker stop -t 35 "$id"
  done
done
```

- 가장 최근으로 되살릴 때는 멈춘 뒤 "설정 확인"의 `latestRestorable`이 멈춘 시각보다 뒤가 될 때까지 기다렸다가 1을 한다. 복원 가능 시각은 몇 분 늦게 따라온다.

1. 새 인스턴스로 복원한다. 시각은 UTC다. 가장 최근으로 되살리려면 `--restore-time` 대신 `--use-latest-restorable-time`을 준다.
   - **서브넷 그룹과 보안 그룹을 원본과 같게 준다.** 빠뜨리면 기본 보안 그룹이 붙어 서버에서 붙지 못한다.
   - 로그 내보내기와 스냅숏 태그 복사는 원본에서 물려받지 않아 명령에 준다. 자동 확장 상한과 Performance Insights는 5에서 켠다.

```bash
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier meterengine-db \
  --target-db-instance-identifier meterengine-db-restore-<YYYYMMDD> \
  --restore-time <YYYY-MM-DDTHH:MM:SSZ> \
  --db-subnet-group-name meterengine-rds-subnet-private \
  --vpc-security-group-ids sg-068a54ab4cfa91730 \
  --enable-cloudwatch-logs-exports postgresql --copy-tags-to-snapshot \
  --no-publicly-accessible --no-multi-az
```

   - 스냅숏에서 되살릴 때는 위 명령 대신 아래 명령을 쓴다. 시각 옵션이 없고 새 인스턴스 이름은 `--db-instance-identifier`로 준다.

```bash
aws rds restore-db-instance-from-db-snapshot \
  --db-snapshot-identifier meterengine-db-before-<설명>-<YYYYMMDD> \
  --db-instance-identifier meterengine-db-restore-<YYYYMMDD> \
  --db-subnet-group-name meterengine-rds-subnet-private \
  --vpc-security-group-ids sg-068a54ab4cfa91730 \
  --enable-cloudwatch-logs-exports postgresql --copy-tags-to-snapshot \
  --no-publicly-accessible --no-multi-az
```

   - 어느 쪽이든 쓸 수 있게 될 때까지 기다린 뒤 엔드포인트를 본다.

```bash
aws rds wait db-instance-available --db-instance-identifier meterengine-db-restore-<YYYYMMDD>
aws rds describe-db-instances --db-instance-identifier meterengine-db-restore-<YYYYMMDD> \
  --query 'DBInstances[0].Endpoint.Address' --output text
```

2. 서버에 붙어 `sudo -i` 한 뒤 복원본을 읽어 본다. 마지막으로 적용된 마이그레이션 버전과 주요 표의 행 수가 원하는 시각의 것인지 본다.

```bash
RESTORED=<복원본 엔드포인트>
export PGPASSWORD=$(grep '^DB_PASSWORD=' /etc/meterengine/prod.env | cut -d= -f2-)
DB_NAME=$(grep '^DB_NAME=' /etc/meterengine/prod.env | cut -d= -f2-)
DB_USERNAME=$(grep '^DB_USERNAME=' /etc/meterengine/prod.env | cut -d= -f2-)
docker run --rm -e PGPASSWORD postgres:18 psql \
  "host=$RESTORED port=5432 dbname=$DB_NAME user=$DB_USERNAME sslmode=require" -At \
  -c 'SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1' \
  -c 'SELECT count(*) FROM organization' \
  -c 'SELECT count(*) FROM invoice' \
  -c 'SELECT count(*) FROM payment_attempt'
```

3. 배포할 SHA를 정한다. 지금 SHA를 다시 배포하면 복원본보다 새 마이그레이션이 기동 때 다시 적용된다. 그 마이그레이션 때문에 복원했다면 복원본의 마지막 버전까지만 가진 이전 SHA를 배포한다.
4. 복원 시각 뒤에 암호화 키를 바꿨으면 그 시각의 키로 되돌린다("암호화 키 맞추기").
5. 오토스케일링 그룹이 서버를 새로 띄우지 않게 멈추고, `db-host`를 복원본으로 바꾼 뒤 3에서 정한 SHA를 앱 서버마다 다시 띄운다. 환경 파일의 식별자가 아직 원본이라 새로 뜨는 서버는 환경 확인에서 멈추고, `RECOVERY_DB_IDENTIFIER`를 주는 아래 명령만 통과한다. Actions로는 안 된다. 배포 로그의 `DB:` 줄이 복원본인지 본다.
   - 복원본에 삭제 방지, 자동 확장 상한, Performance Insights를 원본과 같게 켠다. 복원으로는 켜지지 않고, 켜도 DB가 멈추지 않는다.

```bash
aws autoscaling suspend-processes --auto-scaling-group-name meterengine-production-app
aws ssm put-parameter --name /meterengine/production/db-host --type String --overwrite --value <복원본 엔드포인트>
aws rds modify-db-instance --db-instance-identifier meterengine-db-restore-<YYYYMMDD> \
  --deletion-protection --max-allocated-storage 50 \
  --enable-performance-insights --performance-insights-retention-period 7
aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names meterengine-production-app \
  --query 'AutoScalingGroups[0].Instances[].InstanceId' --output text
```

   - 위에서 나온 서버마다 SSM 세션으로 붙어 root로 아래를 실행한다.

```bash
(exec 8>/etc/meterengine/deploy-command.lock && flock -n 8 && cd /opt/meterengine && git fetch --quiet origin 8>&- 9>&- && git checkout --quiet --force <SHA> 8>&- 9>&- && RECOVERY_DB_IDENTIFIER=meterengine-db-restore-<YYYYMMDD> ./deploy/deploy.sh production <SHA>)
```

6. `deploy/environments/production.json`의 `database_identifier`를 복원본으로 고쳐 main에 넣는다. 3에서 이전 SHA를 골랐다면 그 마이그레이션을 되돌리는 변경도 같이 넣는다.
   - 머지 직전에 `aws autoscaling resume-processes --auto-scaling-group-name meterengine-production-app`로 그룹을 되살린다. 멈춘 채로는 CD의 새로 고침이 서버를 띄우지 못한다. 머지되면 CD가 main HEAD로 서버를 새로 바꾼다.
   - 되살린 뒤 머지 전까지 새로 뜨는 서버는 환경 확인에서 멈춰 교체가 되풀이된다. 일하는 서버는 남아 있어 서비스는 영향이 없다.
   - 이 뒤로 그 PR 이전 커밋으로 되돌릴 때도 `RECOVERY_DB_IDENTIFIER`가 필요하다. 그 커밋의 환경 파일에는 원본 식별자가 있다.

- 원본 인스턴스는 바로 지우지 않는다. 복원 시각과 멈춘 시각(멈추지 못했으면 전환한 시각) 사이에 들어온 행은 원본과 복원본을 대조해 찾는다. 삭제 방지가 켜져 있어 먼저 꺼야 지워진다.

### 암호화 키 맞추기

- 키는 바꾸지 않는 것이 원칙이다. 이력은 값을 찍지 않고 버전과 시각만 본다.

```bash
aws ssm get-parameter-history --name /meterengine/production/crypto-encryption-key \
  --query 'Parameters[].[Version,LastModifiedDate]' --output text
```

- 복원 시각에 쓰던 버전으로 되돌린다. 값을 화면에 찍지 않는다.

```bash
aws ssm put-parameter --name /meterengine/production/crypto-encryption-key --type SecureString --overwrite \
  --value "$(aws ssm get-parameter --name /meterengine/production/crypto-encryption-key:<버전> --with-decryption --query Parameter.Value --output text)"
```

## 서버 접속

SSH 키와 22번 포트가 없어 SSM으로만 붙는다.

- 앱 서버 ID는 오토스케일링 그룹에서 찾는다. 서버는 교체될 때마다 ID가 바뀐다.

```bash
aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names meterengine-production-app \
  --query 'AutoScalingGroups[0].Instances[].[InstanceId,LifecycleState,HealthStatus]' --output text
aws ssm start-session --target <서버 ID>
```

- 서버 포트를 로컬로 끌어오기.

```bash
aws ssm start-session --target <서버 ID> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3000"],"localPortNumber":["3000"]}'
```


## 공개 인증의 실제 IP

ALB는 받은 `X-Forwarded-For` 뒤에 접속 주소를 붙이므로 맨 오른쪽 값을 사용자 IP로 쓴다. 그 앞의 값은 사용자가 꾸밀 수 있다. 한도와 429 응답은 [백엔드 인증](../backend/README.md#공개-인증-요청-제한)을 따른다.

- 프론트는 사용자가 보낸 `X-MeterEngine-Client-IP`를 늘 지우고 맨 오른쪽 값으로 다시 써서 같은 서버의 백엔드에 넘긴다.
- 백엔드는 `METERENGINE_AUTH_RATE_LIMIT_TRUSTED_PROXIES`에 든 직접 연결에서 온 `X-MeterEngine-Client-IP`만 믿는다.
  - 백엔드와 프론트는 compose의 `proxy` 네트워크(`172.30.0.0/24`)에 붙고, 프론트는 `172.30.0.11`로 주소를 고정한다.
  - compose가 이 값의 기본값을 고정 주소 대역 `172.30.0.8/29`로 넣어 Parameter Store 파라미터가 따로 필요 없다. 다른 컨테이너는 `172.30.0.128/25`에서 주소를 받아 이 대역에 들지 않는다.
  - 주소나 대역을 바꾸면 compose의 고정 주소, `ip_range`, 이 기본값을 같이 바꾼다. 비우면 모든 사용자가 프론트 주소 하나의 한도를 나눠 쓴다. 전체 사설망을 일괄 신뢰하지 않는다.
  - 서버에 `172.30.0.0/24`와 겹치는 docker 네트워크가 있으면 compose가 `proxy` 네트워크를 만들지 못해 배포가 실패한다.
- 백엔드는 `METERENGINE_AUTH_RATE_LIMIT_LOAD_BALANCERS`에 든 직접 연결(ALB)에서만 맨 오른쪽 값을 읽는다. compose 기본값은 ALB가 놓인 공용 서브넷 `10.0.0.0/20`, `10.0.16.0/20`이다. 서브넷을 바꾸면 이 기본값도 같이 바꾼다.
- `X-Forwarded-For`가 여러 줄이거나 맨 오른쪽 값이 숫자 IP가 아니면 백엔드는 접속 상대 IP를 쓴다. 이때는 ALB 뒤 사용자들이 한도를 나눠 쓴다.

## 설정값

- 전부 Parameter Store `/meterengine/<환경>/` 아래에 있다. 아래 명령은 production 기준이다.
- 파라미터 이름이 대문자 환경변수 이름이 된다 (`db-host` -> `DB_HOST`).
- staging은 모든 파라미터를 `deploy/terraform/staging/parameters.tf`가 만든다.
- production은 DB 접속 정보와 `invoice-auto-finalize-enabled`만 `deploy/terraform/production/parameters.tf`가 갖고, 비밀값과 공개 저장소에 올리지 않는 값은 아래 명령으로 손으로 등록한다. 코드가 갖는 값을 손으로 바꾸면 다음 apply가 되돌린다.

| 파라미터 | 타입 | 쓰이는 곳 |
| --- | --- | --- |
| `db-host` | String | 백엔드 datasource URL |
| `db-port` | String | 백엔드 datasource URL |
| `db-name` | String | 백엔드 datasource URL |
| `db-username` | String | 백엔드 datasource |
| `db-password` | SecureString | 백엔드 datasource. 배포 때 KMS로 복호화한다 |
| `db-exporter-password` | SecureString | 공용 모니터링 서버의 postgres exporter가 RDS에 붙을 읽기 전용 사용자 `meterengine_exporter`의 비밀번호 |
| `organization-id` | String | 이 버전의 compose는 읽지 않는다. 옛 프론트가 조회하던 도입사이고, 옛 버전으로 되돌리는 배포가 요구해 남긴다 |
| `organization-name` | String | 이 버전의 compose는 읽지 않는다. 옛 프론트 상단 바의 이름이고, 옛 버전으로 되돌리는 배포가 요구해 남긴다 |
| `oauth2-proxy-client-id`, `oauth2-proxy-client-secret` | SecureString | 앱 서버는 읽지 않는다. 공용 모니터링 Grafana 로그인 값을 옮겨 올 원본이다("공용 모니터링 서버") |
| `crypto-encryption-key` | SecureString | 백엔드가 토스페이먼츠 키, 빌링키, 웹훅 비밀값을 암호화하는 키. **값을 바꾸면 이미 저장한 암호문을 풀지 못한다** |
| `operator-email` | String | 백엔드가 기동할 때 없으면 만드는 첫 운영자의 이메일 |
| `operator-password` | SecureString | 첫 운영자 비밀번호. 값을 바꾸고 재배포하면 새 비밀번호가 된다 |
| `meterengine-auth-rate-limit-login-ip-limit`, `-login-account-limit`, `-login-window`, `-signup-ip-limit`, `-signup-account-limit`, `-signup-window` | String | 선택. 로그인과 가입 한도와 구간. 없으면 compose가 백엔드 기본값(100, 10, 5m, 20, 3, 30m)을 넣는다 |
| `invoice-auto-finalize-enabled` | String | 선택. `true`면 인보이스 자동 확정이 매시 정각에 돈다. 없으면 compose가 `false`를 넣는다. 켜면 첫 정시가 확정 시각이 지난 과거 달을 모두 확정하므로 켜기 전에 팀에 알린다 |
| `ingest-warmup-api-key` | SecureString | 선택. 백엔드가 기동할 때 수집 경로를 데우는 예열 도입사의 ingest 키 원문. 없으면 예열을 건너뛴다("수집 경로 예열") |
| `ingest-warmup-organization-id`, `ingest-warmup-customer-id` | String | 선택. 예열 도입사와 예열 고객의 id. 없으면 compose가 빈 값을 넣는다 |
| `ingest-warmup-requests`, `ingest-warmup-time-limit` | String | 선택. 예열 요청 수와 시간 상한. 없으면 compose가 `1000`, `20s`를 넣는다. 숫자나 기간 형식이 아니면 백엔드가 뜨지 못한다. 시간 상한은 늘리지 않는다. 예열 중에는 readiness가 503이고 ALB는 15초 간격 5번 실패면 빼므로, 컨테이너가 죽고 빠르면 60초 뒤 서버가 교체된다. 스스로 다시 뜨는 데 약 23초, 상한 20초, 마지막 요청 대기 5초를 더하면 약 48초다. 상한은 예열을 시작한 때부터 재므로 다시 뜨는 데 35초를 넘기면 서버가 교체될 수 있다 |
| `frontend-api-key` | SecureString | 이 버전의 compose는 읽지 않는다. 옛 프론트가 백엔드를 부르던 API 키 원문이고, 옛 버전으로 되돌리는 배포가 요구해 남긴다 |
| `app-version` | String | 앱 서버가 띄울 커밋 SHA 40자. CD와 `rollout.sh`가 쓰고 서버가 부팅 때 읽는다. Terraform은 자리 값 `unset`만 만들고 되돌리지 않는다. `prod.env`에는 들어가지 않는다 |

- 새 파라미터를 등록한다.

```bash
aws ssm put-parameter --name /meterengine/production/<이름> --type String --value '<값>'
aws ssm put-parameter --name /meterengine/production/<이름> --type SecureString --value '<값>'
```

- 값을 만들어야 하는 비밀값은 아래 명령으로 등록한다. 값을 화면에 찍지 않는다.

```bash
aws ssm put-parameter --name /meterengine/production/crypto-encryption-key --type SecureString \
  --value "$(openssl rand -base64 32)"
aws ssm put-parameter --name /meterengine/production/operator-password --type SecureString \
  --value "$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
aws ssm put-parameter --name /meterengine/production/frontend-api-key --type SecureString \
  --value "me_live_$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
```

- 운영자로 로그인할 때 비밀번호는 Parameter Store에서 본다.

```bash
aws ssm get-parameter --name /meterengine/production/operator-password --with-decryption --query Parameter.Value --output text
```

- 측정 환경은 Terraform이 같은 이름의 파라미터를 만든다. test-sjpark 두 환경은 `measurements/test-sjpark/sut/module/parameters.tf`, test-storage는 `measurements/test-storage/sut/terraform/parameters.tf`다.

- 값을 고쳤으면 서버를 다시 띄워야 반영된다. 같은 SHA로 `rollout.sh`를 다시 부르면 서버를 새로 바꾼다("수동 배포").
- **값에 개행이 섞이면 `deploy.sh`가 배포를 멈춘다.** 콘솔에서 복사할 때 줄바꿈이 딸려 오지 않았는지 본다.

### 수집 경로 예열

새 서버의 백엔드는 readiness를 열기 전에 자기 수집 API를 불러 데운다. 동작은 [`backend/README.md`](../backend/README.md#수집-경로-예열)에 있다. 환경마다 예열 도입사, 고객, ingest 키를 한 번 만들어 Parameter Store에 넣는다. 넣기 전에는 예열 없이 뜬다.

- staging도 이 세 값은 Terraform이 만들지 않고 손으로 넣는다. 키는 비밀값이고 도입사 id와 고객 id는 그 환경 DB에서 만들어야 생긴다.

1. 가입 화면(`https://<호스트>/signup`)에서 도입사 이름을 `MeterEngine 예열 (삭제 금지)`로 가입한다. 비밀번호는 무작위로 만들고 보관하지 않는다.
2. [`backend/README.md`](../backend/README.md#배포-환경의-운영-키-발급) "배포 환경의 운영 키 발급"의 1, 2단계로 운영자 세션을 열고, 그 도입사를 active로 승인한다.
3. 같은 절의 3단계로 운영자를 그 도입사에 소속시키고 세션의 도입사를 바꾼다.
4. 키와 고객을 만들고 Parameter Store에 넣은 뒤 로그아웃한다.

```bash
# 2. pending 목록에서 예열 도입사 id를 찾아 승인한다
api "$BASE/v1/organizations?status=pending"
api -X PUT $BASE/v1/organizations/<도입사 id>/status -d '{"status":"active"}'

# 4. live 모드에 ingest 스코프만 주고 분당 한도는 두지 않는다. 응답의 secret이 원문이다
api $BASE/v1/api-keys -d '{"label":"수집 경로 예열","mode":"live","scopes":["ingest"]}'
read -rsp '예열 키: ' WARMUP_KEY && echo
# 고객은 그 키로 만들어야 키와 같은 live 환경에 들어간다. 응답의 id가 고객 id다
curl -s $BASE/v1/customers -H @<(printf 'Authorization: Bearer %s\n' "$WARMUP_KEY") \
  -H 'Content-Type: application/json' -d '{"name":"MeterEngine 예열","external_id":"meterengine-warmup"}'
aws ssm put-parameter --name /meterengine/<환경>/ingest-warmup-api-key --type SecureString --overwrite --value "$WARMUP_KEY"
aws ssm put-parameter --name /meterengine/<환경>/ingest-warmup-organization-id --type String --overwrite --value '<도입사 id>'
aws ssm put-parameter --name /meterengine/<환경>/ingest-warmup-customer-id --type String --overwrite --value '<고객 id>'
unset WARMUP_KEY
api -X DELETE $BASE/v1/sessions/current
unset SESSION
```

- zsh에서는 4의 `read`를 `read -rs '?예열 키: ' WARMUP_KEY`로 바꾼다.

5. 같은 SHA로 `rollout.sh`를 불러 서버를 바꾼다. 새 서버의 백엔드 로그에 `Ingest warm-up finished: requests=..., elapsed=...ms`가 남는다.

- 끌 때는 `ingest-warmup-api-key`를 지우고 같은 SHA로 `rollout.sh`를 부른다. Parameter Store는 빈 값을 담지 못한다.

```bash
aws ssm delete-parameter --name /meterengine/<환경>/ingest-warmup-api-key
```

- 예열 도입사를 정지하면 예열도 멈춘다.
- 받아들인 부작용
  - 예열 도입사와 고객이 운영자 도입사 목록과 그 도입사의 고객 목록에 보인다.
  - 인보이스 자동 확정이 켜진 환경에서는 달마다 예열 고객의 `no_events` 확정 시도 행이 남는다. 미터와 단가가 없어 청구는 생기지 않는다.
  - 서버를 바꿀 때마다 백엔드 요청 로그에 `127.0.0.1`에서 온 예열 요청 줄이 요청 수만큼 남는다.
  - 서버를 바꿀 때마다 요청 지표와 수집 중복 수가 예열 요청만큼 오른다. 확장이 보는 10초 CPU 지표는 readiness가 200이 되고 1분 뒤부터 올라가 새 서버의 예열이 들어가지 않는다.

## Terraform

- staging과 production의 서버, DB, 권한은 `deploy/terraform/<환경>`이 정하고, 상태는 S3에 있다.
- `versions.tf`가 고정한 Terraform 1.17.1로만 돌린다. 다른 버전은 init에서 멈추고, 더 새 버전이 한 번 쓴 상태는 1.17.1이 읽지 못한다.
- apply는 develop 최신 커밋에서만 한다. PR 브랜치의 plan은 미리 보기로만 쓴다.
  - main이나 릴리스 체크아웃에서는 plan도 apply도 하지 않는다. main은 릴리스 전까지 develop의 Terraform을 갖지 않아, 거기서 apply하면 사양이 되돌아가거나 develop에만 있는 자원이 지워진다.
- CD가 돌지 않을 때 apply한다. DB 클래스나 서버 사양을 바꾸면 몇 분 끊겨 겹친 배포의 헬스 확인이 실패한다.
- production plan은 입력이 없다.
  - 운영 버전 파라미터 `/meterengine/production/app-version`에 40자 SHA가 기록되기 전에는 production plan이 실패한다. main 릴리스의 CD가 이 값을 쓴다. 최소 1대 확인이라 의도한 동작이다.
- staging plan은 기본값 없는 입력 다섯 개를 `TF_VAR_` 환경 변수로 넣고, 값을 화면에 찍지 않는다.
  - `db_password_version`, `runtime_secret_versions`: 상태에 기록된 지금 버전을 그대로 쓴다. 버전이 같으면 write-only 값은 비교하지 않는다.
  - `db_password_wo`, `runtime_secrets`: Parameter Store의 지금 값을 `--with-decryption`으로 읽어 쓴다.
  - `operator_email`: Parameter Store `/meterengine/staging/operator-email`의 지금 값을 쓴다.
- plan에 아래가 하나라도 보이면 apply하지 않고 멈춘다.
  - 삭제나 교체
  - `aws_db_instance`에서 클래스 말고 AWS 쪽 속성의 변경. `password_wo_version` 변경도 포함한다
  - `aws_ssm_parameter.runtime`의 값이나 비밀 버전 변경
- plan 전문은 PR이나 이슈 같은 공개 기록에 붙이지 않는다. 바뀌는 수와 주소만 적는다.

## 앱 서버 오토스케일링 그룹

`deploy/terraform/modules/app_fleet`이 두 환경에 같은 모양으로 만든다.

| 자원 | 이름 |
| --- | --- |
| ALB | `meterengine-<환경>-alb` |
| 오토스케일링 그룹 | `meterengine-<환경>-app` |
| 보안 그룹 | `meterengine-<환경>-alb`, `meterengine-<환경>-asg` |
| 앱 서버 역할 | `meterengine-<환경>-app` |
| 버전 파라미터 | `/meterengine/<환경>/app-version` |

- ALB는 `/v1/*`를 backend(8080)로, 나머지를 frontend(3000)로 보낸다. 도메인이 아닌 Host는 404, http는 https로 301, production의 www는 apex로 301이다.
  - idle 130초, X-Forwarded-For는 append, 접속 로그는 끈다(토큰이 실린 URL이 남는다). production ALB는 삭제 보호가 켜져 있다.
- 서버는 미리 구운 앱 서버 이미지("앱 서버 이미지")로 뜨고 부팅 스크립트(`modules/app_fleet/templates/app-user-data.sh.tftpl`)가 배포까지 한다. 부팅 때는 패키지와 도구를 설치하지 않는다.
  1. 버전 파라미터를 읽는다. 40자 커밋 SHA가 아니면 저장소를 받기 전에 실패한다.
  2. 이미지에 docker, git, python3, compose가 있는지 본다. 하나라도 없으면 그 이름을 찍고 실패한다.
  3. 저장소를 그 커밋으로 받는다.
  4. `deploy/deploy.sh <환경> <SHA>`를 부른다. 앱과 감시 에이전트를 띄우고 CPU 지표 유닛 `meterengine-cpu-metric.service`를 설치한다.
- 배포는 `rollout.sh`가 버전 파라미터에 SHA를 쓰고 새로 고침(최소 건강 100%, 최대 200%)을 시작하는 것이다. 새 서버가 대상 그룹에서 healthy가 된 뒤 옛 서버가 빠진다.
- 측정 환경 test-storage도 이 부팅 스크립트를 사본 없이 읽는다. 바꾸면 다음 test-storage apply에서 시작 템플릿 새 버전이 나온다([`measurements/test-storage/README.md`](../measurements/test-storage/README.md)).
- 최소 대수는 1, 최대는 3이다(`<환경>/main.tf`의 `min_size`).
  - 최소 대수가 1 이상이면 버전 파라미터가 40자 SHA인지 plan에서 본다. `unset`이면 plan이 멈춘다.
  - 형식만 보므로, 그 커밋이 Caddy를 걷어낸 커밋인지는 apply 전에 `git cat-file -e <값>:deploy/caddy/Caddyfile`가 실패하는지로 본다. Caddy가 있는 커밋이면 서버가 ALB 헬스 체크를 통과하지 못해 교체만 되풀이한다.
- 최소 대수를 바꾸는 apply는 두 대상 그룹 모두에서 그만큼 healthy가 될 때까지 최대 20분 기다린다. 도메인 레코드는 그 뒤에 바뀐다.
  - 시간이 지나 실패하면 바로 다시 apply하지 않는다. 다시 돌리면 기다리지 않고 지나가 healthy 서버 없이 도메인이 넘어갈 수 있다. "문제 해결"의 `describe-target-health`로 두 대상 그룹에 healthy 서버가 있는지 먼저 본다.
  - 대기는 healthy 수가 최소 대수와 같아야 끝난다. 확장으로 대수가 늘어난 동안 최소 대수를 바꾸면 끝나지 않으니, 원하는 대수가 최소일 때 바꾼다.
- 확장: 서버마다 `deploy/server/cpu-metric.sh`가 10초마다 (CPU 사용률 / 원하는 대수)를 `MeterEngine/Scaling`의 `CPUUtilizationPerDesiredInstance`에 올리고, 확장 정책은 같은 칸의 합을 60%에 맞춘다. 3칸(30초) 연속 넘으면 늘린다. 서버별 평균을 쓰면 부팅 중인 서버가 빠져 대수를 부풀린다. 60은 test-storage 측정(서버 한 대가 초당 900건에 60%에서 65%)으로 잡은 추정값이다.
  - 백엔드 readiness가 200이 아니면 올리지 않고, 200이 된 뒤 1분도 올리지 않는다. 기동 직후 JIT로 튀는 CPU가 평균을 올려 한 대 더 띄우지 않게 하려는 것이다.
  - EC2의 `CPUUtilization`은 1분 값이라 판단에 3분이 걸려 쓰지 않는다.
- 배포 역할이 버전 파라미터를 쓰고 롤링을 시작한다. Terraform은 버전 값과 원하는 대수를 되돌리지 않는다.
- CD가 도는 동안 apply하지 않는다. 시작 템플릿이 바뀌는 apply는 롤링을 시작해, 배포가 시작한 롤링과 겹치면 실패한다.

### 대상 그룹 교체 주의

- 대상 그룹의 port, protocol, name_prefix, vpc_id, target_type을 바꾸면 그룹이 교체된다. 새 그룹이 먼저 생기고 리스너 규칙이 그쪽으로 옮겨진 뒤 옛 그룹이 지워진다.
- 서버가 있는 환경에서는 새 그룹의 대상이 healthy가 될 때까지 수십 초 동안 503이 난다.
- plan에 `aws_lb_target_group` 교체가 보이면 멈춘다. 꼭 바꿔야 하면 두 단계로 나눈다.
  1. 새 그룹을 만들어 ASG에 같이 붙이고 대상이 healthy인지 본다.
  2. 리스너 규칙을 새 그룹으로 옮기고 옛 그룹을 뗀다.

### production의 Terraform 밖 자원

- DB 보안 그룹 `meterengine-rds-sg`, ECR `meterengine-backend`, `meterengine-frontend`는 Terraform이 만들지 않는다. DB 보안 그룹에는 새 앱 서버와 공용 모니터링에서 오는 5432 규칙만 Terraform이 더한다.
- 배포 역할 `meterengine-github-actions-role`도 Terraform 밖이다. 롤링에 필요한 권한은 인라인 정책 `meterengine-production-rollout`으로 손으로 더한다.
  - `meterengine-production-app` 그룹의 새로 고침 시작과 취소, 오토스케일링 그룹과 ALB 조회, `/meterengine/production/app-version` 읽기와 쓰기.
- 운영 서버의 송신은 443과 DB 5432만 열린다. 다른 포트가 필요하면 `production/main.tf`의 `app_egress_ports`에 더한다.

### 공용 모니터링 연결

- 두 루트가 보안 그룹 `meterengine-monitoring`을 이름으로 찾아, 그 그룹에서 앱 서버로 오는 8080, 9100, 9101과 DB로 오는 5432를 연다. 이 그룹이 없으면 plan이 멈춘다.
- production 루트는 그 그룹에 운영 ALB에서 오는 3000을 더하고, 태그 `Role=monitoring`인 서버를 Grafana 대상 그룹에 붙인다(`terraform/production/grafana.tf`).
- staging은 exporter 비밀번호 파라미터 `/meterengine/staging/db-exporter-password`를 Terraform이 자리 값으로 만든다. 실제 값과 DB 사용자는 사람이 넣는다.

```bash
aws ssm put-parameter --name /meterengine/staging/db-exporter-password \
  --type SecureString --overwrite --value "$(openssl rand -hex 24)"
aws ssm start-session --target <staging 서버 ID>
```

- 세션 안에서 exporter 사용자를 만들거나 비밀번호를 맞춘다.

```bash
sudo bash /opt/meterengine/measurements/test-storage/sut/scripts/remote/upsert-exporter-user.sh /meterengine/staging
```

### 앱 서버 이미지

- staging, production, test-storage의 앱 서버가 이 이미지로 뜬다. 값은 `<환경>/main.tf`의 app_fleet `ami_id`와 test-storage의 `ami_id` 기본값이고, 서로 다르면 deploy 시험이 실패한다.
- `terraform/modules/app_fleet/image/provision.sh`가 이미지에 넣을 것을 정하고, `build-app-image.sh`가 그 스크립트로 이미지를 굽는다.
- 부팅 스크립트가 쓰는 도구를 늘리면 provision.sh에도 넣고 다시 굽는다.
- 기본 이미지(Amazon Linux 2023 arm64)에 docker, git, python3, openssl과 compose 플러그인을 더한다. docker는 부팅 때 시작하게 등록만 하고 굽는 동안 띄우지 않는다.
- 저장소 체크아웃, CPU 지표 유닛, `/etc/meterengine` 설정은 넣지 않는다. `/etc/machine-id`를 비워 두어 이 이미지로 뜬 서버마다 새 값을 만든다.
- 이미지 태그는 `Role=app-image`, `BaseImage`(기본 AMI), `ComposeVersion`, `SourceCommit`(구운 커밋)이다.

#### 굽기

- 계정 928618307647의 관리자 자격과 AWS CLI v2로, develop을 받은 체크아웃에서 돌린다. 리전은 스크립트가 ap-northeast-2로 정한다.
  - provision.sh에 커밋하지 않은 변경이 있거나 지금 커밋이 origin/develop에 없으면 아무것도 만들지 않고 멈춘다.
  - 쓰는 권한: `sts:GetCallerIdentity`, EC2의 보안 그룹 만들기와 송신 규칙 바꾸기와 지우기, 인스턴스 시작과 조회와 콘솔 출력과 종료, 이미지 만들기와 조회와 삭제 보호, 태그 달기.

- 기본 AMI는 인수로 준다. provision.sh만 바꿔 다시 구울 때는 가장 최근 이미지의 `BaseImage` 태그 값을, 보안 공지로 다시 구울 때는 최신 기본 AMI를 준다. 시작 템플릿의 `ami_id`는 이미 구운 이미지라 인수로 주지 않는다.

```bash
git switch develop && git pull
# 최신 기본 AMI로 다시 구울 때
base=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 \
  --query Parameter.Value --output text)
deploy/terraform/modules/app_fleet/image/build-app-image.sh "$base"
```

- 약 10분 걸린다. staging 공용 서브넷에 빌더(t4g.medium)를 띄워 provision.sh를 돌리고, 빌더가 스스로 꺼지면 이미지를 만든다. 빌더에는 역할이 없고, 임시 보안 그룹은 송신 443만 연다.
- 표준 출력은 `image_id=<AMI ID>` 한 줄이고, 그 이미지에는 삭제 보호가 켜진다. 진행 기록은 표준 오류로 나온다.
- 빌더가 시간 안에 꺼지지 않으면 콘솔 출력을 찍고 이미지 없이 실패한다.
- 이미지가 failed가 되면 바로 실패한다. 시간 안에 available이 되지 않으면 그 이미지를 삭제 보호 없이 남기고 실패한다.
- 결과 이미지가 시작 템플릿에 맞지 않으면(arm64, 루트 `/dev/xvda`, 30GiB 이하) 실패한다. 그 이미지는 삭제 보호 없이 남으니 deregister-image로 지우고 스냅숏도 delete-snapshot으로 지운다.
- 성공해도 실패해도 빌더와 임시 보안 그룹을 지운다. 지우지 못하면 진행 기록에 지울 명령을 찍는다. 스크립트가 강제로 끊겼으면 남은 것을 찾아 지운다.

```bash
aws ec2 describe-instances --filters Name=tag:Name,Values=meterengine-app-image-builder \
  Name=instance-state-name,Values=pending,running,stopping,stopped --query 'Reservations[].Instances[].InstanceId' --output text
aws ec2 describe-security-groups --filters Name=tag:Name,Values=meterengine-app-image-builder \
  --query 'SecurityGroups[].GroupId' --output text
```

- 이 이미지로 뜬 서버의 `/var/log/cloud-init-output.log` 앞부분은 굽던 때 기록이다. `[app-image] 굽기 끝` 줄 아래부터가 그 서버의 부팅 기록이다.
- 옛 이미지는 지금 쓰는 것과 그 직전 것만 남긴다. 지울 때는 삭제 보호를 끄고 이미지를 지운 뒤 스냅숏도 지운다.

```bash
aws ec2 disable-image-deregistration-protection --image-id <AMI ID>
aws ec2 deregister-image --image-id <AMI ID>
aws ec2 delete-snapshot --snapshot-id <스냅숏 ID>
```

#### 다시 굽는 계기

- 날짜가 아니라 계기가 생기면 굽는다.
  - provision.sh를 바꾸는 PR이 머지되면 같은 날 굽고 ami 값을 바꾸는 PR을 낸다. provision.sh만 머지하면 어느 서버에도 반영되지 않는다.
  - AL2023 보안 공지(ALAS)에 이미지 안 패키지(kernel, docker, containerd, runc, git, openssl, openssh, glibc)의 Critical이나 Important가 나오면 한 주 안에 새 기본 AMI로 굽는다.
- 스프린트를 시작할 때 최신 기본 AMI와 가장 최근 이미지의 `BaseImage` 태그를 비교하고 ALAS를 훑는다.

```bash
aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 \
  --query Parameter.Value --output text
aws ec2 describe-images --owners self --filters Name=tag:Role,Values=app-image \
  --query 'sort_by(Images, &CreationDate)[-1].[ImageId, Tags[?Key==`BaseImage`].Value | [0]]' --output text
```

- compose 버전을 올릴 때는 provision.sh와 `terraform/monitoring/bootstrap.sh`를 같이 고친다. 각 파일이 같은 버전과 sha256을 따로 고정하고, 서로 다르면 deploy 시험이 실패한다.

## 모니터링

- 모든 환경의 앱 서버와 DB를 공용 모니터링 서버 한 대가 긁는다. 앱 서버에는 agents 프로필의 node exporter와 cAdvisor만 뜬다.
- postgres exporter는 RDS에 `pg_monitor` 권한의 읽기 전용 사용자 `meterengine_exporter`로 붙는다. 새 RDS에는 이 사용자를 먼저 만든다.

```sql
CREATE USER meterengine_exporter WITH PASSWORD '<db-exporter-password>';
GRANT pg_monitor TO meterengine_exporter;
```

### 공용 모니터링 서버

EC2 `meterengine-monitoring` 한 대가 모든 환경의 앱 서버와 DB를 긁는다. 서버와 권한은 `deploy/terraform/monitoring`이 정한다.

- 앱 서버는 태그 `Role=app`인 실행 중 서버를 30초마다 다시 찾아 백엔드 `8080`, node exporter `9100`, cAdvisor `9101`을 긁는다. `Environment` 태그가 `env` 라벨, 인스턴스 ID가 `instance` 라벨이 된다.
- DB는 환경마다 postgres exporter를 하나씩 띄워 `env`를 붙이고, RDS 지표는 DB 식별자에서 `env`를 만든다.
- 서버나 DB 보안 그룹이 보안 그룹 `meterengine-monitoring`을 들여야 긁힌다. 들이지 않은 대상은 down으로 보인다. 다른 루트가 이 이름으로 찾으므로 이름을 바꾸지 않는다.
- 들어오는 규칙은 운영 ALB에서 오는 3000 하나다. Prometheus는 서버의 127.0.0.1:9090, Grafana는 3000에 뜬다.
- 운영 ALB가 `grafana.meterengine.com`을 이 서버의 3000으로 보낸다. 인증서는 사이트와 따로 발급해 리스너에 더하고, `/metrics`는 ALB가 404로 끝낸다.
  - 서버를 다시 만들면 다음 production apply가 새 서버를 대상 그룹에 붙일 때까지 Grafana가 503이다.
- 지표 보관은 15일 또는 20GB 중 먼저 닿는 쪽에서 오래된 것부터 지운다. 기록은 서버 디스크에만 있어 서버를 다시 만들면 지워진다.
- 서버가 읽는 파라미터 이름은 `monitoring/parameters.json` 하나가 정한다. `start.sh`와 서버 역할이 이 파일을 같이 읽어, 목록 밖 파라미터는 읽지 못한다. 환경을 더하면 이 파일과 `compose.monitoring.yml`, `monitoring/prometheus/prometheus.yml`을 같이 고친다.
- 환경의 `db-exporter-password`가 없으면 그 환경 exporter는 띄우지 않는다.

서버를 만들면 `/meterengine/monitoring/` 아래 비밀 파라미터가 생긴다. Terraform은 값을 쓰기만 하고 읽거나 되돌리지 않는다.

| 파라미터 | 처음 값 | 할 일 |
| --- | --- | --- |
| `grafana-admin-password` | 난수 | 없음 |
| `grafana-github-client-id`, `grafana-github-client-secret` | `unset` | #526의 조직 `asm17-ms2` 소유 OAuth 앱 값을 그대로 쓴다. 운영 `/meterengine/production/oauth2-proxy-client-id`, `-client-secret` 값을 아래 명령으로 옮긴다. `unset`이 남아 있으면 `start.sh`가 멈춘다 |
| `slack-webhook-url` | 받는 곳 없는 자리 주소 | 실제 주소를 넣는다. 자리 주소가 남아 있으면 경보가 아무 데도 가지 않는다 |

- 값을 화면에 찍지 않고 옮긴다.

```bash
for name in client-id client-secret; do
  aws ssm put-parameter --overwrite --type SecureString --name "/meterengine/monitoring/grafana-github-$name" \
    --value "$(aws ssm get-parameter --name "/meterengine/production/oauth2-proxy-$name" --with-decryption --query Parameter.Value --output text)" >/dev/null
done
```

- 그 앱의 리다이렉트 URI에 `https://grafana.meterengine.com/login/github`를 더한다. Grafana는 이 경로로 로그인을 시작하고 돌아온다. 조직 owner가 더하고, Grafana를 도메인으로 공개하는 apply 전에 더한다.
  - 지금 GitHub OAuth 앱은 wildcard를 켜지 않으면 등록한 경로만 받는다. 경로 없는 주소로 바꾸면 `/login/github`가 거절된다.

띄우거나 설정을 바꿀 때는 서버에 붙어 저장소를 원하는 SHA로 바꾼 뒤 `start.sh`를 부른다. `start.sh`는 스스로 체크아웃하지 않고 저장소 HEAD가 인자와 같은지만 본다.

```bash
aws ec2 describe-instances --filters Name=tag:Role,Values=monitoring Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].InstanceId' --output text
aws ssm start-session --target <서버 ID>
```

세션 안에서 아래를 부른다.

```bash
sudo bash -c 'cd /opt/meterengine && git fetch origin && git checkout --force <SHA> && ./deploy/monitoring/start.sh <SHA>'
```

- Grafana는 GitHub `asm17-ms2` 조직 로그인만 받고 로그인 폼과 기본 인증은 꺼져 있다. GitHub 로그인이 안 되면 Grafana 화면에 들어갈 수 없고, 9090 포트 포워딩으로 Prometheus 화면에서 조회한다.

```bash
aws ssm start-session --target <서버 ID> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["9090"],"localPortNumber":["9090"]}'
```

### 대시보드 보기

- https://grafana.meterengine.com 은 운영 ALB를 거쳐 공용 모니터링 서버의 Grafana로 간다. GitHub로 로그인하고 `asm17-ms2` 조직 멤버만 들어온다.
  - 조직 멤버인지는 로그인할 때만 보므로, 조직에서 뺀 사람도 로그인이 유지되는 동안은 들어온다.
  - Prometheus 화면은 밖에 열지 않는다. 쿼리는 Grafana Explore에서 한다.
- Grafana의 MeterEngine 폴더에서 대시보드를 연다.
  - 대시보드마다 맨 위 `환경` 변수로 `env`를 고른다. 관측 시스템 패널은 환경을 가리지 않는다.
  - 앱 자원 대시보드의 `서버` 변수는 고른 환경의 백엔드 서버다. 서버 안 Grafana에서는 백엔드 색이라 특정 색을 고르면 서버와 컨테이너 패널이 빈다.
  - MeterEngine 서비스 개요는 가용성, 요청 수, 5xx 비율, 느린 API와 ALB의 요청, 5xx, 지연, 정상 서버 수다.
    - ALB 패널은 cloudwatch exporter가 읽는 CloudWatch 지표라 3분쯤 늦다. 앱 서버 대상 그룹만 읽는다.
    - 비용: `cloudwatch/config.yml`의 ALB 지표마다 1분에 한 번씩 CloudWatch를 조회하고, 대상 그룹 지표는 대상 그룹마다 조회한다. 계정의 무료 한도를 넘으면 조회 1,000번에 0.01달러다.
  - MeterEngine API 상세는 uri와 method로 고른 API의 지연과 오류, 그리고 전체 요청의 인증, 호출 제한, 감사 기록 실패 지표다.
  - MeterEngine 이벤트 수집은 수집 API 요청, 이벤트 결과, 저장 실패 지표다. 맨 아래 접힌 DB 왕복 행은 단건 수집이 거치는 DB 왕복별 평균과 p99다.
  - MeterEngine 청구와 결제는 인보이스 확정, 결제, 토스페이먼츠 지표다.
  - MeterEngine 예약 작업과 웹훅 발송은 예약 작업 실행과 웹훅 전달, 적체 지표다.
  - MeterEngine 앱 자원은 JVM, 톰캣, 커넥션 풀, 서버와 컨테이너 자원, 관측 시스템 상태다.
  - MeterEngine DB는 PostgreSQL과 RDS 지표다.
- 처음 로그인하면 GitHub 로그인 이름으로 Grafana 사용자가 생기고 권한은 Editor다.
  - 파일로 관리하는 대시보드와 경보 규칙은 화면에서 저장되지 않는다. 바꾸려면 파일을 고쳐 배포한다.
  - 화면에서 새로 만든 대시보드는 Grafana 볼륨에만 남는다. 오래 쓸 것은 JSON으로 내보내 `grafana/dashboards/`에 넣고 PR로 올린다.

GitHub 로그인이 안 되면 공용 모니터링 서버의 9090을 포트 포워딩해 Prometheus 화면에서 조회한다("공용 모니터링 서버").

### 경보

- Slack으로 간다 (`slack-webhook-url`).
- 경보는 `env`마다 따로 울린다. 측정 환경(`test-`로 시작하는 `env`)은 모든 경보에서 뺀다. `env` 라벨이 없는 서버 안 Prometheus에서는 지금처럼 그 서버 하나를 본다.
- 같은 경보의 재알림 주기는 4시간이다.

| 경보 | 조건 | 뜻 |
| --- | --- | --- |
| 백엔드 헬스체크 실패 | `max by (env) (up{job="backend"}) < 1` 1분 지속 | 그 환경의 어느 백엔드 서버도 scrape에 응답하지 않는다. 프로세스 다운이나 응답 불능 |
| 앱 서버 없음 | staging이나 production에서 찾은 앱 서버 0대, 1분 지속 | 오토스케일링 그룹에 실행 중인 서버가 없다. 서버가 0대면 위 경보는 계열이 사라져 울리지 않아 따로 센다 |
| 5xx 응답 비율 초과 | 5분 창 5xx 비율 > 5%, 5분 지속 | 요청이 실패로 새고 있다 |
| CPU 사용률 초과 | 서버마다 5분 평균 > 80%, 10분 지속 | 처리량 한계이거나 폭주 프로세스 |
| 예약 작업 실패 | 15분 안에 실행이 예외로 끝나거나 대상 일부가 실패 | 작업이 일을 다 끝내지 못했다. 작업 이름별로 울린다 |
| 예약 작업 미실행 | 마지막 성공 뒤 5분(30초, 1분 주기), 30분(10분 주기), 25시간(하루 주기) | 작업이 멈췄거나 계속 실패한다. 한 번도 실행되지 않은 작업은 잡지 못한다 |
| 인보이스 자동 확정 미실행 | 자동 확정이 2시간 넘게 성공하지 않음 | 마지막 성공이 끝난 뒤 2시간 안에 다음 성공이 없다. 정시 실행이 빠졌거나 실패했거나, 그 달 첫 훑기가 오래 걸린다. 꺼져 있으면 울리지 않는다 |
| 인보이스 자동 확정 실패 남음 | 마지막 자동 확정 시도가 실패인 고객과 달 > 0. 기동 때와 매 정시 실행 뒤에 센다. test 환경은 뺀다 | 서버 쪽 오류로 확정하지 못한 인보이스가 남았다. 해결된 뒤에도 다음 정시 한 번은 더 울릴 수 있다. 보류(단가 없는 사용량)는 여기서 울리지 않는다 |
| 결제 결과 모름 미해결 | 확정 작업 뒤에도 결과를 모르는 live 결제 > 0, 10분 지속 | 고객이 돈을 냈는지 모른다 |
| 결제 재시도 소진 | 1시간 안에 재시도 상한까지 실패한 live 인보이스 > 0 | 자동 청구가 멈췄다. 고객에게 연락해야 한다 |
| 결제 대사 불일치 | 1시간 안에 live 대사 불일치 > 0 | 우리 결제 기록과 토스페이먼츠 거래가 다르다 |
| 웹훅 전송 실패 | 15분 안에 실패로 끝난 웹훅 > 0. 재시도 상한, 마지막 시도의 선점 만료(`lease_expired`), 첫 시도에 끝나는 막힌 주소(`blocked_address`)를 센다. 도입사가 끄거나 지운 엔드포인트(`endpoint_disabled`)와 test 환경 전송은 뺀다 | 도입사가 이벤트를 받지 못했다 |
| 웹훅 발송 적체 | 보낼 때가 지난 웹훅 가운데 가장 오래 밀린 것 > 10분, 5분 지속. test 환경 전송은 뺀다 | 발송이 처리량을 따라가지 못해 도입사 통보가 늦다 |

- 예약 작업 경보는 결제, 웹훅 발송, 청구 제외 통보 작업만 본다. 인보이스 자동 확정은 위의 전용 경보로 따로 본다.
- 결제 경보는 테스트 모드 결제에서도 울리고 `mode` 라벨로 구분한다.
- 예약 작업, 인보이스 자동 확정, 결제, 웹훅 경보는 Prometheus 조회가 실패하면 업무 경보 대신 `DatasourceError` 하나로 묶여 온다. 어느 규칙인지는 `rulename` 라벨로 보고, 업무 문제가 아니라 Prometheus와 Grafana 데이터 소스를 확인한다.

- 임계값과 수신처를 바꾸려면 UI가 아니라 `grafana/provisioning/alerting/alerting.yml`을 고쳐 배포한다.
- 새 경보를 만들었으면 임계값을 낮춰 한 번 발화시켜 Slack 수신을 확인하고 원복한다.
- 경보는 백엔드를 직접 긁어 보는 것이라, 그 앞의 ALB가 죽거나 인증서가 만료된 상황은 잡지 못한다.

## 문제 해결

- 서버는 부팅 때 배포까지 끝낸다. 부팅 배포의 출력은 서버의 `/var/log/cloud-init-output.log`에 남는다.
- 그 기록에 `서버 이미지에 ... 없다`가 있으면 시작 템플릿의 image_id가 미리 구운 앱 서버 이미지가 아니다. `<환경>/main.tf`의 app_fleet `ami_id`를 본다("앱 서버 이미지").
- CD가 실패하면 먼저 서버 밖에서 새로 고침과 대상 그룹 상태를 본다.

```bash
aws autoscaling describe-instance-refreshes --auto-scaling-group-name meterengine-production-app --max-records 3
aws autoscaling describe-scaling-activities --auto-scaling-group-name meterengine-production-app --max-items 5
aws elbv2 describe-target-groups --load-balancer-arn "$(aws elbv2 describe-load-balancers --names meterengine-production-alb \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)" --query 'TargetGroups[].TargetGroupArn' --output text
aws elbv2 describe-target-health --target-group-arn <대상 그룹 ARN>
```

- 서버 안에서는 root로 아래를 본다.

```bash
# 지금 뭐가 도는지
docker compose -p meterengine-prod ps

# 로그 (컨테이너당 30MB까지만 보관한다)
docker compose -p meterengine-prod logs -f backend frontend

# 이 서버가 띄운 커밋과 버전 파라미터
git -C /opt/meterengine rev-parse HEAD
. /etc/meterengine/instance.env && aws ssm get-parameter --name "$APP_VERSION_PARAMETER" --query Parameter.Value --output text

# 서버 안에서 직접 두드리기. ALB는 /v1/* 밖의 백엔드 경로를 보내지 않아 /actuator/*는 인터넷에서 닿지 않는다
curl -s http://127.0.0.1:8080/actuator/health
curl -s http://127.0.0.1:3000/health

# 10초 CPU 지표
systemctl status meterengine-cpu-metric.service
journalctl -u meterengine-cpu-metric.service --since -10min
```

- compose 명령을 파일 경로로 직접 부를 때는 `--env-file`이 반드시 필요하다.

```bash
docker compose --env-file /etc/meterengine/prod.env -f /opt/meterengine/deploy/compose.prod.yml ps
```

- 인증서는 ACM이 DNS 검증으로 발급하고 갱신한다. 검증 레코드는 Terraform이 만든다.
- 배포가 백엔드 기동 실패로 끝나면 `deploy.sh` 로그의 `DB:` 줄에서 어느 DB에 붙었는지 확인한다.
