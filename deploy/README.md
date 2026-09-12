# 배포

운영 환경 구성과 배포, 롤백, 서버 접속, 모니터링 확인 절차다.

## 구성

```
인터넷 --443--> [Caddy] --/v1/*----> [backend:8080] --> RDS PostgreSQL
                   |     \--그 외--> [frontend:3000]
                   |                       |
              인증서 자동 발급    (서버사이드로 backend:8080 직접 호출)

[Prometheus] --scrape--> backend:8080/actuator/prometheus, node-exporter:9100
     ^
[Grafana:3001(127.0.0.1)] --경보--> Slack
```

- 도메인: https://meterengine.com (Route 53 호스티드 존, A 레코드가 EC2 탄력적 IP를 가리킨다).
  - `www.meterengine.com`도 같은 IP를 가리키고 Caddy가 `https://meterengine.com`으로 301 리다이렉트한다.
- 서버: EC2 `meterengine-server` (t4g.medium, arm64, Amazon Linux 2023, ap-northeast-2).
  - 인스턴스 ID `i-0f47bb1f028cd29a9`.
- 이미지: ECR `meterengine-backend`, `meterengine-frontend`. 태그는 배포한 커밋의 git SHA 40자.
  - 레지스트리 `928618307647.dkr.ecr.ap-northeast-2.amazonaws.com`.
- 설정과 비밀값: SSM Parameter Store `/meterengine/prod/*`.
- 인터넷에 열린 포트는 80과 443이다. SSH 포트는 열려 있지 않고 접속은 SSM으로 한다.
- `8080`, `3000`, `3001`은 서버의 127.0.0.1에만 열려 있다.
- compose 프로젝트 이름은 `meterengine-prod`다.

| 파일 | 내용 |
| --- | --- |
| `compose.prod.yml` | 운영 스택 정의. Caddy, backend, frontend, Prometheus, node exporter, Grafana |
| `caddy/Caddyfile` | 경로 분배와 HTTPS. `/v1/*`는 백엔드, 나머지는 프론트엔드 |
| `deploy.sh` | 배포 절차 전체. 서버에서 root로 돈다 |
| `prometheus/prometheus.yml` | Prometheus 수집 대상 |
| `grafana/provisioning/`, `grafana/dashboards/` | Grafana 데이터 소스, 대시보드, 경보의 정본 |

- 루트의 `docker-compose.yml`은 로컬 개발용이고 운영 스택과 무관하다.
- Grafana와 대시보드는 파일이 정본이다. UI에서 고친 것은 재배포 때 파일 내용으로 돌아간다.

## 사전 준비

배포를 직접 돌리거나 서버에 붙기 전에 갖춰야 하는 것이다.

- 로컬에 AWS CLI와 `session-manager-plugin`을 설치하고 `ap-northeast-2` 자격을 설정한다.
- `/meterengine/prod/*` 파라미터가 먼저 등록되어 있어야 한다.
  - **compose가 값이 없으면 기동을 막으므로, 값을 쓰는 구성이 파라미터 없이 main에 머지되면 그 즉시 CD 배포가 실패한다.** 등록 명령은 "설정값" 참조.

### 서버 1회 셋업

새 서버를 만들었을 때만 한다.

1. SSM 세션으로 서버에 붙는다 ("서버 접속" 참조).
2. root로 아래를 실행한다.

```bash
sudo -i

dnf install -y docker git
systemctl enable --now docker

docker compose version 2>/dev/null || {
	mkdir -p /usr/libexec/docker/cli-plugins
	curl -fsSL https://github.com/docker/compose/releases/download/v5.5.0/docker-compose-linux-aarch64 \
		-o /usr/libexec/docker/cli-plugins/docker-compose
	chmod +x /usr/libexec/docker/cli-plugins/docker-compose
}

git clone https://github.com/asm17-ms2/meterengine.git /opt/meterengine
mkdir -p /etc/meterengine && chmod 700 /etc/meterengine
```

- 이 서버는 arm64라 compose 플러그인도 `aarch64` 파일을 받는다.
- `/etc/meterengine/prod.env`는 만들지 않는다. 배포할 때마다 `deploy.sh`가 Parameter Store 값으로 새로 쓴다.

## 배포

### 자동 배포

- main에 머지되면 `.github/workflows/cd.yml`이 배포까지 끝낸다. 따로 할 일은 없다.

```
main 머지 -> build (backend) | build (frontend)   ubuntu-24.04-arm 러너에서 병렬
          -> ECR push (태그 = 커밋 SHA)
          -> deploy: SSM SendCommand -> 서버에서 deploy.sh
          -> https://meterengine.com/usage 가 200인지 외부에서 확인
```

- **IAM 신뢰 정책이 `refs/heads/main` 한정이라 다른 브랜치에서 실행하면 AssumeRole에서 막힌다.** PR 단계에서는 CD를 시험할 수 없다.
- 같은 SHA의 이미지가 ECR에 이미 있으면 빌드를 건너뛴다.
- 배포가 실패하면 Actions 로그에 서버 출력이 찍힌다. SSM 응답은 2500자에서 잘리므로 원인이 안 보이면 서버에 붙어 로그를 본다 ("문제 해결" 참조).

### 수동 배포

- 서버 안에서 실행한다.

```bash
/opt/meterengine/deploy/deploy.sh <git-sha>
```

- 서버 밖에서 한 번에 실행한다.

```bash
aws ssm send-command \
  --instance-ids i-0f47bb1f028cd29a9 \
  --document-name AWS-RunShellScript \
  --parameters commands="cd /opt/meterengine && git fetch --quiet origin && git checkout --quiet --force <git-sha> && ./deploy/deploy.sh <git-sha>" \
  --query 'Command.CommandId' --output text
```

- `deploy.sh`가 하는 일의 순서다.

1. `/opt/meterengine`을 그 커밋으로 맞춘다.
2. Parameter Store `/meterengine/prod/*`를 읽어 `/etc/meterengine/prod.env`를 새로 만든다 (권한 600).
3. ECR에 로그인하고 그 SHA 태그의 이미지를 받는다.
4. `docker compose up -d --remove-orphans` 후 Caddy 설정을 reload 한다.
   - Caddyfile은 bind mount라 `deploy.sh`를 거치지 않고 compose만 올리면 반영되지 않는다. `docker compose -p meterengine-prod exec caddy caddy reload --config /etc/caddy/Caddyfile`을 직접 실행한다.
5. 백엔드 `/actuator/health`가 UP인지, 프론트 `/usage`가 응답하는지 최대 90초 기다린다. 실패하면 컨테이너 로그를 찍고 0이 아닌 코드로 끝난다.
6. 336시간보다 오래된 이미지를 정리한다.

## 롤백

- 이미지 태그가 커밋 SHA라 롤백은 이전 SHA로 다시 배포하는 것이다. 그 이미지는 ECR에 이미 있어 빌드가 필요 없다.
- **DB 마이그레이션은 되돌아가지 않는다. Flyway는 앞으로만 간다.** 스키마를 바꾼 배포를 되돌리면 옛 코드가 새 스키마 위에서 도는 상태가 된다.

### 되돌릴 SHA 찾기

```bash
aws ecr describe-images --repository-name meterengine-backend \
  --query 'reverse(sort_by(imageDetails,&imagePushedAt))[].[imageTags[0],imagePushedAt]' --output text
```

### Actions에서 롤백

1. GitHub > Actions > CD > Run workflow.
2. **Use workflow from은 main으로 둔다.** 다른 브랜치를 고르면 AssumeRole이 막힌다.
3. `image_tag`에 되돌릴 커밋 SHA 40자를 넣고 실행한다.

- 빌드 job은 건너뛰고 배포만 다시 돈다.
- 서버 레포도 그 커밋으로 맞춰지므로 compose 파일과 Caddyfile까지 그 시점 상태로 돌아간다.

### 서버에서 롤백

Actions에 들어갈 수 없을 때 쓴다.

```bash
aws ssm send-command \
  --instance-ids i-0f47bb1f028cd29a9 \
  --document-name AWS-RunShellScript \
  --parameters commands="cd /opt/meterengine && git fetch --quiet origin && git checkout --quiet --force <SHA> && ./deploy/deploy.sh <SHA>"
```

## 서버 접속

SSH 키와 22번 포트가 없어 SSM으로만 붙는다.

- 콘솔: EC2 > 인스턴스 > `meterengine-server` > 연결 > Session Manager 탭.
- 로컬 터미널.

```bash
aws ssm start-session --target i-0f47bb1f028cd29a9
```

- 서버 포트를 로컬로 끌어오기.

```bash
aws ssm start-session --target i-0f47bb1f028cd29a9 \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3000"],"localPortNumber":["3000"]}'
```

## 설정값

- 전부 Parameter Store `/meterengine/prod/` 아래에 있다.
- 파라미터 이름이 대문자 환경변수 이름이 된다 (`db-host` -> `DB_HOST`).

| 파라미터 | 타입 | 쓰이는 곳 |
| --- | --- | --- |
| `db-host` | String | 백엔드 datasource URL |
| `db-port` | String | 백엔드 datasource URL |
| `db-name` | String | 백엔드 datasource URL |
| `db-username` | String | 백엔드 datasource |
| `db-password` | SecureString | 백엔드 datasource. 배포 때 KMS로 복호화한다 |
| `organization-id` | String | 프론트가 조회할 도입사 |
| `organization-name` | String | 프론트 상단 바에 표시할 이름 |
| `grafana-admin-password` | SecureString | Grafana admin 로그인 |
| `slack-webhook-url` | SecureString | 경보가 갈 Slack incoming webhook |
| `tosspayments-secret-key` | SecureString | 백엔드가 토스페이먼츠 API를 부를 때 쓰는 Basic 인증 키 |
| `tosspayments-client-key` | String | 브라우저에서 토스페이먼츠 SDK 초기화용. 아직 읽는 곳이 없다 |

- 새 파라미터를 등록한다.

```bash
aws ssm put-parameter --name /meterengine/prod/<이름> --type String --value '<값>'
aws ssm put-parameter --name /meterengine/prod/<이름> --type SecureString --value '<값>'
```

- 값을 고쳤으면 재배포해야 반영된다. 같은 SHA로 `deploy.sh`를 다시 돌리면 된다.
- **값에 개행이 섞이면 `deploy.sh`가 배포를 멈춘다.** 콘솔에서 복사할 때 줄바꿈이 딸려 오지 않았는지 본다.
- `tosspayments-client-key`는 `prod.env`에는 들어가지만 `compose.prod.yml`이 어느 컨테이너에도 넘기지 않는다.

## 외부 연동

결제는 토스페이먼츠 테스트 상점으로 붙는다. 상점 설정은 레포 밖에 있고, 바꾸면 배포된 백엔드의 동작이 바뀐다.

| 설정 | 값 | 어디서 바꾸나 |
| --- | --- | --- |
| 연동 키 종류 | API 개별 연동 키 (`test_ck_` / `test_sk_`) | 개발자센터 > API 키 |
| API 버전 | `2024-06-01` | 개발자센터 > API 키 |
| 시크릿 키 | Parameter Store `tosspayments-secret-key` | "설정값"의 `put-parameter` |
| 클라이언트 키 | Parameter Store `tosspayments-client-key` | "설정값"의 `put-parameter` |

- **두 키는 같은 상점에서 함께 받은 한 쌍이어야 한다.** 짝이 맞지 않으면 API가 `UNAUTHORIZED_KEY`로 거절한다.
- **연동 키 종류를 주문서형, 결제창형(`test_gck_` / `test_gsk_`)으로 바꾸면 안 된다.** 빌링키 발급이 `NOT_SUPPORTED_METHOD`로 막힌다.
- **API 버전을 바꾸면 응답 필드가 바뀐다.** 응답을 파싱하는 코드가 붙은 뒤에는 버전을 올릴 때 백엔드 매핑을 같이 고친다.
- 클라이언트 키는 브라우저에 노출되는 값이라 SecureString이 아니다.
- 자동결제 승인 API의 `Idempotency-Key` 헤더는 첫 요청부터 15일간 첫 응답을 그대로 돌려준다.
  - **성공이든 실패든 응답이 캐시되므로, 실패한 결제를 재시도할 때는 새 키를 쓴다**.
  - 15일이 지나면 키가 만료되어 새 결제로 처리되니 이중 결제 방어를 이 헤더에만 맡기지 않는다.

## 모니터링

- Prometheus가 백엔드 `/actuator/prometheus`와 node exporter를 15초마다 수집하고, Grafana가 대시보드와 경보로 만든다.
- 지표 보관은 15일 또는 2GB 중 먼저 닿는 쪽에서 오래된 것부터 지운다.
- 모니터링 컨테이너는 서비스 트래픽을 받지 않는다.

### 대시보드 보기

Grafana는 인터넷에 노출하지 않고 127.0.0.1:3001에 바인딩되어 있다.

1. 포트 포워딩 세션을 켠다.

```bash
aws ssm start-session --target i-0f47bb1f028cd29a9 \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3001"],"localPortNumber":["3001"]}'
```

2. 세션을 켠 채 http://localhost:3001 에 접속한다.
3. `admin`과 Parameter Store `grafana-admin-password` 값으로 로그인한다.
4. MeterEngine 폴더 > MeterEngine SLO 대시보드를 연다.
   - 패널: 가용성, 백엔드 상태, 5xx 비율, 응답 지연 p95/p99, CPU 사용률, 메모리 사용률, 루트 디스크 사용률.

### 경보

- Slack으로 간다 (`slack-webhook-url`).
- 같은 경보의 재알림 주기는 4시간이다.

| 경보 | 조건 | 뜻 |
| --- | --- | --- |
| 백엔드 헬스체크 실패 | `up{job="backend"} == 0` 3분 지속 | scrape 자체가 실패. 프로세스 다운이나 응답 불능 |
| 5xx 응답 비율 초과 | 5분 창 5xx 비율 > 5%, 5분 지속 | 요청이 실패로 새고 있다 |
| CPU 사용률 초과 | 5분 평균 > 80%, 10분 지속 | 처리량 한계이거나 폭주 프로세스 |

- 임계값과 수신처를 바꾸려면 UI가 아니라 `grafana/provisioning/alerting/alerting.yml`을 고쳐 배포한다.
- 새 경보를 만들었으면 임계값을 낮춰 한 번 발화시켜 Slack 수신을 확인하고 원복한다.
- 지금 경보는 서버 안에서 백엔드를 보는 것이라 Caddy가 죽거나 인증서가 만료된 상황은 잡지 못한다.

## 문제 해결

```bash
# 지금 뭐가 도는지
docker compose -p meterengine-prod ps

# 로그 (컨테이너당 30MB까지만 보관한다)
docker compose -p meterengine-prod logs -f backend
docker compose -p meterengine-prod logs --tail 100 caddy

# 서버 안에서 직접 두드리기. /actuator/*는 Caddy가 노출하지 않아 인터넷에서는 404다
curl -s http://127.0.0.1:8080/actuator/health
curl -sI http://127.0.0.1:3000/usage
```

- compose 명령을 파일 경로로 직접 부를 때는 `--env-file`이 반드시 필요하다.

```bash
docker compose --env-file /etc/meterengine/prod.env -f /opt/meterengine/deploy/compose.prod.yml ps
```

- 인증서가 발급되지 않으면 Caddy 로그를 본다.
  - 도메인 A 레코드가 이 서버를 가리키는지, 80번 포트가 열려 있는지 확인한다.
  - **같은 도메인에 대한 발급 시도는 주 5회로 제한되니 반복해서 재시도하지 않는다**.
  - `caddy-data` 볼륨을 지우면 인증서를 새로 발급받는다.
- 배포가 백엔드 기동 실패로 끝나면 `deploy.sh` 로그의 `DB:` 줄에서 어느 DB에 붙었는지 확인한다.
