# 배포

배포 대상은 `staging`, `production`, `test-slo`, `test-storage` 중 하나를 명시한다. 서버와 DB가 manifest와 다르면 checkout, 비밀값 조회, 컨테이너 변경 전에 거절한다.

## 구성

`deploy/environments/*.json`이 환경별 대상과 활성 서비스를 정한다. production은 기존 서버와 `meterengine-prod` 볼륨을 보존한다. staging은 승인된 Terraform apply 출력과 실제 EC2/RDS를 대조해 manifest에 고정한다.

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

[Prometheus] --scrape--> backend-blue:8080, backend-green:8080의
             /actuator/prometheus, caddy:2020 (Caddy 지표), node-exporter:9100, cadvisor:8080 (컨테이너),
             postgres-exporter:9187 (RDS 통계 뷰), cloudwatch-exporter:9106 (RDS CloudWatch),
             grafana:3000 (경보 평가와 알림 전송)
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
- production의 목표 설정 경로는 `/meterengine/production/*`이다. 기존 `/meterengine/prod/*`는 전환 원본과 롤백용으로 보존한다. 서버 설정과 Parameter Store 전환은 별도 승인 후 진행한다.
- 인터넷에 열린 포트는 80과 443이다. SSH 포트는 열려 있지 않고 접속은 SSM으로 한다.
- `3000`, `3001`은 서버의 127.0.0.1에만 열려 있다. 백엔드의 `8080`, Caddy 내부 사이트 `8081`과 지표 사이트 `2020`은 compose 네트워크 안에서만 닿는다.
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
| `compose.prod.yml` | 배포 스택 정의. Caddy, backend-blue, backend-green, frontend, Prometheus, node exporter, cAdvisor, postgres exporter, cloudwatch exporter, Grafana, oauth2-proxy |
| `caddy/Caddyfile` | 경로 분배와 HTTPS. `/v1/*`는 `upstreams.caddy`의 백엔드 중 헬스체크를 통과한 쪽, 나머지는 프론트엔드. `:8081`은 프론트엔드와 `deploy.sh`가 쓰는 내부 사이트, `:2020`은 Prometheus가 긁는 지표 사이트 |
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
- 선택한 manifest의 `parameter_path`에 파라미터가 먼저 등록되어 있어야 한다.
  - 선택된 서비스의 필수값은 checkout과 기동 전에 검증한다.
- production SSO 활성화는 별도 승인을 받은 뒤 준비한다.
  1. `grafana.meterengine.com`, `prometheus.meterengine.com` DNS를 production EC2 주소로 연결한다.
  2. 조직 `asm17-ms2` 소유 GitHub OAuth App을 만들고 콜백을 `https://grafana.meterengine.com/oauth2/callback`으로 정한다.
  3. 아래 서비스별 SSO 파라미터를 등록한다. 실제 credential, DNS, SSM 변경과 배포는 별도 승인 대상이다.

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
- 배포 환경, 파라미터 경로, 도메인과 Compose 프로젝트는 manifest에서 읽는다. `deploy.conf`의 옛 `PARAM_PATH`, `SITE_DOMAIN`, `SITE_ALIASES`가 manifest와 다르면 배포를 거절한다.
- SUT에는 `deploy.conf`의 `DEPLOYMENT_TARGET=test-slo` 또는 `DEPLOYMENT_TARGET=test-storage`도 필요하다. 없으면 production 값으로 대신하지 않는다.
- Python 3, AWS CLI, `flock`, Docker Compose가 필요하다. IMDSv2의 계정, 리전, 인스턴스와 AWS 호출 계정, RDS 식별자와 endpoint, 평문 `db-host`를 먼저 대조한다.
- 설치된 `deploy.sh`의 `DEPLOY_CONTRACT_VERSION=1`과 `environment.py`가 있어야 새 진입점을 쓸 수 있다. 옛 이미지 SHA를 배포할 때는 환경 계약을 포함한 `SOURCE_REF`를 따로 지정한다. 기존 서버의 초기 계약 설치와 설정 전환도 서버 변경 승인 대상이다.

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
- `CADDY_SSO_SITES`는 `grafana.`, `prometheus.` SSO 사이트를 켜고 끈다. 적지 않으면 검증된 production에서 `oauth2-proxy`, `grafana`, `prometheus`가 모두 선택됐을 때만 `on`이다. staging과 SUT는 기본 `off`다. `deploy.conf`에 적으면 그 값을 따르며 `on`은 같은 서비스 선택을 요구한다.
  - SUT에는 두 이름의 DNS 레코드가 없어, 켜면 Caddy가 받을 수 없는 인증서 발급을 계속 재시도한다. 그 실패가 운영과 함께 쓰는 `meterengine.com` 발급 한도에 잡히는지 확인하지 못했다.
  - `deploy.sh`가 배포마다 이 값으로 `deploy/caddy/sso.caddy`(git이 무시하는 파일)를 쓰고 Caddyfile이 그 파일을 import한다. `on`, `off` 말고 다른 값이면 배포가 멈춘다.
  - `oauth2-proxy`가 선택됐으면 `off`여도 세 `oauth2-proxy-*` 파라미터가 필요하다. 미선택 staging은 이 값을 조회하지 않고 Compose를 해석한다.

## 배포

### staging host 설치와 배포

승인할 source commit/tree, installer와 bundle의 SHA256, Terraform target JSON을 고정한다. staging manifest의 instance ID와 DB host가 null이면 설치할 수 없다. 아래 renderer는 요청만 만들며 AWS를 호출하지 않는다.

```bash
python3 deploy/install_host_request.py bootstrap \
  --source-sha <source-sha> --source-tree <source-tree> --public-ref <published-ref> \
  --installer-sha256 <installer-sha256> --bundle-sha256 <bundle-sha256> \
  --target-file <approved-target.json> > <request.json>
```

요청의 정확한 bytes와 대상 인스턴스를 검토하고 승인받은 뒤 기존 `aws ssm send-command --cli-input-json file://<request.json>` 경로로 실행한다. 승인 payload가 신뢰 시작점이다. 공개 pinned fetch에서 commit/tree, installer, 정해진 `git -c tar.umask=0022 archive --format=tar` bundle과 target hash를 대조하며, 기본 clone의 installer는 실행하지 않는다. fetch 실패 시 자격이나 다른 저장소로 우회하지 않는다.

installer의 직접 호출 형태는 다음과 같다. bootstrap이 검증한 파일과 pin만 전달하며 bundle 옆의 private bare `source.git`도 보존한다. 이 sidecar가 없거나 다른 commit/tree를 갖거나 외부경로 링크를 포함하면 설치를 거절한다.

```bash
bash <verified-install-host.sh> staging --source-sha <source-sha> --source-tree <source-tree> \
  --bundle <bundle.tar> --bundle-sha256 <bundle-sha256> --target-file <approved-target.json>
```

bundle의 명시 경로, 일반 파일, mode, 중복을 검사하고 private Git의 commit/tree와 archive bytes, committed target을 대조한다. identity preflight와 FD8 아래 재확인을 통과한 뒤 `/etc/meterengine/deployer/<source-sha>`에 설치한다. CA는 기존 helper로 검증하고, 없는 CA/config만 만든다. receipt를 완성한 뒤 `deployer/current`를 원자 교체한다. 같은 SHA는 receipt와 모든 파일, mode가 같을 때만 재사용한다. 실패한 candidate와 private bootstrap source는 진단을 위해 남기며, installer는 checkout, 앱 env, Caddy와 Docker를 바꾸지 않는다.

```bash
python3 deploy/install_host_request.py deploy \
  --image-sha <image-source-sha> --source-ref <installed-source-sha> \
  --run-id manual.<execution-id> --backend-digest sha256:<backend-digest> \
  --frontend-digest sha256:<frontend-digest> > <deploy-command.sh>
```

검토하고 승인한 명령을 기존 SSM 경로로 전달한다. wrapper는 FD8을 잡은 뒤 current의 실제 버전을 고정하고, 배포, FE health와 재관측까지 잠금을 유지한다. 실행 ID는 첫 글자 영숫자, 나머지 ASCII 영숫자와 `._:-`, 최대 128자로 제한한다. 수동 ID는 GitHub run이나 정식 release PASS를 증명하지 않는다.

staging/production은 Caddy가 선택한 서비스의 정확히 한 running container와 frontend를 관측한다. container의 실제 `.Image` ID로 image를 inspect하여 해당 ECR RepoDigest와 linux/arm64를 확인한다. 관측 전후 Caddy 색, checkout commit/tree와 health가 같아야 state에 `SOURCE_REF`, `SOURCE_TREE`, `DEPLOY_RUN_ID`, 양 이미지 digest/ID를 기존 `LIVE_COLOR`, `LIVE_SHA`와 함께 원자 기록한다. 관측 실패는 기존 state를 보존한다.

### 자동 배포

- main에 머지되면 `.github/workflows/cd.yml`이 빌드와 배포를 시작한다. 현재 source 후보의 배포 호출에는 아래 제한이 있다.
  - `backend/`, `frontend/`, `deploy/`, `cd.yml` 중 하나라도 바뀐 머지만 배포한다. 문서나 demo만 바뀐 머지는 배포하지 않는다.
- `image_tag`를 비우면 선택한 main SHA로 이미지를 빌드하고 ECR에 올린다. 40자 커밋 SHA를 지정하면 빌드를 생략하고 해당 이미지를 사용한다.
- 현재 호출은 production 실행 ID와 두 이미지 digest를 전달하지 않아 서버 환경 계약에서 배포를 거절한다. Actions 수동 배포와 롤백은 환경별 CI 연결 전까지 사용할 수 없다.

```
main 머지 -> build (backend) | build (frontend) -> ECR push
          -> deploy: SSM SendCommand -> 서버 환경 계약에서 거절
image_tag 지정 수동 실행 -> build 생략 -> deploy: 서버 환경 계약에서 거절
```

- 같은 SHA의 이미지가 ECR에 이미 있으면 빌드를 건너뛴다.
- 배포가 실패하면 Actions 로그에 서버 출력이 찍힌다. SSM 응답은 2500자에서 잘리므로 원인이 안 보이면 서버에 붙어 로그를 본다 ("문제 해결" 참조).

### 수동 배포

- 진입점은 `./deploy/deploy.sh ENVIRONMENT IMAGE_SOURCE_SHA [SOURCE_REF]`다. `SOURCE_REF`를 생략하면 이미지 SHA와 같다. 소스 ref의 환경 검증기와 해당 manifest가 설치본과 같아야 checkout을 허용한다.
- staging과 production은 실행 ID와 backend/frontend SHA 태그의 ECR digest를 필수로 받고 실제 ECR 값과 대조한 뒤 `repository@digest`로 이미지를 고정한다. 이전 색의 전체 이미지 참조는 보존하고, SUT는 SHA 태그 경로를 유지한다.

```bash
export DEPLOY_RUN_ID=<실행-ID>
export BACKEND_IMAGE_DIGEST=sha256:<backend-digest>
export FRONTEND_IMAGE_DIGEST=sha256:<frontend-digest>
(exec 8>/etc/meterengine/deploy-command.lock && flock -n 8 && cd /opt/meterengine && test -r deploy/environment.py && grep -qx 'DEPLOY_CONTRACT_VERSION=1' deploy/deploy.sh && ./deploy/deploy.sh production <git-sha> <source-ref>)
```

- `SOURCE_REF`는 소스 checkout과 tree 검증에만 쓰고 이미지 태그는 첫 SHA를 쓴다. FD 8 잠금은 checkout 전부터 종료까지 유지한다. SUT 호출도 `sut-deploy.sh IMAGE_SOURCE_SHA SOURCE_REF`로 소스 ref를 명시한다.
- 관측 파일로 manifest를 미리 대조할 수 있다. 파일에는 `aws_account_id`, `aws_region`, `instance_id`, `database_identifier`, `database_host`가 있어야 한다.

```bash
python3 deploy/environment.py check --environment production --observed /tmp/observed.json
```

- `deploy.sh`가 하는 일의 순서다.

1. Caddy 컨테이너 안에서 도는 색의 `/actuator/health/readiness`를 1초 간격으로 세 번까지 묻고 대상 색을 정한다.
   - 한 색만 돌면 반대 색이 대상이다. 도는 색이 UP이 아니어도 다시 만들지 않고 경고만 남긴다.
   - 두 색이 다 돌면 UP이 아닌 색이 대상이다. 둘 다 UP이면 Caddy 내부 사이트 `:8081` 응답의 `X-Upstream`이 가리키는 색을 살리고, 그 색을 알 수 없으면 기록 파일의 색(파일이 없으면 블루)을 살린다. 둘 다 UP이 아니면 블루가 대상이다.
   - 둘 다 돌지 않으면 블루가 대상이다.
2. `/opt/meterengine`을 `SOURCE_REF`로 맞추고, 곧바로 Caddy 요청 로그 설정 `deploy/caddy/access-log.caddy`, SSO 사이트 설정 `deploy/caddy/sso.caddy`, 백엔드 목록 `deploy/caddy/upstreams.caddy`를 쓴다.
   - 목록은 돌고 있는 반대 색과 블루그린 이전 `backend`다. 그중 UP인 색도 `backend`도 없으면 대상 색을 더한다.
3. checkout 전에 한 번 읽고 검증한 파라미터 스냅샷으로 `/etc/meterengine/prod.env`를 새로 만든다 (권한 600). 새 색은 검증한 이미지 참조를 쓰고, 반대 색은 실행 중인 전체 이미지 참조를 보존한다.
4. ECR에 로그인하고 `enabled_services`에 있는 백엔드 외 서비스와 대상 색의 이미지만 받은 뒤, 활성 서비스를 `up -d --no-deps`로 올리고(orphan은 지우지 않는다) 대상 색을 띄운다. 이어 Caddy 설정을 reload 한다.
   - 돌고 있는 반대 색은 받지도 다시 만들지도 않는다. 그 태그가 ECR에서 지워졌어도 배포가 막히지 않는다.
   - Caddyfile은 bind mount라 `deploy.sh`를 거치지 않고 compose만 올리면 반영되지 않는다. `docker compose -p meterengine-prod exec caddy caddy reload --config /etc/caddy/Caddyfile`을 직접 실행한다.
5. 대상 색의 `/actuator/health`(DB 포함)가 UP인지 최대 90초 기다리고, 그 컨테이너의 이미지가 검증한 참조와 같은지 확인한다.
6. Prometheus와 CloudWatch exporter는 각각 활성일 때만 설정을 다시 읽게 한다.
   - prometheus.yml도 Caddyfile과 같다. `docker compose -p meterengine-prod exec prometheus kill -HUP 1`로 다시 읽게 한다. `docker compose kill`로 보내면 Docker가 수동으로 멈춘 컨테이너로 기록해 다시 켜지 않는다.
   - cloudwatch/config.yml도 같다. `docker compose -p meterengine-prod exec cloudwatch-exporter kill -HUP 1`로 다시 읽게 한다. 이것이 실패해도 배포는 멈추지 않는다.
7. 백엔드 목록을 대상 색 하나로 바꾸고 Caddy를 reload 한다. 새 요청은 대상 색으로 가고, 반대 색이 받던 요청은 마저 끝난다. 이어 내부 사이트 `:8081`의 응답 헤더 `X-Upstream`이 대상 색인지 최대 60초 확인한다.
8. 5초(Caddyfile의 `lb_try_duration`) 기다린 뒤 돌고 있던 반대 색을 `stop`으로 멈춘다. 지우지 않아 그 이미지가 정리되지 않는다. 블루그린 이전 `backend` 컨테이너가 있으면 이때 지운다.
9. 프론트 `/health`가 ok를 돌려주는지 최대 90초 기다린다.
10. 336시간보다 오래된 이미지를 정리한다.
11. Grafana가 활성일 때만 경보 설정을 다시 읽게 한다. 앱 확인은 이미 끝난 상태다. 401이면 관리자 비밀번호와 `grafana-admin-password`가 일치하는지 확인하고, 다른 오류면 `alerting.yml`과 Grafana 로그를 본다. 이 단계가 실패해도 새 백엔드는 남고 CD의 외부 확인은 실행된다.
12. 마지막으로 배포 기록 파일을 쓴다.

- 비활성 exporter, Prometheus, Grafana는 pull, up, 비밀값 필수 검사, health와 reload에서 제외한다. 기존 컨테이너와 관측 볼륨은 자동으로 지우지 않는다. production과 SUT는 기존 관측 서비스를 활성으로 유지한다.
- staging/production은 `compose.runtime.yml`로 앱 역할과 read-only RDS CA를 넣는다. backend의 역할별 연결과 `sslmode=verify-full`, 실제 PG 차단과 웹훅 허용 목록을 검증하기 전에는 staging 준비 완료로 보지 않는다. SUT는 기존 자격을 유지한다.
- 어느 단계든 실패하면 컨테이너 로그나 실패 줄을 찍고 0이 아닌 코드로 끝난다.
- 실패 정리에서는 기존 백엔드의 현재 readiness와 Caddy 경유 응답을 확인한 뒤 대상 색을 지운다. 기존 백엔드가 응답하지 않거나 복귀를 확인하지 못하면 새 색을 남기고, 새 색이 정상이면 Caddy도 새 색으로 돌린다. 8단계부터는 대상 색을 지우지 않는다.
- 대상 색은 7단계부터 요청을 받는다. 받고 있는 백엔드가 없던 배포만 2단계 목록에 대상 색이 들어가, readiness를 통과한 순간부터 받는다.
- `deploy.sh`는 한 번에 하나만 돈다. 앞 배포가 돌고 있으면(`/etc/meterengine/deploy-command.lock`) 바로 실패한다. CD가 기다리다 포기해도 서버의 앞 배포는 끝까지 돈다.

### 첫 전환 배포

블루그린 이전 구성(백엔드 서비스 이름 `backend`)인 서버에 이 구성을 처음 배포할 때다.

- `deploy.sh`는 `backend`를 둔 채 블루를 띄우고, Caddy 백엔드 목록을 `backend:8080`에서 블루로 바꿔 Caddy가 블루로 보내는 것을 확인한 뒤 `backend`를 지운다. Prometheus는 `backend`를 긁지 않아 전환 중 그 지표는 남지 않는다. 이 배포에서 Prometheus는 정의가 바뀌어 다시 만들어지므로 블루가 뜰 때까지 백엔드 헬스체크 실패 경보가 한 번 울릴 수 있다.
- 프론트엔드는 이 배포에서 백엔드 주소가 `http://caddy:8081`로 바뀌어 다시 만들어진다. 곧이어 Caddy가 reload 될 때까지 몇 초 동안 화면이 백엔드를 부르지 못할 수 있다. 수집 API는 영향이 없다.
- 개발 서버의 첫 전환 배포는 배포 직전부터 끝날 때까지 로컬 터미널에서 아래 루프를 돌려 200이 아닌 응답을 센다. 조회 권한이 있는 API 키를 사용한다.

```bash
URL=https://meterengine.com/v1/billable-metric-prices
API_KEY='<조회용 API 키>'
while :; do
	code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -H "Authorization: Bearer $API_KEY" "$URL")
	printf '%s %s\n' "$(date +%T)" "$code"
	sleep 0.2
done | tee first-switch.log
```

- 배포가 끝나면 Ctrl-C로 멈추고 `grep -vc ' 200$' first-switch.log`로 센다. 0이 아니면 그 시각의 `deploy.sh` 로그와 Caddy 로그를 본다.

## 롤백

- 이미지 태그가 커밋 SHA라 롤백은 이전 SHA로 다시 배포하는 것이다. 그 이미지는 ECR에 이미 있어 빌드가 필요 없다.
- 롤백도 배포와 같은 블루그린 순서로 돈다. 멈춰 둔 색이 그 SHA의 이미지를 쥐고 있으면 서버에 남아 있고, 없으면 ECR에서 받는다.
- 옛 이미지로 롤백해도 `SOURCE_REF`는 현재 환경 계약을 포함해야 한다. 환경 계약이 없는 옛 소스로 checkout해 배포하는 경로는 거절한다. 실행 ID와 두 이미지 digest도 다시 지정한다.
- **DB 마이그레이션은 되돌아가지 않는다. Flyway는 앞으로만 간다.** 스키마를 바꾼 배포를 되돌리면 옛 코드가 새 스키마 위에서 도는 상태가 된다.

### 되돌릴 SHA 찾기

```bash
aws ecr describe-images --repository-name meterengine-backend \
  --query 'reverse(sort_by(imageDetails,&imagePushedAt))[].[imageTags[0],imagePushedAt]' --output text
```

### Actions에서 롤백

현재 workflow는 실행 ID와 두 digest를 전달하지 않아 배포를 거절한다. 환경별 CI 연결 전에는 아래 서버 절차를 쓰고, 이미지 SHA와 소스 ref를 따로 지정한다.

### 서버에서 롤백

서버에서 "수동 배포"의 실행 ID와 두 digest를 지정한 뒤 같은 명령으로 이전 이미지 SHA를 배포한다. 원격 호출은 이 값이 서버 명령에 전달되지 않으면 거절된다.

```bash
aws ssm send-command \
  --instance-ids i-0f47bb1f028cd29a9 \
  --document-name AWS-RunShellScript \
  --parameters commands="exec 8>/etc/meterengine/deploy-command.lock && flock -n 8 && cd /opt/meterengine && test -r deploy/environment.py && grep -qx 'DEPLOY_CONTRACT_VERSION=1' deploy/deploy.sh && ./deploy/deploy.sh production <SHA> <SOURCE_REF>"
```

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
- 먼저 Actions와 서버의 배포가 돌고 있지 않은지 확인하고 복원 중 새 배포를 시작하지 않는다. 5를 마칠 때까지, 3에서 이전 SHA를 골랐다면 피한 마이그레이션이 다시 적용되지 않는 소스를 준비할 때까지 유지한다.
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

2. 별도 승인한 owner 작업으로 복원본을 읽어 마지막 성공 Flyway 버전과 `organization`, `invoice`, `payment_attempt`의 행 수가 원하는 시각의 것인지 확인한다. runtime env에는 master 자격이 없으므로 이전 `DB_USERNAME/PASSWORD` 추출 명령을 쓰지 않는다. 새 shared/test/live schema의 복원 검증 절차를 확인하기 전에는 전환하지 않는다.

3. 배포할 SHA를 정한다. 지금 SHA를 다시 배포하면 복원본보다 새 마이그레이션이 기동 때 다시 적용된다. 그 마이그레이션 때문에 복원했다면 복원본의 마지막 버전까지만 가진 이전 SHA를 배포한다.
4. 복원 시각 뒤에 암호화 키를 바꿨으면 그 시각의 키로 되돌린다("암호화 키 맞추기").
5. 승인받은 복원본의 식별자와 endpoint로 production manifest와 설치본을 함께 맞추고, `db-host`도 같은 endpoint로 바꾼다. 3에서 정한 이미지 SHA와 현재 환경 계약이 있는 소스 ref로 다시 배포한다("롤백"의 서버 명령). 배포 로그의 `DB:` 줄이 복원본인지 본다.
   - 복원본에 삭제 방지, 자동 확장 상한, Performance Insights를 원본과 같게 켠다. 복원으로는 켜지지 않고, 켜도 DB가 멈추지 않는다.

```bash
aws ssm put-parameter --name /meterengine/production/db-host --type String --overwrite --value <복원본 엔드포인트>
aws rds modify-db-instance --db-instance-identifier meterengine-db-restore-<YYYYMMDD> \
  --deletion-protection --max-allocated-storage 50 \
  --enable-performance-insights --performance-insights-retention-period 7
```

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

staging/production의 정본은 [`runtime-parameters.v1.json`](runtime-parameters.v1.json)이다. `runtime_parameters.py`는 활성 서비스가 쓰는 정확한 이름만 읽고, 필수값 누락, 중복, 추가 응답과 DB 불일치가 있으면 env 파일을 교체하지 않는다.

배포 식별 `METERENGINE_DEPLOYMENT_ENVIRONMENT`는 검증한 manifest의 `deployment_environment`를 env 파일과 backend에 전달한다. 셸 상속값이나 SSM에 별도 값을 두지 않는다. runtime의 누락과 미지원 값은 기동을 거절하며, `local`과 `test`는 해당 로컬/시험 구성에서만 지정한다.

`METERENGINE_CUTOVER_MODE=read_only`는 초기 SELECT-only 역할만 받으며 HTTP는 health/readiness 외 경로를 차단한다. bootstrap, Ready 등록, 예약과 직접 외부 호출도 멈춘다. 별도 승인한 target 활성화 뒤에도 실행 중인 read_only 프로세스는 쓰지 않는다. 활성화한 DB에는 같은 artifact를 `active`로 새로 시작한다.

staging은 LIVE PG 자격 등록과 저장된 LIVE 자격의 모든 PG 호출을 거절한다. 웹훅은 선택 runtime 파라미터 `meterengine-staging-webhook-allowed-urls`로 전달한 `METERENGINE_STAGING_WEBHOOK_ALLOWED_URLS`의 정확한 HTTPS URI만 허용한다. 기본값과 빈 전체값은 빈 목록이다. 쉼표로 나누고 각 항목의 앞뒤 공백만 제거한다. 빈 항목, 중복, userinfo와 fragment, 잘못된 host/port는 기동을 거절한다. URI에 쉼표가 필요하면 `%2C`로 인코딩한다. scheme/ASCII host의 소문자, 생략 port 443과 빈 path `/`만 정규화하고 raw path/query의 대소문자와 percent encoding, query 없음과 빈 query를 구분한다. private network 허용은 이 목록을 우회하지 않는다.

로그인과 가입의 IP/계정 한도와 구간은 `meterengine-auth-rate-limit-` 접두사의 `login-ip-limit`, `login-account-limit`, `login-window`, `signup-ip-limit`, `signup-account-limit`, `signup-window`로 덮어쓴다. 없으면 env에 쓰지 않아 compose 기본값 100, 10, 5m, 20, 3, 30m을 유지한다. staging runtime은 선택값도 정확한 이름으로만 읽으며 Terraform은 선택 파라미터를 만들거나 관리하지 않는다.

- 앱 DB는 `meterengine_app`이며 runtime 역할은 `meterengine_shared`, `meterengine_test`, `meterengine_live`다. bootstrap owner가 이 역할을 먼저 만들어야 한다.
- runtime 경로는 `/meterengine/staging`, `/meterengine/production`이다. owner 자격 `db-migration-username/password`는 `/meterengine/<환경>-migration`에만 둔다. EC2 역할은 그 경로와 다른 환경, 파라미터 이력 조회를 명시 거절한다.
- `crypto-encryption-key`를 바꾸면 기존 암호문을 풀지 못한다. 원본 `/meterengine/prod`와 source export 자격은 보존하고 runtime에 넣지 않는다.
- exporter와 Grafana 자격은 해당 서비스가 활성일 때만 읽는다. staging은 core 서비스만 활성이다.
- SUT는 기존 `db-username/password`와 자체 Terraform 파라미터를 유지한다. 새 runtime 오버레이를 쓰지 않으며 배포 스크립트가 필수 자격을 검사한다.
- 값이 바뀌면 재배포해야 반영된다. 비밀값은 개행 없이 공급하고 명령 본문이나 로그에 넣지 않는다.

### staging Terraform

선언은 독립 EC2/RDS, SG, 역할, EIP, DNS와 SSM을 만든다. 공유 VPC와 subnet group을 재사용하되 production 자원과 SUT state는 변경하지 않는다. state key는 `deploy/staging/terraform.tfstate`다.

로컬 검사만 실행한다. `test`의 apply도 mock provider 안에서만 실행된다.

```bash
terraform -chdir=deploy/terraform/staging init -backend=false -lockfile=readonly
terraform fmt -check -recursive deploy/terraform
terraform -chdir=deploy/terraform/staging validate
terraform -chdir=deploy/terraform/staging test -test-directory=tests
python3 -m unittest discover -s deploy/tests -v
```

- Terraform 1.17.1, AWS provider 6.69.0과 random 3.9.1을 고정한다. root와 module의 비밀 입력은 ephemeral/sensitive이며 RDS `password_wo`와 SSM `value_wo`에만 전달한다. 매 실행 새 암호를 만들지 않는다.
- 실제 plan/apply에는 승인한 동일 비밀값과 명시적 version이 필요하다. saved plan에도 비밀값이 없으므로 apply 시 다시 공급한다. AWS 6.69.0의 SSM description/tier/key 변경도 비밀값 재공급과 version 증가를 동반한다. 해당 AWS update 동작은 아직 실행 검증하지 않았다.
- `TF-APPLY-STAGING`, `IAM-STAGING`, `DNS-STAGING`, 필요한 `SSM-STAGING`을 각각 승인받은 뒤 실행한다. saved plan의 자원 증감, 대상과 state key를 함께 검토한다. GitHub 환경과 실제 OIDC token 검증은 별도다.
- apply 후 `terraform output -json deployment_target`을 실제 EC2/RDS ready와 대조해 staging manifest에 고정한다.
- `prevent_destroy`, RDS 삭제 보호, 7일 백업과 최종 스냅숏을 유지한다. 자동 중지 자원은 만들지 않는다.
- staging 배포 역할의 예상 subject는 불변 저장소 ID를 포함한 `environment:staging`이다. 같은 subject의 develop/main 구분은 GitHub Environment branch 보호와 workflow 조건이 맡는다. owner 역할은 별도의 main-ref subject를 제안하며 실제 trust 적용 전 검증한다.

### production Terraform

`terraform/production`은 기존 EC2 `i-0f47bb1f028cd29a9`와 RDS `meterengine-db`를 `deploy/production/terraform.tfstate`로 편입하고 `m7g.large`, `db.m7g.large`로 변경한다. 기존 초기 DB `meterengine`, AMI, 네트워크, profile, EIP와 DNS는 보존한다. 앱 대상 DB `meterengine_app`의 생성과 전환은 별도다.

```bash
terraform -chdir=deploy/terraform/production init -backend=false -lockfile=readonly
terraform -chdir=deploy/terraform/production validate
terraform -chdir=deploy/terraform/production test
python3 -m unittest discover -s deploy/tests -p test_terraform_plan.py -v
```

초기 편입 전에는 알려진 state의 소유권을 대조하고 주소, ID, 객체 버전과 조회 시각만 담은 승인용 audit을 만든다. Actions는 승인한 audit을 소비하고 자기 환경의 state만 읽는다. 실행마다 대상 계정, 리전, EC2/RDS 식별자와 RDS pending modification을 별도로 다시 조회한다. raw plan, JSON과 로그는 비공개 폴더에 저장한다. 읽기 plan은 `-lock=false`로 실행하며 원격 state를 쓰지 않는다.

`python3 deploy/terraform_plan.py PRIVATE_PLAN_JSON --ownership PRIVATE_AUDIT_JSON --observed PRIVATE_TARGET_JSON`은 두 import와 지정 사양, 최종 스냅숏 및 자동 백업 보존 정책만 허용한다. 추가 생성, 삭제, 교체, 암호 쓰기와 예상 밖 변경은 exit 2로 거절한다. audit JSON은 `account_id`, `region`, `bucket`, `audited_at`, `state_versions: {state_key: {version_id, etag?, last_modified?}}`, `states: {state_key: [{address, id, identifier?, resource_id?, arn?}]}`다. observed JSON은 `account_id`, `region`, `instance_id`, `database_identifier`, `database_resource_id`, `rds_pending_modifications: []`다. audit의 대상 범위와 해시를 검토하고 observed는 매번 새로 조회한다. 편입 뒤에는 해당 환경의 state가 소유권 정본이다. `--import-only`는 진단용이며 별도 무변경 apply를 요구하지 않는다.

production은 `apply_immediately=true`로 선언해 승인한 중단 시간에 RDS 사양을 반영한다. 기존 pending modification의 필드 이름 목록을 observed의 `rds_pending_modifications`에 넣고, 비어 있지 않으면 거절한다. EC2의 `user_data_replace_on_change`는 null에서 기본값 false로 정규화된다. EC2 사양 변경으로 다시 계산되는 `public_ip`, `public_dns`만 미정값으로 허용한다. EIP 연결은 별도로 확인한다.

적용 전에는 팀원 고지, 중단 시간, saved plan과 해시, 기존 대상 ID, state key와 두 import 주소를 보이고 `TF-STATE-PRODUCTION`, `TF-APPLY-PRODUCTION`을 각각 승인받는다. `prevent_destroy`, RDS 삭제 보호, 최종 스냅숏과 자동 백업 보존을 유지하며 새 암호를 공급하지 않는다. 적용 뒤 RDS pending modification, 서비스 health와 기존 대상 ID를 확인하고 plan이 무변경인지 검증한다.

### Terraform automation bootstrap

`terraform/automation`은 `meterengine-terraform-{staging,production}-{plan,apply}` 역할과 전용 managed policy를 별도 `deploy/automation/terraform.tfstate`로 관리한다. 기존 bucket, OIDC provider와 VPC는 만들거나 import하지 않는다. 아래 로컬 검사는 AWS 자격과 원격 state를 쓰지 않는다.

```bash
terraform -chdir=deploy/terraform/automation init -backend=false -lockfile=readonly
terraform -chdir=deploy/terraform/automation validate
terraform -chdir=deploy/terraform/automation test -test-directory=tests
python3 deploy/terraform/automation/tests/test_access.py
```

실제 bootstrap 전에는 다음 순서로 승인과 확인을 마친다.

1. `staging-infrastructure`, `production-infrastructure`의 native required reviewers와 main branch만 허용하는 custom deployment branch policy를 별도 승인 후 설정하고 API 응답으로 확인한다. 앱 배포용 `staging`과 구분한다. 설정 없이 Environment를 자동 생성하지 않는다.
2. 기존 AWS 주체로 정확한 역할 이름과 `meterengine-terraform-*` policy 이름의 충돌을 조회한다. 충돌하면 중단하고 소유권을 검토한다. 기존 역할을 자동 import하거나 덮어쓰지 않는다.
3. 네 역할의 정확한 trust, 출력 `policy_documents`, bootstrap key와 비공개 saved plan을 검토해 IAM/bootstrap/state 쓰기를 각각 승인받는다. `infrastructure_environments_verified=true`는 운영자의 확인 표시이며 GitHub 상태를 조회하는 검증기가 아니다.
4. 승인된 기존 로컬 AWS 주체로만 bootstrap을 실행한다. 새 access key를 만들지 않는다. 다음 명령은 승인을 받은 뒤에만 실행하며 init부터 원격 backend를 사용한다.

```bash
umask 077
bootstrap_private=$(mktemp -d)
terraform -chdir=deploy/terraform/automation init -reconfigure -lockfile=readonly
terraform -chdir=deploy/terraform/automation plan -lock=false \
  -var=infrastructure_environments_verified=true -out="$bootstrap_private/bootstrap.tfplan" \
  >"$bootstrap_private/plan.log" 2>&1
shasum -a 256 "$bootstrap_private/bootstrap.tfplan"
# 같은 saved plan의 대상과 해시를 승인받은 뒤 실행한다.
terraform -chdir=deploy/terraform/automation apply "$bootstrap_private/bootstrap.tfplan" \
  >"$bootstrap_private/apply.log" 2>&1
terraform -chdir=deploy/terraform/automation output -json role_arns
```

plan 역할은 자기 state의 현재/고정 버전을 읽고 native lock과 자기 plan prefix만 쓴다. apply 역할만 자기 state를 쓰며, 자기 target의 `diagnostics/`에는 AES256 PutObject만 추가로 허용한다. plan 원본은 SSE-S3로 저장하고 apply는 고정 VersionId로만 읽으며 plan/context를 덮어쓰지 못한다. production은 최초 소유권 audit에 필요한 bucket 전체 버전 metadata와 `deploy/ownership/production/`의 축약 JSON만 추가로 읽는다. audit의 key/VersionId/SHA256은 승인과 helper가 결합하며 IAM에 버전을 고정하지 않는다. 다른 state 본문, bootstrap state와 audit 쓰기 권한은 없으며 automation 역할/policy의 직접 변경은 명시 거절한다.

staging apply는 앱 역할의 inline policy와 trust를 관리하는 승인된 운영자다. 이 위임으로 간접 권한 상승을 시도할 수 있으므로 automation 역할의 직접 변경 거절을 완전한 격리로 보지 않는다. IAM 승인에 이 앱 역할 관리 권한도 명시한다. runtime 역할 자체에는 IAM 관리 권한을 주지 않는다.

EC2 목록과 RDS `dbi-resource-id` 필터 조회는 리전으로 제한한 metadata read다. staging SG/EIP/rule은 명시적 `Environment=staging` 태그로 제한하고 생성 중 volume/ENI에 없는 태그를 요구하지 않는다. production 쓰기는 기존 EC2의 중지/시작/속성 변경과 기존 RDS 변경뿐이며 허용 diff는 production 검증기가 추가 제한한다. Terraform 1.17.1의 기본 `env:/` workspace 목록은 거절하고 default workspace만 쓴다.

aws 6.69.0의 RDS 기본 대기는 생성 50분, 변경 80분이다. staging 생성 뒤 CA 보정은 변경 대기를 추가할 수 있어 staging apply 세션 상한은 10800초, production apply는 7200초, plan은 3600초다. workflow도 각각 같은 세션 길이를 요청하고 apply job 제한을 staging 170분, production 110분으로 둔다.

실제 역할 획득, AWS 권한 평가, token 대조와 apply는 로컬 mock 검증으로 확인되지 않는다. 권한 거절이 발생하면 새 호출과 자원을 확인해 좁은 변경을 검토하고 다시 승인받는다.

### Terraform 수동 plan과 승인

`terraform.yml`은 main에서 `staging` 또는 `production`을 선택해 실행한다. plan과 apply가 같은 source SHA, Terraform 1.17.1, lockfile과 default workspace를 사용한다. plan 원본은 `deploy/plans/{target}/{run_id}/{run_attempt}/{source_sha}/`에 저장하고 apply는 plan job이 전달한 VersionId와 SHA256으로만 복원한다.

실제 설정을 승인받을 때 다음 이름과 값을 함께 검토한다. 비밀값은 staging apply 단계에만 전달하며 version과 operator email은 plan에 저장되는 일반 입력이다.

| 위치 | 이름 | 입력 |
| --- | --- | --- |
| Repository vars | `TF_STAGING_DB_PASSWORD_VERSION` | 양의 정수 |
| Repository vars | `TF_STAGING_RUNTIME_SECRET_VERSIONS_JSON` | runtime secret key별 양의 정수 JSON |
| Repository vars | `TF_STAGING_OPERATOR_EMAIL` | 운영자 email |
| staging-infrastructure secrets | `TF_STAGING_DB_PASSWORD` | 실제 owner password |
| staging-infrastructure secrets | `TF_STAGING_RUNTIME_SECRETS_JSON` | `db-shared-password`, `db-test-password`, `db-live-password`, `crypto-encryption-key`, `operator-password`의 실제 값 JSON |
| Repository vars | `TF_PRODUCTION_OWNERSHIP_AUDIT_KEY`, `TF_PRODUCTION_OWNERSHIP_AUDIT_VERSION_ID`, `TF_PRODUCTION_OWNERSHIP_AUDIT_SHA256` | 최초 production 편입의 승인된 private audit 식별자 |

production state가 생긴 뒤에는 자기 state의 고정 버전으로 소유권을 확인한다. production에는 staging password나 dummy를 주지 않는다. missing/null/dummy apply 입력은 역할 획득 전에 거절한다.

GitHub Environment/reviewer/main 정책과 secrets, IAM bootstrap, S3 plan 저장과 실제 apply 승인을 마친 뒤에만 실행한다. production은 먼저 변경 사양, 보존 정책, 적용 시간과 예상 중단을 담은 팀 고지문을 별도 승인받아 전송한다.

```bash
gh workflow run terraform.yml --ref main -f target=staging
gh run list --workflow terraform.yml --branch main --event workflow_dispatch --limit 5
gh run view <run-id> --json status,conclusion,jobs
gh run view <run-id> --web
```

plan summary에서 대상, source SHA, plan SHA256과 고정 plan/context VersionId를 확인한 뒤 native Environment에서 승인한다. production summary는 import/사양 변경과 final snapshot 보존 정책도 표시한다. workflow는 승인을 자동 처리하지 않는다.

production은 plan과 apply에서 기존 EC2/RDS/EIP와 빈 RDS pending modifications를 조회한다. apply 시 saved plan의 before class, AMI/subnet/SG/profile과 RDS network identity가 실제 값과 같아야 한다. 차이가 있거나 state VersionId가 바뀌면 새 run에서 plan부터 다시 시작하고 다시 승인한다. native stale-state 검사는 lineage/serial 변경을 검출하지만 모든 외부 AWS 변경을 검출하지는 않는다.

실패하면 공개 고정 오류와 해당 run의 private `diagnostics/`를 확인한다. runner의 init/plan/show/apply 로그는 따로 보존하며 공개 artifact나 presigned URL로 내보내지 않는다. bucket 검증 이전 실패는 runner의 private 임시 로그만 남긴다. helper 내부의 plan 재해석이 실패하면 해당 show 원문은 보존되지 않으므로 plan 단계에서 저장한 show 로그를 함께 확인한다. 자동 replan이나 승인 없는 재시도는 하지 않는다.

### production SSO 준비

선택된 `oauth2-proxy`는 사이트 공개를 꺼도 다음 SecureString 값을 요구한다. 선택하지 않은 staging은 이 값을 조회하지 않는다.

| 파라미터 이름 | 값 |
| --- | --- |
| `oauth2-proxy-client-id` | 조직 OAuth App의 Client ID |
| `oauth2-proxy-client-secret` | 같은 App의 Client secret |
| `oauth2-proxy-cookie-secret` | 쿠키 암호화 값. 교체하면 다시 로그인해야 한다 |

검증된 production manifest의 `/meterengine/production` 경로와 읽기 권한, DNS, OAuth App 설정을 함께 검토하고 승인받은 운영 작업에서 등록한다. 쿠키 값은 URL-safe base64 32바이트 난수로 준비한다. 기존 `/prod` 값이 자동 이전된다고 가정하지 않는다.

### RDS CA와 owner 작업

CA는 공식 HTTPS truststore에서 받아 모든 인증서의 파싱과 유효성을 확인한 뒤 원자 교체한다. 별도 게시 checksum이 있다고 가정하지 않는다. 승인한 서버 변경에서 설치하고 같은 파일을 runtime에 read-only로 마운트한다.

```bash
sudo ./deploy/rds-ca.sh install /etc/meterengine/rds-ca.pem
stat /etc/meterengine/rds-ca.pem
```

실제 staging에서는 원래 RDS endpoint로 정상 연결, 잘못된 CA와 hostname의 실패를 확인해야 한다. 로컬 TLS 시험은 이 실제 연결 검증을 대신하지 않는다.

owner 작업은 manifest의 instance/endpoint와 CA를 확인하고 SSM 포트 전달을 연 뒤 별도 승인하여 실행한다. Linux를 지원하며 staging은 OrbStack Linux ARM64를 확인한 macOS에서도 실행할 수 있다. production은 Linux의 migration만 지원한다. 포트 전달 세션에 owner 자격을 보내지 않고 별도 터미널에서 유지한다. owner 명령을 실행할 터미널에서 `RDS_CA_FILE`을 승인한 영구 작업 디렉터리에 보존한 검증된 CA의 절대 경로로 지정한다.

```bash
aws ssm start-session --target <staging-instance-id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<원래-RDS-endpoint>"],"portNumber":["5432"],"localPortNumber":["15432"]}'
```

기존 네 인자는 `--migrate-only`와 같다. 첫 staging은 최종 source와 backend digest를 고정하고, 앱을 아직 기동하지 않은 실제 SSM `read_only` 상태에서 host 설치와 아래 owner 작업을 수행한다. 빈 새 DB는 같은 이미지와 포트 전달에서 bootstrap, migration, fresh activation 순서로 준비한다. 각 명령은 별도 승인한 owner 작업이며 실제 DB 초기화와 TLS 검증을 통과한 이미지가 필요하다.

```bash
RDS_CA_FILE=/absolute/path/to/approved-working-directory/rds-ca.pem
stat "$RDS_CA_FILE"
./deploy/migrate.sh staging <ECR-backend@sha256:digest> 15432 "$RDS_CA_FILE" --bootstrap-database
./deploy/migrate.sh staging <ECR-backend@sha256:digest> 15432 "$RDS_CA_FILE" --migrate-only
DB_TARGET_IDENTIFIER=<manifest-database_identifier> ./deploy/migrate.sh staging <ECR-backend@sha256:digest> 15432 "$RDS_CA_FILE" --activate-target --fresh-target
```

최종 staging source의 cutover mode는 `active`지만 실제 SSM 전환은 owner 작업 뒤다. 세 owner 작업의 성공 증거, 대상, source와 plan 해시를 확인한 뒤 별도 승인한 Terraform saved plan으로 `/meterengine/staging/meterengine-cutover-mode` String parameter의 `read_only -> active` update 한 건만 적용한다. 기존 자원 생성 승인을 재사용하지 않으며 실패하거나 다른 diff가 있으면 새 plan부터 다시 승인받는다. SSM `active`를 확인한 뒤 같은 source와 digest의 앱을 처음 기동한다.

bootstrap만 staging의 shared/test/live username/password를 추가로 읽는다. 역할은 `meterengine_shared`, `meterengine_test`, `meterengine_live`로 고정한다. migration과 activation은 연결 정보와 owner 자격만 읽으며 activation target은 manifest와 정확히 일치해야 한다. 혼합하거나 순서를 바꾼 모드는 거절한다.

자격은 권한 0600의 임시 env 파일로 전달하고 종료 시 지운다. CA read-only mount, host network, 원래 RDS hostname의 loopback 매핑과 backend의 `verify-full`을 유지한다. 전달 세션은 종료 시 해제하고 검증된 CA는 승인한 영구 작업 디렉터리에 보존한다. 로컬 runner 시험과 Mac loopback TCP 확인은 실제 SSM/RDS/TLS, 이미지의 DB 초기화 성공을 보증하지 않는다.

## 모니터링

- Prometheus가 백엔드 `/actuator/prometheus`와 Caddy `:2020/metrics`, node exporter, cAdvisor, postgres exporter, Grafana를 15초마다, cloudwatch exporter를 1분마다 수집하고, Grafana가 대시보드와 경보로 만든다.
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
- Grafana의 MeterEngine 폴더에서 대시보드를 연다.
  - MeterEngine 서비스 개요는 가용성, 요청 수, 5xx 비율, 느린 API, Caddy 요청과 업스트림 상태다.
  - MeterEngine API 상세는 uri와 method로 고른 API의 지연과 오류, 그리고 전체 요청의 인증, 호출 제한, 감사 기록 실패 지표다.
  - MeterEngine 이벤트 수집은 수집 API 요청, 이벤트 결과, 저장 실패 지표다. 맨 아래 접힌 DB 왕복 행은 단건 수집이 거치는 DB 왕복별 평균과 p99다.
  - MeterEngine 청구와 결제는 인보이스 확정, 결제, 토스페이먼츠 지표다.
  - MeterEngine 예약 작업과 웹훅 발송은 예약 작업 실행과 웹훅 전달, 적체 지표다.
  - MeterEngine 앱 자원은 JVM, 톰캣, 커넥션 풀, 서버와 컨테이너 자원, 관측 시스템 상태다.
  - MeterEngine DB는 PostgreSQL과 RDS 지표다.
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
| 웹훅 발송 적체 | 보낼 때가 지난 웹훅 가운데 가장 오래 밀린 것 > 10분, 5분 지속. test 환경 전송은 뺀다 | 발송이 처리량을 따라가지 못해 도입사 통보가 늦다 |

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
