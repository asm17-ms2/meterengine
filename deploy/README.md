# 배포

운영 환경 구성과 배포 절차다. 실행 대상은 AWS의 EC2 한 대이고, 여기 있는 파일들이 그 서버에서 무엇이 도는지를 정한다.

## 구성

```
인터넷 --443--> [Caddy] --/v1/*----> [backend:8080] --> RDS PostgreSQL
                   |     \--그 외--> [frontend:3000]
              인증서 자동 발급              |
                                (서버사이드로 backend:8080 직접 호출)

[Prometheus] --scrape--> backend:8080/actuator/prometheus, node-exporter:9100
     ^
[Grafana:3001(127.0.0.1)] --경보--> Slack        (아래 "모니터링" 참조)
```

- 도메인: https://meterengine.com (Route 53 호스티드 존, A 레코드가 EC2 탄력적 IP를 가리킨다).
  `www.meterengine.com`도 같은 IP를 가리키고 Caddy가 정본 주소로 301 리다이렉트한다
- 서버: EC2 `meterengine-server` (t4g.medium, arm64, Amazon Linux 2023, ap-northeast-2)
- 이미지: ECR `meterengine-backend`, `meterengine-frontend`. 태그는 배포한 커밋의 git SHA
- 설정과 비밀 값: SSM Parameter Store `/meterengine/prod/*`
- 인터넷에 열린 포트는 80과 443뿐이다. SSH는 없다(접속은 SSM, 아래 참조)

프론트엔드가 백엔드를 부르는 경로는 브라우저가 아니라 프론트엔드 서버다(서버 컴포넌트). 그래서 컨테이너 네트워크 안의 `http://backend:8080`으로 가고, 공개 도메인을 거치지 않는다.

## 파일

| 파일 | 내용 |
| --- | --- |
| `compose.prod.yml` | 운영 스택 정의. Caddy, backend, frontend와 모니터링 셋(Prometheus, node exporter, Grafana) |
| `caddy/Caddyfile` | 경로 분배와 HTTPS. `/v1/*`는 백엔드, 나머지는 프론트엔드. 디렉터리째 마운트하는 이유는 `compose.prod.yml` 주석에 있다 |
| `deploy.sh` | 배포 절차 전체. 서버에서 root로 돈다 |
| `prometheus/` | Prometheus 수집 대상 정의 (MS2-168) |
| `grafana/` | Grafana 데이터 소스, 대시보드, 경보의 정본. UI에서 고친 것은 재배포 때 파일 내용으로 돌아간다 |

루트의 `docker-compose.yml`은 로컬 개발용이고 이것과 무관하다.

## 서버 1회 셋업

새 서버를 만들었을 때만 한다. SSM 세션으로 붙어 root로 실행한다.

```bash
sudo -i

dnf install -y docker git
systemctl enable --now docker

# compose 플러그인이 패키지로 딸려 오지 않으면 직접 받는다. 이 서버는 ARM이라 aarch64다
docker compose version 2>/dev/null || {
	mkdir -p /usr/libexec/docker/cli-plugins
	curl -fsSL https://github.com/docker/compose/releases/download/v5.5.0/docker-compose-linux-aarch64 \
		-o /usr/libexec/docker/cli-plugins/docker-compose
	chmod +x /usr/libexec/docker/cli-plugins/docker-compose
}

git clone https://github.com/asm17-ms2/meterengine.git /opt/meterengine
mkdir -p /etc/meterengine && chmod 700 /etc/meterengine
```

`/etc/meterengine/prod.env`는 만들지 않는다. 배포할 때마다 `deploy.sh`가 Parameter Store 값으로 새로 쓴다.

## 배포

**main에 머지되면 자동으로 배포된다.** `.github/workflows/cd.yml`이 이미지를 굽고 ECR에 올린 뒤,
SSM으로 서버의 배포 스크립트를 부른다. 따로 할 일은 없다.

```
main 머지 -> [build (backend) | build (frontend)]  ubuntu-24.04-arm 러너에서 병렬
          -> ECR push (태그 = 커밋 SHA)
          -> deploy: SSM SendCommand -> 서버에서 deploy.sh
          -> 외부에서 https://meterengine.com/usage 200 확인
```

ARM 러너를 쓰는 이유는 배포 대상 EC2가 t4g(arm64)여서다. x86에서 구운 이미지는 서버에서 실행되지
않는다. 퍼블릭 저장소라 이 러너는 무료다.

AWS 자격은 OIDC로 받는다. 저장소 secrets에 장기 액세스 키가 없다. 다만 IAM 신뢰 정책이
`refs/heads/main` 한정이라 **다른 브랜치에서는 배포 워크플로가 AssumeRole에서 막힌다.**
PR 단계에서 CD를 시험할 수 없다는 뜻이기도 하다.

배포가 실패하면 Actions 로그에 서버 출력이 그대로 찍힌다. SSM 응답은 2500자에서 잘리므로,
거기서 원인이 안 보이면 서버에 붙어서 컨테이너 로그를 본다(아래 "문제를 볼 때").

### 손으로 배포하기

```bash
/opt/meterengine/deploy/deploy.sh <git-sha>
```

스크립트가 하는 일은 순서대로 이렇다.

1. `/opt/meterengine`을 그 커밋으로 맞춘다. compose 파일과 Caddyfile이 이미지와 같은 커밋을 쓰게 된다
2. Parameter Store `/meterengine/prod/*`를 읽어 `/etc/meterengine/prod.env`를 새로 만든다(권한 600)
3. ECR에 로그인하고 그 SHA 태그의 이미지를 받는다
4. `docker compose up -d` 후 Caddy 설정을 reload 한다. Caddyfile은 bind mount라 내용이 바뀌어도
   compose가 컨테이너를 다시 만들지 않아, 이 단계가 없으면 Caddyfile만 고친 배포가 반영되지 않는다
5. 백엔드 `/actuator/health`가 UP인지, 프론트 `/usage`가 응답하는지 최대 90초 기다린다. 실패하면 컨테이너 로그를 찍고 0이 아닌 코드로 끝난다
6. 오래된 이미지를 정리한다(EBS가 20GB뿐이다)

서버 바깥에서 한 방으로 실행하려면 SSM을 쓴다. 세션에 붙지 않아도 되고 실행 기록이 남는다.

```bash
aws ssm send-command \
  --instance-ids i-0f47bb1f028cd29a9 \
  --document-name AWS-RunShellScript \
  --parameters 'commands=["/opt/meterengine/deploy/deploy.sh <git-sha>"]' \
  --query 'Command.CommandId' --output text
```

## 롤백

이전 커밋으로 되돌린다. 이미지 태그가 곧 커밋 SHA라, 롤백은 "이전 SHA로 다시 배포"와 같은 말이다.
되돌릴 커밋의 이미지는 ECR에 이미 있으므로 빌드가 필요 없다.

1. GitHub > Actions > **CD** > Run workflow
2. **Use workflow from은 main으로 둔다** (다른 브랜치를 고르면 AssumeRole이 막힌다)
3. `image_tag`에 되돌릴 커밋 SHA 40자를 넣고 실행

빌드 job이 건너뛰어지고 배포만 다시 돈다. 서버의 레포도 그 커밋으로 맞춰지므로 compose 파일과
Caddyfile까지 그 시점 상태로 돌아간다.

Actions에 들어갈 수 없는 상황이면 서버에서 직접 같은 일을 할 수 있다.

```bash
aws ssm send-command \
  --instance-ids i-0f47bb1f028cd29a9 \
  --document-name AWS-RunShellScript \
  --parameters 'commands=["cd /opt/meterengine && git fetch origin && git checkout --force <SHA> && ./deploy/deploy.sh <SHA>"]'
```

되돌릴 SHA는 ECR에서 확인할 수 있다.

```bash
aws ecr describe-images --repository-name meterengine-backend \
  --query 'reverse(sort_by(imageDetails,&imagePushedAt))[].[imageTags[0],imagePushedAt]' --output text
```

DB 마이그레이션은 되돌아가지 않는다. Flyway는 앞으로만 간다. 스키마를 바꾼 배포를 롤백해야 하면
옛 코드가 새 스키마에서 도는 상황이 되므로, 그 경우는 롤백보다 고쳐서 다시 배포하는 편이 안전하다.

## 서버 접속 (SSM)

SSH 키도 22번 포트도 없다. 서버 안의 SSM 에이전트가 AWS로 걸어 둔 연결을 타고 들어가는 방식이라, 인바운드를 열지 않고도 접속이 되고 접근 통제가 IAM으로, 기록이 CloudTrail로 간다.

- 콘솔: EC2 > 인스턴스 > `meterengine-server` > 연결 > Session Manager 탭
- 로컬 터미널: `session-manager-plugin` 설치 후
  ```bash
  aws ssm start-session --target i-0f47bb1f028cd29a9
  ```
- 서버 포트를 노트북으로 끌어오기(디버깅용):
  ```bash
  aws ssm start-session --target i-0f47bb1f028cd29a9 \
    --document-name AWS-StartPortForwardingSession \
    --parameters '{"portNumber":["3000"],"localPortNumber":["3000"]}'
  ```

## Parameter Store

전부 `/meterengine/prod/` 아래에 있다. 파라미터 이름이 환경변수 이름이 된다(`db-host` -> `DB_HOST`).

| 파라미터 | 타입 | 쓰이는 곳 |
| --- | --- | --- |
| `db-host` | String | 백엔드 datasource URL |
| `db-port` | String | 같음 |
| `db-name` | String | 같음 |
| `db-username` | String | 백엔드 datasource |
| `db-password` | SecureString | 같음. 배포 때 KMS로 복호화한다 |
| `organization-id` | String | 프론트가 조회할 도입사 |
| `organization-name` | String | 프론트 상단 바에 표시할 이름 |
| `grafana-admin-password` | SecureString | Grafana admin 로그인 (MS2-168) |
| `slack-webhook-url` | SecureString | 경보가 갈 Slack incoming webhook (MS2-168) |
| `tosspayments-secret-key` | SecureString | 백엔드가 토스페이먼츠 API를 부를 때 쓰는 Basic 인증 키 |
| `tosspayments-client-key` | String | 브라우저에서 토스페이먼츠 SDK를 초기화할 때 쓴다. 아직 읽는 곳이 없다 |

값을 고쳤으면 재배포해야 반영된다(같은 SHA로 `deploy.sh`를 다시 돌리면 된다).

**아래 파라미터들은 각 구성이 main에 머지되기 전에 만들어야 한다.** compose가 값이 없으면
뜨지 않게 막고 있어서(`:?`), 없는 채로 머지되면 그 즉시 CD 배포가 실패한다.

```bash
aws ssm put-parameter --name /meterengine/prod/grafana-admin-password --type SecureString --value '<비밀번호>'
aws ssm put-parameter --name /meterengine/prod/slack-webhook-url --type SecureString --value '<webhook URL>'
aws ssm put-parameter --name /meterengine/prod/tosspayments-secret-key --type SecureString --value '<시크릿 키>'
aws ssm put-parameter --name /meterengine/prod/tosspayments-client-key --type String --value '<클라이언트 키>'
```

값에 개행이 섞이면 `deploy.sh`가 배포를 멈춘다. 콘솔에서 복사할 때 줄바꿈이 딸려 오지 않았는지 본다.

## 토스페이먼츠

결제는 토스페이먼츠 **테스트 상점**으로 붙는다. 아직 전자결제 계약 전이라 실제 돈은 움직이지 않는다.
개발자센터에 회원가입하면 사업자등록번호 없이 테스트 상점이 생기고, 테스트 결제내역과 웹훅까지 쓸 수 있다.

레포 밖에 있어서 코드만 봐서는 알 수 없는 상점 설정은 아래와 같다. 바꾸면 배포된 백엔드의 동작이 바뀐다.

| 설정 | 값 | 어디서 바꾸나 |
| --- | --- | --- |
| 연동 키 종류 | **API 개별 연동 키** (`test_ck_` / `test_sk_`) | 개발자센터 > API 키 |
| API 버전 | `2024-06-01` | 개발자센터 > API 키 |
| 시크릿 키 | Parameter Store `tosspayments-secret-key` | 위 `put-parameter` |
| 클라이언트 키 | Parameter Store `tosspayments-client-key` | 위 `put-parameter` |

두 키는 **같은 상점에서 함께 받은 한 쌍이어야 한다.** 짝이 맞지 않으면 API가 `UNAUTHORIZED_KEY`로 거절한다.

클라이언트 키는 브라우저에 노출되는 값이라 SecureString이 아니다. 지금은 파라미터만 있고 읽는 곳이 없다.
`deploy.sh`가 경로 아래를 통째로 읽으므로 `TOSSPAYMENTS_CLIENT_KEY`로 `prod.env`에는 들어가지만,
`compose.prod.yml`이 어느 컨테이너에도 넘기지 않는다. 프론트에 붙이는 것은 아직 안 했는데, 이 레포의
프론트 설정(`frontend/src/lib/config.ts`)이 전부 `server-only`라서 브라우저로 값을 내보내는 방법을
그때 처음 정해야 하기 때문이다. 여기서 환경변수 이름을 미리 박으면 그 결정을 잘못 가둔다.

**연동 키 종류를 주문서형/결제창형(`test_gck_` / `test_gsk_`)으로 바꾸면 안 된다.** 그 키에는 자동결제가
붙어 있지 않아 빌링키 발급이 `NOT_SUPPORTED_METHOD`로 막힌다.

**API 버전을 바꾸면 응답 필드가 바뀐다.** 아직 토스페이먼츠 API를 부르는 코드가 없어서 지금 당장은
영향이 없지만, 응답을 파싱하는 코드가 붙고 나면 콘솔에서 버전만 올렸을 때 코드는 그대로인데 파싱이
조용히 어긋난다. 그때부터는 버전을 올릴 때 백엔드 매핑을 같이 고친다.

자동결제 승인 API는 `Idempotency-Key` 헤더를 받는다. 같은 키로 다시 부르면 첫 응답을 그대로 돌려주고
카드사에 다시 가지 않으며, 유효기간은 첫 요청부터 15일이다. **성공이든 실패든 응답이 캐시되므로,
실패한 결제를 재시도할 때는 반드시 새 키를 써야 한다.** 15일이 지나면 키가 만료되어 새 결제로 처리되니,
이중 결제 방어를 이 헤더에만 맡기지 않는다.

## 모니터링 (MS2-168)

Prometheus가 백엔드(`/actuator/prometheus`)와 node exporter(서버 자원)를 15초마다 긁고,
Grafana가 그것을 대시보드와 경보로 만든다. 셋 다 서비스 트래픽을 받지 않아 어느 것이 죽어도
서비스는 돈다.

지표 보관은 15일 또는 2GB 중 먼저 닿는 쪽에서 오래된 것부터 지운다. EBS가 20GB뿐이라 둘 다 건다.

### 대시보드 보기

Grafana는 인터넷에 노출하지 않는다(127.0.0.1:3001 바인딩). 인증을 최후방으로 미룬 상태(MS2-126)에서
로그인 화면 하나를 노출면으로 만들지 않으려는 결정이고, 보는 방법은 SSM 포트 포워딩이다.

```bash
aws ssm start-session --target i-0f47bb1f028cd29a9 \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3001"],"localPortNumber":["3001"]}'
```

세션을 켠 채 http://localhost:3001 접속, 계정은 `admin` / Parameter Store의 `grafana-admin-password` 값.
MeterEngine 폴더에 SLO 대시보드(가용성, p95/p99 지연, 5xx율, 서버 자원)가 있다.

### 경보

Slack으로 간다(`slack-webhook-url`). 임계값은 시작값이고, 조정은 UI가 아니라
`grafana/provisioning/alerting/alerting.yml`을 고쳐 배포한다.

| 경보 | 조건 | 뜻 |
| --- | --- | --- |
| 백엔드 헬스체크 실패 | `up{job="backend"} == 0` 3분 지속 | scrape 자체가 실패. 프로세스 다운이나 응답 불능 |
| 5xx 응답 비율 초과 | 5분 창 5xx 비율 > 5%, 5분 지속 | 요청이 실패로 새고 있다 |
| CPU 사용률 초과 | 5분 평균 > 80%, 10분 지속 | 처리량 한계이거나 폭주 프로세스 |

새 경보를 만들었으면 임계값을 일부러 낮춰 한 번 발화시켜 Slack 수신을 확인하고 원복한다.
한 번도 울려 보지 않은 경보는 없는 것과 같다.

### 여기서 하지 않는 것

외부 관점 헬스체크(blackbox exporter로 https://meterengine.com을 실제 HTTPS로 확인)와 인증서
만료 일수 감시는 이번 범위에서 뺐다. 지금의 `up` 기반 경보는 서버 안에서 백엔드를 보는 것이라,
Caddy가 죽거나 인증서가 만료된 상황은 잡지 못한다. 필요해지면 blackbox exporter 컨테이너 하나로
둘 다 해결된다(`probe_ssl_earliest_cert_expiry`).

## 문제를 볼 때

```bash
# 지금 뭐가 도는지
docker compose -p meterengine-prod ps

# 로그 (컨테이너당 30MB까지만 보관한다)
docker compose -p meterengine-prod logs -f backend
docker compose -p meterengine-prod logs --tail 100 caddy

# 서버 안에서 직접 두드리기 (이 두 포트는 127.0.0.1에만 열려 있다)
curl -s http://127.0.0.1:8080/actuator/health
curl -sI http://127.0.0.1:3000/usage
```

인증서가 발급되지 않으면 Caddy 로그를 본다. 흔한 원인은 도메인 A 레코드가 이 서버를 가리키지 않거나, 80번 포트가 막혀 있는 것이다(Let's Encrypt가 80으로 확인하러 온다). 같은 도메인에 대한 발급 시도는 주 5회로 제한되니 반복해서 재시도하지 않는다.
