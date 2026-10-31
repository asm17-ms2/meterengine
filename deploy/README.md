# 배포

구성과 배포, 롤백, 서버 접속, 모니터링 확인 절차다.

## 구성

지금 띄운 서버는 개발 서버 하나이고 운영 서버는 없다.

```
인터넷 --443--> [Caddy] --/v1/*----> [backend-blue:8080 | backend-green:8080] --> RDS PostgreSQL
                 |   ^ \              (deploy.sh가 정한 색으로 보낸다)
                 |   |  \--그 외--> [frontend:3000]
                 |   |                     |
                 |   \------:8081---------/  (서버사이드 호출도 Caddy를 거쳐 백엔드로)
                 |
                 +--grafana., prometheus.--> [oauth2-proxy] 확인 뒤 --> [Grafana:3000], [Prometheus:9090]
                 |                           (asm17-ms2 GitHub 로그인)   (monitoring 네트워크)
            인증서 자동 발급

[Prometheus] --scrape--> backend-blue:8080, backend-green:8080, backend:8080 (블루그린 이전 이름, 첫 전환 배포용)의
             /actuator/prometheus, node-exporter:9100,
             postgres-exporter:9187 (RDS 통계 뷰), cloudwatch-exporter:9106 (RDS CloudWatch)
     ^
[Grafana:3001(127.0.0.1)] --경보--> Slack
  (deploy.sh 경보 재적재와 SSO 장애 때 비상 접근)
```

- 도메인: https://meterengine.com (Route 53 호스티드 존, A 레코드가 EC2 탄력적 IP를 가리킨다).
  - `www.meterengine.com`도 같은 IP를 가리키고 Caddy가 `https://meterengine.com`으로 301 리다이렉트한다.
- 서버: EC2 `meterengine-server` (t4g.medium, arm64, Amazon Linux 2023, ap-northeast-2).
  - 인스턴스 ID `i-0f47bb1f028cd29a9`.
- 이미지: ECR `meterengine-backend`, `meterengine-frontend`. 태그는 배포한 커밋의 git SHA 40자.
  - 레지스트리 `928618307647.dkr.ecr.ap-northeast-2.amazonaws.com`.
- 설정과 비밀값: SSM Parameter Store `/meterengine/prod/*`.
- 인터넷에 열린 포트는 80과 443이다. SSH 포트는 열려 있지 않고 접속은 SSM으로 한다.
- `3000`, `3001`은 서버의 127.0.0.1에만 열려 있다. 백엔드의 `8080`과 Caddy 내부 사이트 `8081`은 compose 네트워크 안에서만 닿는다.
- 백엔드 두 색과 프론트는 고정 대역의 `proxy` 네트워크에만 붙고, Caddy와 Prometheus는 `default`와 `proxy`에 모두 붙는다. 이유는 "공개 인증의 실제 IP" 참조.
- Grafana, oauth2-proxy, exporter들은 `monitoring` 네트워크(`172.30.1.0/24`)에만 붙어 프론트와 백엔드에서 닿지 않는다. Caddy는 이 네트워크에서 `172.30.1.10`으로 주소를 고정하고, Grafana는 이 주소에서 온 사용자 헤더만 믿는다. Prometheus는 `default`, `proxy`, `monitoring`에 모두 붙는다.
  - 서버에 `172.30.1.0/24`와 겹치는 docker 네트워크가 있으면 배포가 실패한다.
  - `monitoring` 네트워크가 생기는 첫 배포에서 Caddy, Grafana, Prometheus, exporter들이 다시 만들어진다. Caddy가 다시 만들어지는 몇 초 동안 공개 접속이 끊긴다. 이 배포에서는 배포 중 실패 측정을 돌리지 않는다.
- compose 프로젝트 이름은 `meterengine-prod`다.
- 백엔드는 `backend-blue`와 `backend-green` 두 서비스다. 배포 사이에는 한 색만 돈다. 다른 색은 보통 멈춘 컨테이너로 남지만, 첫 전환 배포 직후와 실패한 배포가 지운 뒤에는 없다.
- 마지막으로 성공한 배포의 색과 SHA는 `/etc/meterengine/deploy-state`에 `LIVE_COLOR`, `LIVE_SHA`로 남는다.
- Caddy가 보낼 백엔드 목록은 `deploy.sh`가 배포마다 `deploy/caddy/upstreams.caddy`(git이 무시하는 파일)에 쓰고 Caddyfile이 그 파일을 import한다. 이 파일이 없으면 Caddy가 설정을 읽지 못한다.

| 파일 | 내용 |
| --- | --- |
| `compose.prod.yml` | 배포 스택 정의. Caddy, backend-blue, backend-green, frontend, Prometheus, node exporter, postgres exporter, cloudwatch exporter, Grafana, oauth2-proxy |
| `caddy/Caddyfile` | 경로 분배와 HTTPS. `/v1/*`는 `upstreams.caddy`의 백엔드 중 헬스체크를 통과한 쪽, 나머지는 프론트엔드. `:8081`은 프론트엔드와 `deploy.sh`가 쓰는 내부 사이트 |
| `caddy/sso-sites.caddy` | `grafana.`, `prometheus.` 사이트. oauth2-proxy 확인을 거친다. `CADDY_SSO_SITES`가 `on`일 때만 Caddyfile에 들어간다 |
| `deploy.sh` | 배포 절차 전체. 서버에서 root로 돈다 |
| `prometheus/prometheus.yml` | Prometheus 수집 대상 |
| `cloudwatch/config.yml` | cloudwatch exporter가 읽을 RDS 지표 목록 |
| `grafana/provisioning/`, `grafana/dashboards/` | Grafana 데이터 소스, 대시보드, 경보의 정본 |

- 루트의 `docker-compose.yml`은 로컬 개발용이고 배포 스택과 무관하다.
- Grafana의 데이터 소스, 대시보드, 경보는 파일이 정본이다. 대시보드 파일은 Grafana가 스스로 다시 읽고, 경보 파일은 `deploy.sh`가 배포마다 다시 읽게 한다. 데이터 소스 파일은 Grafana를 다시 시작해야 반영된다.

## 사전 준비

배포를 직접 돌리거나 서버에 붙기 전에 갖춰야 하는 것이다.

- 로컬에 AWS CLI와 `session-manager-plugin`을 설치하고 `ap-northeast-2` 자격을 설정한다.
- `/meterengine/prod/*` 파라미터가 먼저 등록되어 있어야 한다.
  - **compose가 값이 없으면 기동을 막으므로, 값을 쓰는 구성이 파라미터 없이 main에 머지되면 그 즉시 CD 배포가 실패한다.** 등록 명령은 "설정값" 참조.
- Grafana, Prometheus의 GitHub 로그인에는 아래가 있어야 한다. oauth2-proxy가 든 구성을 머지하기 전에 순서대로 만든다.
  1. Route 53에 `grafana.meterengine.com`, `prometheus.meterengine.com` A 레코드. `meterengine.com`과 같은 EC2 탄력적 IP를 가리킨다. 없으면 Caddy가 두 주소의 인증서를 받지 못한다.
  2. GitHub OAuth App. 개인 계정이 아니라 조직 `asm17-ms2` 소유로 만든다. 만드는 사람은 조직의 organization owner여야 한다. 콜백 URL은 `https://grafana.meterengine.com/oauth2/callback`이다.
  3. 파라미터 `oauth2-proxy-client-id`, `oauth2-proxy-client-secret`, `oauth2-proxy-cookie-secret`. 앞의 둘은 OAuth App 화면의 값이다. 없으면 compose가 배포를 멈춘다.

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
- 개발 서버와 다른 파라미터 경로나 도메인을 쓰는 서버에만 `/etc/meterengine/deploy.conf`를 만든다. 이 파일이 없으면 `deploy.sh`는 개발 서버의 값을 쓴다.

```bash
cat > /etc/meterengine/deploy.conf <<'EOF'
PARAM_PATH=/meterengine/<이름>
SITE_DOMAIN=<도메인>
SITE_ALIASES=
EOF
```

- **적지 않은 변수는 개발 서버의 값을 쓰므로 세 줄을 다 적는다.** `PARAM_PATH`를 빠뜨리면 개발 서버의 DB에 붙는다.
- `SITE_ALIASES`에 적은 주소는 `SITE_DOMAIN`으로 리다이렉트되고, 비우면 `SITE_DOMAIN` 하나만 받는다.
- 프론트는 `https://<SITE_DOMAIN>`에서 온 공개 인보이스 조회와 결제 재시도만 받는다. compose가 이 값을 `METERENGINE_PUBLIC_ORIGIN`으로 넣는다.
- Caddy가 인증서를 받으려면 `SITE_DOMAIN`과 `SITE_ALIASES`의 주소마다 Route 53 레코드가 이 서버를 가리켜야 한다.
- 배포 중 실패를 재는 서버에만 `DEPLOY_TIMING=on`을 더한다. `deploy.sh`가 맨 앞과 맨 끝에 아래 두 줄을 더 찍는다. 재는 법은 [`measurements/test-slo/k6/README.md`](../measurements/test-slo/k6/README.md).

```
[deploy] 측정 시작 <epoch ms> backend=legacy:<ID 또는 없음>,blue:<ID 또는 없음>,green:<ID 또는 없음>
[deploy] 측정 끝 <epoch ms> backend=legacy:<ID 또는 없음>,blue:<ID 또는 없음>,green:<ID 또는 없음> 종료=<종료 코드>
```

- ID는 그 순간 실행 중인 컨테이너만 적는다. `legacy`는 블루그린 이전의 `backend` 서비스다.

- 메모리 크기가 개발 서버(4GiB)와 다른 서버는 `deploy.conf`에 백엔드 메모리 한도도 적는다.
  - `BACKEND_MEM_LIMIT`: 백엔드 컨테이너의 메모리 한도. 기본 `768m`.
  - `BACKEND_MAX_RAM_PERCENTAGE`: 그 한도 중 JVM 힙의 최대 비율(%). 기본 `50`.
- 배포 중 실패의 원인을 찾는 서버에만 `CADDY_ACCESS_LOG`를 적는다. 값은 공개 사이트 요청 로그를 보낼 Caddy `output`이고, 기본 `discard`는 남기지 않는다. 쓰는 법은 [`measurements/test-slo/k6/README.md`](../measurements/test-slo/k6/README.md).
  - `deploy.sh`가 배포마다 이 값을 `deploy/caddy/access-log.caddy`(git이 무시하는 파일)에 쓰고 Caddyfile이 그 파일을 import한다. 이 파일이 없으면 Caddy가 설정을 읽지 못한다.
  - 접속 로그와 기본 오류 로그는 카드 등록과 도입사 초대 URL의 토큰과 query를 가린다. 같은 URL이 담긴 `Referer`, `Location`도 가리며, 경로에 퍼센트 인코딩, 세미콜론, 점 세그먼트가 있으면 경로 전체를 가린다. 규칙은 Caddyfile의 `redacted_path` 한 곳에 있다. `Authorization`, `Cookie` 등 민감 헤더의 기본 마스킹은 유지한다. 로컬 검증: `python3 -m unittest discover -s deploy/tests -p test_logging.py -v` (Docker와 `caddy:2.11.7-alpine` 이미지 필요).
- `CADDY_SSO_SITES`는 `grafana.`, `prometheus.` SSO 사이트를 켜고 끈다. 적지 않으면 `PARAM_PATH`가 `/meterengine/prod`인 서버만 `on`이고, SUT처럼 다른 경로를 쓰는 서버는 설정 없이 `off`다. `deploy.conf`에 적으면 그 값을 따른다.
  - SUT에는 두 이름의 DNS 레코드가 없어, 켜면 Caddy가 받을 수 없는 인증서 발급을 계속 재시도한다. 그 실패가 운영과 함께 쓰는 `meterengine.com` 발급 한도에 잡히는지 확인하지 못했다.
  - `deploy.sh`가 배포마다 이 값으로 `deploy/caddy/sso.caddy`(git이 무시하는 파일)를 쓰고 Caddyfile이 그 파일을 import한다. `on`, `off` 말고 다른 값이면 배포가 멈춘다.
  - `off`여도 oauth2-proxy 컨테이너는 뜨므로 `oauth2-proxy-*` 파라미터는 있어야 한다.

## 배포

### 자동 배포

- main에 머지되면 `.github/workflows/cd.yml`이 배포까지 끝낸다. 따로 할 일은 없다.
  - `backend/`, `frontend/`, `deploy/`, `cd.yml` 중 하나라도 바뀐 머지만 배포한다. 문서나 demo만 바뀐 머지는 배포하지 않는다.

```
main 머지 -> build (backend) | build (frontend)   ubuntu-24.04-arm 러너에서 병렬
          -> ECR push (태그 = 커밋 SHA)
          -> deploy: SSM SendCommand -> 서버에서 deploy.sh
          -> https://meterengine.com/usage 를 리다이렉트까지 따라가 마지막 응답이 200인지 외부에서 확인
```

- **IAM 신뢰 정책이 `refs/heads/main` 한정이라 다른 브랜치에서 실행하면 AssumeRole에서 막힌다.** PR 단계에서는 CD를 시험할 수 없다.
- 같은 SHA의 이미지가 ECR에 이미 있으면 빌드를 건너뛴다.
- 배포가 실패하면 Actions 로그에 서버 출력이 찍힌다. SSM 응답은 2500자에서 잘리므로 원인이 안 보이면 서버에 붙어 로그를 본다 ("문제 해결" 참조).

### 수동 배포

- checkout 전에 잠금을 잡고 대상 커밋의 `deploy.sh`를 실행한다. 다른 배포가 돌고 있으면 checkout 전에 종료한다.

```bash
(exec 8>/etc/meterengine/deploy-command.lock && flock -n 8 && cd /opt/meterengine && git fetch --quiet origin 8>&- 9>&- && git checkout --quiet --force <git-sha> 8>&- 9>&- && ./deploy/deploy.sh <git-sha>)
```

- 두 번째 인자로 체크아웃할 git ref를 따로 줄 수 있다. 그때는 앞의 `git checkout`에도 그 ref를 준다. 주지 않으면 이미지와 같은 SHA로 맞춘다.

- 서버 밖에서 한 번에 실행한다.

```bash
aws ssm send-command \
  --instance-ids i-0f47bb1f028cd29a9 \
  --document-name AWS-RunShellScript \
  --parameters commands="exec 8>/etc/meterengine/deploy-command.lock && flock -n 8 && cd /opt/meterengine && git fetch --quiet origin 8>&- 9>&- && git checkout --quiet --force <git-sha> 8>&- 9>&- && ./deploy/deploy.sh <git-sha>" \
  --query 'Command.CommandId' --output text
```

- `deploy.sh`가 하는 일의 순서다.

1. Caddy 컨테이너 안에서 도는 색의 `/actuator/health/readiness`를 1초 간격으로 세 번까지 묻고 대상 색을 정한다.
   - 한 색만 돌면 반대 색이 대상이다. 도는 색이 UP이 아니어도 다시 만들지 않고 경고만 남긴다.
   - 두 색이 다 돌면 UP이 아닌 색이 대상이다. 둘 다 UP이면 Caddy 내부 사이트 `:8081` 응답의 `X-Upstream`이 가리키는 색을 살리고, 그 색을 알 수 없으면 기록 파일의 색(파일이 없으면 블루)을 살린다. 둘 다 UP이 아니면 블루가 대상이다.
   - 둘 다 돌지 않으면 블루가 대상이다.
2. `/opt/meterengine`을 그 커밋(두 번째 인자를 주면 그 ref)으로 맞추고, 곧바로 Caddy 요청 로그 설정 `deploy/caddy/access-log.caddy`, SSO 사이트 설정 `deploy/caddy/sso.caddy`, 백엔드 목록 `deploy/caddy/upstreams.caddy`를 쓴다.
   - 목록은 돌고 있는 반대 색과 블루그린 이전 `backend`다. 그중 UP인 색도 `backend`도 없으면 대상 색을 더한다.
3. Parameter Store `/meterengine/prod/*`(`deploy.conf`가 있으면 그 `PARAM_PATH`)를 읽어 `/etc/meterengine/prod.env`를 새로 만든다 (권한 600). 대상 색의 태그는 새 SHA, 돌고 있는 반대 색의 태그는 지금 이미지의 태그다.
4. ECR에 로그인하고 백엔드가 아닌 서비스와 대상 색의 이미지만 받은 뒤, 백엔드가 아닌 서비스를 `up -d`로 올리고(orphan은 지우지 않는다) 대상 색을 띄운다. 이어 Caddy 설정을 reload 한다.
   - 돌고 있는 반대 색은 받지도 다시 만들지도 않는다. 그 태그가 ECR에서 지워졌어도 배포가 막히지 않는다.
   - Caddyfile은 bind mount라 `deploy.sh`를 거치지 않고 compose만 올리면 반영되지 않는다. `docker compose -p meterengine-prod exec caddy caddy reload --config /etc/caddy/Caddyfile`을 직접 실행한다.
5. 대상 색의 `/actuator/health`(DB 포함)가 UP인지 최대 90초 기다리고, 그 컨테이너의 이미지가 새 SHA인지 확인한다.
6. Prometheus 설정을 다시 읽게 한다.
   - prometheus.yml도 Caddyfile과 같다. `docker compose -p meterengine-prod exec prometheus kill -HUP 1`로 다시 읽게 한다. `docker compose kill`로 보내면 Docker가 수동으로 멈춘 컨테이너로 기록해 다시 켜지 않는다.
7. 백엔드 목록을 대상 색 하나로 바꾸고 Caddy를 reload 한다. 새 요청은 대상 색으로 가고, 반대 색이 받던 요청은 마저 끝난다. 이어 내부 사이트 `:8081`의 응답 헤더 `X-Upstream`이 대상 색인지 최대 60초 확인한다.
8. 5초(Caddyfile의 `lb_try_duration`) 기다린 뒤 돌고 있던 반대 색을 `stop`으로 멈춘다. 지우지 않아 그 이미지가 정리되지 않는다. 블루그린 이전 `backend` 컨테이너가 있으면 이때 지운다.
9. 프론트 `/health`가 ok를 돌려주는지 최대 90초 기다린다.
10. 336시간보다 오래된 이미지를 정리한다.
11. Grafana 경보 설정을 다시 읽게 한다. 앱 확인은 이미 끝난 상태다. 401이면 관리자 비밀번호와 `grafana-admin-password`가 일치하는지 확인하고, 다른 오류면 `alerting.yml`과 Grafana 로그를 본다. 이 단계가 실패해도 새 백엔드는 남고 CD의 외부 확인은 실행된다.
12. 마지막으로 배포 기록 파일을 쓴다.

- 어느 단계든 실패하면 컨테이너 로그나 실패 줄을 찍고 0이 아닌 코드로 끝난다.
- 실패 정리에서는 기존 백엔드의 현재 readiness와 Caddy 경유 응답을 확인한 뒤 대상 색을 지운다. 기존 백엔드가 응답하지 않거나 복귀를 확인하지 못하면 새 색을 남기고, 새 색이 정상이면 Caddy도 새 색으로 돌린다. 8단계부터는 대상 색을 지우지 않는다.
- 대상 색은 7단계부터 요청을 받는다. 받고 있는 백엔드가 없던 배포만 2단계 목록에 대상 색이 들어가, readiness를 통과한 순간부터 받는다.
- `deploy.sh`는 한 번에 하나만 돈다. 앞 배포가 돌고 있으면(`/etc/meterengine/deploy-command.lock`) 바로 실패한다. CD가 기다리다 포기해도 서버의 앞 배포는 끝까지 돈다.

### 첫 전환 배포

블루그린 이전 구성(백엔드 서비스 이름 `backend`)인 서버에 이 구성을 처음 배포할 때다.

- `deploy.sh`는 `backend`를 둔 채 블루를 띄우고, Caddy 백엔드 목록을 `backend:8080`에서 블루로 바꿔 Caddy가 블루로 보내는 것을 확인한 뒤 `backend`를 지운다. prometheus.yml의 `backend:8080`은 이 전환을 위해 남겨 둔 것이다.
- 프론트엔드는 이 배포에서 백엔드 주소가 `http://caddy:8081`로 바뀌어 다시 만들어진다. 곧이어 Caddy가 reload 될 때까지 몇 초 동안 화면이 백엔드를 부르지 못할 수 있다. 수집 API는 영향이 없다.
- 개발 서버의 첫 전환 배포는 머지 직전부터 CD가 끝날 때까지 로컬 터미널에서 아래 루프를 돌려 200이 아닌 응답을 센다. 조회 권한이 있는 API 키를 사용한다.

```bash
URL=https://meterengine.com/v1/billable-metric-prices
API_KEY='<조회용 API 키>'
while :; do
	code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -H "Authorization: Bearer $API_KEY" "$URL")
	printf '%s %s\n' "$(date +%T)" "$code"
	sleep 0.2
done | tee first-switch.log
```

- CD가 끝나면 Ctrl-C로 멈추고 `grep -vc ' 200$' first-switch.log`로 센다. 0이 아니면 그 시각의 `deploy.sh` 로그와 Caddy 로그를 본다.

## 롤백

- 이미지 태그가 커밋 SHA라 롤백은 이전 SHA로 다시 배포하는 것이다. 그 이미지는 ECR에 이미 있어 빌드가 필요 없다.
- 롤백도 배포와 같은 블루그린 순서로 돈다. 멈춰 둔 색이 그 SHA의 이미지를 쥐고 있으면 서버에 남아 있고, 없으면 ECR에서 받는다.
- **블루그린 이전 SHA로 되돌리면 그 시점의 `deploy.sh`가 돌아 한 번 끊긴다.** 옛 스크립트가 블루와 그린을 orphan으로 지우고 `backend`를 띄운다. 그 뒤의 블루그린 배포는 첫 전환 배포처럼 다시 시작한다.
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
  --parameters commands="exec 8>/etc/meterengine/deploy-command.lock && flock -n 8 && cd /opt/meterengine && git fetch --quiet origin 8>&- 9>&- && git checkout --quiet --force <SHA> 8>&- 9>&- && ./deploy/deploy.sh <SHA>"
```

## DB 백업과 복구

- DB는 RDS `meterengine-db`다. 자동 백업이 있어 보존 기간 안의 어느 시각으로든 새 인스턴스에 복원할 수 있다. 원본은 그대로 남는다.
- **백업은 DB만 되살린다. `crypto-encryption-key`가 그 시각의 값과 다르면 복원한 토스페이먼츠 키, 빌링키, 웹훅 비밀값을 풀지 못한다.** "암호화 키 맞추기" 참조.

### 설정 확인

```bash
aws ssm get-parameter --name /meterengine/prod/db-host --query Parameter.Value --output text
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
- 먼저 main 머지를 멈추고 Actions에서 CD가 돌고 있지 않은지 본다. CD는 main HEAD를 그때의 `db-host`로 배포해서, 전환 전이면 백엔드가 원본 DB로 다시 뜨고 전환 뒤면 3에서 피한 마이그레이션이 복원본에 적용된다. 5를 마칠 때까지, 3에서 이전 SHA를 골랐다면 그 마이그레이션을 main에서 되돌릴 때까지 멈춘다.
- 원본이 살아 있는 채로 되돌린다면 1 전에 서버에 붙어 `sudo -i` 한 뒤 백엔드를 멈춘다. 그러면 빠지는 구간이 멈춘 시각에서 끝난다. 그동안 API는 응답하지 않고, 5의 배포가 다시 띄운다.

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
5. `db-host`를 복원본으로 바꾸고 3에서 정한 SHA로 다시 배포한다("롤백"의 Actions나 서버 명령). 배포 로그의 `DB:` 줄이 복원본인지 본다.
   - 복원본에 삭제 방지, 자동 확장 상한, Performance Insights를 원본과 같게 켠다. 복원으로는 켜지지 않고, 켜도 DB가 멈추지 않는다.

```bash
aws ssm put-parameter --name /meterengine/prod/db-host --type String --overwrite --value <복원본 엔드포인트>
aws rds modify-db-instance --db-instance-identifier meterengine-db-restore-<YYYYMMDD> \
  --deletion-protection --max-allocated-storage 50 \
  --enable-performance-insights --performance-insights-retention-period 7
```

- 원본 인스턴스는 바로 지우지 않는다. 복원 시각과 멈춘 시각(멈추지 못했으면 전환한 시각) 사이에 들어온 행은 원본과 복원본을 대조해 찾는다. 삭제 방지가 켜져 있어 먼저 꺼야 지워진다.

### 암호화 키 맞추기

- 키는 바꾸지 않는 것이 원칙이다. 이력은 값을 찍지 않고 버전과 시각만 본다.

```bash
aws ssm get-parameter-history --name /meterengine/prod/crypto-encryption-key \
  --query 'Parameters[].[Version,LastModifiedDate]' --output text
```

- 복원 시각에 쓰던 버전으로 되돌린다. 값을 화면에 찍지 않는다.

```bash
aws ssm put-parameter --name /meterengine/prod/crypto-encryption-key --type SecureString --overwrite \
  --value "$(aws ssm get-parameter --name /meterengine/prod/crypto-encryption-key:<버전> --with-decryption --query Parameter.Value --output text)"
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

## 공개 인증의 실제 IP

공개 Caddy는 `X-MeterEngine-Client-IP`를 접속 IP로 덮어쓴다. 내부 8081 사이트는 프론트 고정 주소(172.30.0.11)에서 온 값만 유지하고, 다른 연결이 보낸 값은 접속 IP로 덮어쓴다. 프론트 주소를 바꾸면 Caddyfile의 `FRONTEND_ADDRESS` 기본값도 같이 바꾼다. 백엔드는 `METERENGINE_AUTH_RATE_LIMIT_TRUSTED_PROXIES`에 든 직접 연결에서 온 헤더만 믿는다. 한도와 429 응답은 [백엔드 인증](../backend/README.md#공개-인증-요청-제한)을 따른다.

- 백엔드 두 색은 compose의 `proxy` 네트워크(`172.30.0.0/24`)에만 붙는다. Caddy는 `172.30.0.10`, 프론트는 `172.30.0.11`로 주소를 고정한다.
- compose가 이 값의 기본값을 고정 주소 대역 `172.30.0.8/29`로 넣어 Parameter Store 파라미터가 따로 필요 없다. 다른 컨테이너는 `172.30.0.128/25`에서 주소를 받아 이 대역에 들지 않는다.
- 주소나 대역을 바꾸면 compose의 고정 주소, `ip_range`, 이 기본값을 같이 바꾼다. 비우면 모든 사용자가 Caddy IP 하나의 한도를 나눠 쓴다. 전체 사설망을 일괄 신뢰하지 않는다.
- 서버에 `172.30.0.0/24`와 겹치는 docker 네트워크가 있으면 compose가 `proxy` 네트워크를 만들지 못해 배포가 실패한다.
- `proxy` 네트워크가 생기는 첫 배포에서 Caddy, 프론트, Prometheus가 다시 만들어진다. Caddy가 다시 만들어지는 몇 초 동안 80과 443이 닫혀 공개 접속이 끊긴다. 다시 뜬 Caddy는 `default`에도 붙어 있어 그때까지 받던 색에 계속 보낸다. 이 배포에서는 배포 중 실패 측정을 돌리지 않는다.
- `proxy` 네트워크가 없던 SHA로 롤백하면 Caddy가 `default`에만 붙어, 새 색이 뜰 때까지 `proxy`에만 있는 받던 색에 보내지 못한다.

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
| `db-exporter-password` | SecureString | postgres exporter가 RDS에 붙을 읽기 전용 사용자 `meterengine_exporter`의 비밀번호 |
| `organization-id` | String | 이 버전의 compose는 읽지 않는다. 옛 프론트가 조회하던 도입사이고, 옛 버전으로 되돌리는 배포가 요구해 남긴다 |
| `organization-name` | String | 이 버전의 compose는 읽지 않는다. 옛 프론트 상단 바의 이름이고, 옛 버전으로 되돌리는 배포가 요구해 남긴다 |
| `grafana-admin-password` | SecureString | Grafana admin 로그인 |
| `oauth2-proxy-client-id` | SecureString | oauth2-proxy GitHub 로그인. GitHub OAuth App의 Client ID |
| `oauth2-proxy-client-secret` | SecureString | oauth2-proxy GitHub 로그인. GitHub OAuth App의 Client secret |
| `oauth2-proxy-cookie-secret` | SecureString | oauth2-proxy 로그인 쿠키 암호화. 바꾸면 모두 다시 로그인한다 |
| `slack-webhook-url` | SecureString | 경보가 갈 Slack incoming webhook |
| `crypto-encryption-key` | SecureString | 백엔드가 토스페이먼츠 키, 빌링키, 웹훅 비밀값을 암호화하는 키. **값을 바꾸면 이미 저장한 암호문을 풀지 못한다** |
| `operator-email` | String | 백엔드가 기동할 때 없으면 만드는 첫 운영자의 이메일 |
| `operator-password` | SecureString | 첫 운영자 비밀번호. 값을 바꾸고 재배포하면 새 비밀번호가 된다 |
| `meterengine-auth-rate-limit-login-ip-limit`, `-login-account-limit`, `-login-window`, `-signup-ip-limit`, `-signup-account-limit`, `-signup-window` | String | 선택. 로그인과 가입 한도와 구간. 없으면 compose가 백엔드 기본값(100, 10, 5m, 20, 3, 30m)을 넣는다 |
| `frontend-api-key` | SecureString | 이 버전의 compose는 읽지 않는다. 옛 프론트가 백엔드를 부르던 API 키 원문이고, 옛 버전으로 되돌리는 배포가 요구해 남긴다 |

- 새 파라미터를 등록한다.

```bash
aws ssm put-parameter --name /meterengine/prod/<이름> --type String --value '<값>'
aws ssm put-parameter --name /meterengine/prod/<이름> --type SecureString --value '<값>'
```

- 값을 만들어야 하는 비밀값은 아래 명령으로 등록한다. 값을 화면에 찍지 않는다.

```bash
aws ssm put-parameter --name /meterengine/prod/crypto-encryption-key --type SecureString \
  --value "$(openssl rand -base64 32)"
aws ssm put-parameter --name /meterengine/prod/operator-password --type SecureString \
  --value "$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
aws ssm put-parameter --name /meterengine/prod/frontend-api-key --type SecureString \
  --value "me_live_$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
aws ssm put-parameter --name /meterengine/prod/oauth2-proxy-cookie-secret --type SecureString \
  --value "$(openssl rand -base64 32 | tr -- '+/' '-_')"
```

- 운영자로 로그인할 때 비밀번호는 Parameter Store에서 본다.

```bash
aws ssm get-parameter --name /meterengine/prod/operator-password --with-decryption --query Parameter.Value --output text
```

- test-slo와 test-storage는 각자의 `sut/terraform/parameters.tf`가 같은 이름의 파라미터를 만든다.

- 값을 고쳤으면 재배포해야 반영된다. 같은 SHA로 `deploy.sh`를 다시 돌리면 된다.
- **값에 개행이 섞이면 `deploy.sh`가 배포를 멈춘다.** 콘솔에서 복사할 때 줄바꿈이 딸려 오지 않았는지 본다.

## 모니터링

- Prometheus가 백엔드 `/actuator/prometheus`와 node exporter와 postgres exporter를 15초마다, cloudwatch exporter를 1분마다 수집하고, Grafana가 대시보드와 경보로 만든다.
- postgres exporter는 RDS에 `pg_monitor` 권한의 읽기 전용 사용자 `meterengine_exporter`로 붙는다. 새 RDS에는 이 사용자를 먼저 만든다.

```sql
CREATE USER meterengine_exporter WITH PASSWORD '<db-exporter-password>';
GRANT pg_monitor TO meterengine_exporter;
```

- cloudwatch exporter는 EC2 역할로 CloudWatch를 읽는다. 역할 정책에 `cloudwatch:GetMetricData`와 `cloudwatch:ListMetrics`가 있어야 한다.
- 지표 보관은 15일 또는 2GB 중 먼저 닿는 쪽에서 오래된 것부터 지운다.
- 모니터링 컨테이너는 서비스 트래픽을 받지 않는다.

### 대시보드 보기

- https://grafana.meterengine.com, https://prometheus.meterengine.com 에 GitHub로 로그인한다. `asm17-ms2` 조직 멤버만 들어온다.
  - 로그인은 24시간 유지된다. 조직 멤버인지는 로그인할 때만 보므로, 조직에서 뺀 사람도 그동안은 들어온다.
- Grafana의 MeterEngine 폴더에서 대시보드를 연다. MeterEngine SLO는 가용성과 응답 지연, MeterEngine 저장단은 이벤트 저장 경로의 풀, RDS, PostgreSQL 지표, MeterEngine 결제와 예약 작업은 결제, 웹훅 발송, 예약 작업 지표다.
- 처음 로그인하면 GitHub 로그인 이름으로 Grafana 사용자가 생기고 권한은 Editor다.
  - 파일로 관리하는 대시보드와 경보 규칙은 화면에서 저장되지 않는다. 바꾸려면 파일을 고쳐 배포한다.
  - 화면에서 새로 만든 대시보드는 Grafana 볼륨에만 남는다. 오래 쓸 것은 JSON으로 내보내 `grafana/dashboards/`에 넣고 PR로 올린다.

GitHub 로그인이 안 되면 서버의 127.0.0.1:3001로 들어간다.

1. 포트 포워딩 세션을 켠다.

```bash
aws ssm start-session --target i-0f47bb1f028cd29a9 \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3001"],"localPortNumber":["3001"]}'
```

2. 세션을 켠 채 http://localhost:3001 에 접속한다.
3. `admin`과 Parameter Store `grafana-admin-password` 값으로 로그인한다.

### 경보

- Slack으로 간다 (`slack-webhook-url`).
- 같은 경보의 재알림 주기는 4시간이다.

| 경보 | 조건 | 뜻 |
| --- | --- | --- |
| 백엔드 헬스체크 실패 | `max(up{job="backend"}) < 1` 1분 지속 | 어느 색도 scrape에 응답하지 않는다. 프로세스 다운이나 응답 불능 |
| 백엔드 두 색이 함께 떠 있음 | `sum(up{job="backend"}) > 1` 15분 지속 | 배포가 옛 색을 멈추기 전에 중단됐다 |
| 5xx 응답 비율 초과 | 5분 창 5xx 비율 > 5%, 5분 지속 | 요청이 실패로 새고 있다 |
| CPU 사용률 초과 | 5분 평균 > 80%, 10분 지속 | 처리량 한계이거나 폭주 프로세스 |
| 예약 작업 실패 | 15분 안에 실행이 예외로 끝나거나 대상 일부가 실패 | 작업이 일을 다 끝내지 못했다. 작업 이름별로 울린다 |
| 예약 작업 미실행 | 마지막 성공 뒤 5분(30초, 1분 주기), 30분(10분 주기), 25시간(하루 주기) | 작업이 멈췄거나 계속 실패한다. 한 번도 실행되지 않은 작업은 잡지 못한다 |
| 결제 결과 모름 미해결 | 확정 작업 뒤에도 결과를 모르는 live 결제 > 0, 10분 지속 | 고객이 돈을 냈는지 모른다 |
| 결제 재시도 소진 | 1시간 안에 재시도 상한까지 실패한 live 인보이스 > 0 | 자동 청구가 멈췄다. 고객에게 연락해야 한다 |
| 결제 대사 불일치 | 1시간 안에 live 대사 불일치 > 0 | 우리 결제 기록과 토스페이먼츠 거래가 다르다 |
| 웹훅 전송 실패 | 15분 안에 실패로 끝난 웹훅 > 0. 재시도 상한, 마지막 시도의 선점 만료(`lease_expired`), 첫 시도에 끝나는 막힌 주소(`blocked_address`)를 센다. 도입사가 끄거나 지운 엔드포인트(`endpoint_disabled`)와 test 환경 전송은 뺀다 | 도입사가 이벤트를 받지 못했다 |
| 웹훅 발송 적체 | 보낼 때가 지난 웹훅 가운데 가장 오래 밀린 것 > 10분, 5분 지속 | 발송이 처리량을 따라가지 못해 도입사 통보가 늦다 |

- 예약 작업 경보는 결제, 웹훅 발송, 청구 제외 통보 작업만 본다.
- 결제 경보는 테스트 모드 결제에서도 울리고 `mode` 라벨로 구분한다.
- 예약 작업, 결제, 웹훅 경보는 Prometheus 조회가 실패하면 업무 경보 대신 `DatasourceError` 하나로 묶여 온다. 어느 규칙인지는 `rulename` 라벨로 보고, 업무 문제가 아니라 Prometheus와 Grafana 데이터 소스를 확인한다.

- 임계값과 수신처를 바꾸려면 UI가 아니라 `grafana/provisioning/alerting/alerting.yml`을 고쳐 배포한다.
- 새 경보를 만들었으면 임계값을 낮춰 한 번 발화시켜 Slack 수신을 확인하고 원복한다.
- 지금 경보는 서버 안에서 백엔드를 보는 것이라 Caddy가 죽거나 인증서가 만료된 상황은 잡지 못한다.

## 문제 해결

```bash
# 지금 뭐가 도는지
docker compose -p meterengine-prod ps

# 로그 (컨테이너당 30MB까지만 보관한다)
docker compose -p meterengine-prod logs -f backend-blue backend-green
docker compose -p meterengine-prod logs --tail 100 caddy

# 마지막으로 성공한 배포의 색과 지금 Caddy가 보내는 백엔드 목록
cat /etc/meterengine/deploy-state
cat /opt/meterengine/deploy/caddy/upstreams.caddy

# 서버 안에서 직접 두드리기. /actuator/*는 Caddy가 노출하지 않아 인터넷에서는 404다
docker compose -p meterengine-prod exec caddy wget -qO- http://backend-blue:8080/actuator/health
docker compose -p meterengine-prod exec caddy wget -qO- http://backend-green:8080/actuator/health
curl -sI http://127.0.0.1:3000/usage
```

- 배포가 돌고 있지 않은데 블루와 그린이 함께 떠 있으면 Caddy가 보내지 않는 색을 멈춘다. 보내는 색은 `docker compose -p meterengine-prod exec caddy wget -S -qO /dev/null -T 3 http://localhost:8081/actuator/health/readiness 2>&1 | grep X-Upstream`으로 보고, 다른 색을 `docker compose -p meterengine-prod stop backend-<색>`으로 멈춘다.
  - 멈출 색이 `upstreams.caddy`에 있으면 먼저 그 색을 뺀 목록(예: `to backend-blue:8080`)을 쓰고 Caddy를 reload 하고 5초 기다린 뒤 멈춘다. 목록에 둔 채 멈추면 그 색으로 가던 요청 일부가 실패한다.

- compose 명령을 파일 경로로 직접 부를 때는 `--env-file`이 반드시 필요하다.

```bash
docker compose --env-file /etc/meterengine/prod.env -f /opt/meterengine/deploy/compose.prod.yml ps
```

- 인증서가 발급되지 않으면 Caddy 로그를 본다.
  - 도메인 A 레코드가 이 서버를 가리키는지, 80번 포트가 열려 있는지 확인한다.
  - **같은 도메인에 대한 발급 시도는 주 5회로 제한되니 반복해서 재시도하지 않는다**.
  - `caddy-data` 볼륨을 지우면 인증서를 새로 발급받는다.
- 배포가 백엔드 기동 실패로 끝나면 `deploy.sh` 로그의 `DB:` 줄에서 어느 DB에 붙었는지 확인한다.
