# 배포

운영 환경 구성과 배포 절차다. 실행 대상은 AWS의 EC2 한 대이고, 여기 있는 파일들이 그 서버에서 무엇이 도는지를 정한다.

## 구성

```
인터넷 --443--> [Caddy] --/v1/*----> [backend:8080] --> RDS PostgreSQL
                   |     \--그 외--> [frontend:3000]
              인증서 자동 발급              |
                                (서버사이드로 backend:8080 직접 호출)
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
| `compose.prod.yml` | 운영 스택 정의. Caddy, backend, frontend |
| `caddy/Caddyfile` | 경로 분배와 HTTPS. `/v1/*`는 백엔드, 나머지는 프론트엔드. 디렉터리째 마운트하는 이유는 `compose.prod.yml` 주석에 있다 |
| `compose.local-db.yml` | RDS(MS2-164)가 생길 때까지 쓰는 임시 postgres. 아래 "RDS 전환" 참조 |
| `deploy.sh` | 배포 절차 전체. 서버에서 root로 돈다 |

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

값을 고쳤으면 재배포해야 반영된다(같은 SHA로 `deploy.sh`를 다시 돌리면 된다).

## RDS 전환 (MS2-164 완료 시)

지금은 `db-host`가 `PLACEHOLDER`라 `deploy.sh`가 `compose.local-db.yml`을 함께 물려 임시 postgres 컨테이너를 띄운다. 배포 경로 전체를 RDS 없이 미리 검증하려고 둔 임시 조치다.

RDS가 생기면:

1. Parameter Store의 `db-host`, `db-username`, `db-password`를 실제 값으로 채운다
2. 지금 돌고 있는 것과 **같은 SHA로** 배포 스크립트를 다시 돌린다. 코드가 바뀌지 않았으니 이미지를 다시 구울 필요가 없다. `deploy.sh`가 `.env`를 새 파라미터 값으로 다시 쓰고, `db-host`가 `PLACEHOLDER`가 아닌 것을 보고 임시 postgres를 떼어낸다(`--remove-orphans`가 컨테이너까지 치운다). 이미지가 이미 서버에 있어 `pull`도 즉시 끝나므로 수십 초면 된다

   ```bash
   # 지금 서버에서 도는 SHA 확인
   aws ssm send-command --instance-ids i-0f47bb1f028cd29a9 --document-name AWS-RunShellScript \
     --parameters 'commands=["git -C /opt/meterengine rev-parse HEAD"]'

   # 그 SHA로 다시 배포
   aws ssm send-command --instance-ids i-0f47bb1f028cd29a9 --document-name AWS-RunShellScript \
     --parameters 'commands=["/opt/meterengine/deploy/deploy.sh <SHA>"]'
   ```

   CD를 쓰고 싶으면 Actions > CD > Run workflow에 같은 SHA를 넣어도 된다. 이미 올라간 이미지라 빌드 job이 건너뛰어지고 배포만 돈다. main에 push할 필요는 없다. 그러면 이미지를 새로 굽느라 시간만 더 든다
3. 확인이 끝나면 `compose.local-db.yml`과 `deploy.sh`의 `PLACEHOLDER` 분기를 지우고, 남은 볼륨도 지운다
   ```bash
   docker volume rm meterengine-prod_local-db-data
   ```

임시 DB의 데이터는 옮기지 않는다. 스키마는 Flyway가 RDS에 다시 만들고, 시드는 `R__seed.sql`이 다시 넣는다.

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
