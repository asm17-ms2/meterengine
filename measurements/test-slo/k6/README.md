# k6

부하 발생기에서 test-slo SUT로 k6 스크립트를 돌린다.

- SUT와 부하 발생기가 켜져 있어야 한다. 켜는 법과 아래의 `$LOADGEN`은 [`../README.md`](../README.md)와 [`../loadgen/README.md`](../loadgen/README.md)의 "켜고 끄기".
- `smoke.js`는 초당 5건을 2분 보내 연결을 확인한다.

## API 키

- 모든 스크립트는 `API_KEY` 환경변수가 없으면 시작하지 않는다.
- test-slo 전용 키를 쓴다. 운영자로 로그인한 세션에서 SUT 도입사에 `ingest`, `read` 스코프 키를 발급한다(`POST /v1/api-keys`, 절차는 [`backend/README.md`](../../../backend/README.md#배포-환경의-운영-키-발급)). 원문은 발급 응답에만 나온다.
- 셸 기록에 남지 않게 아래 `read`로 입력받는다. 세션을 새로 열면 다시 입력한다.

## 실행

```bash
aws ssm start-session --target $LOADGEN
```

```bash
git clone --depth 1 https://github.com/asm17-ms2/meterengine.git ~/meterengine
cd ~/meterengine/measurements/test-slo/k6
read -rsp 'API_KEY: ' API_KEY
export API_KEY
k6 run smoke.js
```

- `checks`와 `dropped_iterations` threshold가 통과해야 한다. `dropped_iterations`가 0이 아니면 k6가 정한 속도로 보내지 못한 것이니 부하 발생기의 `instance_type`을 키운다.

## 배포 중 실패 측정

- `deploy-ingest.js`는 수집 요청(`POST /v1/events`)을 초당 100건, 10분 보내고 요청마다 순번, 보낸 시각, 걸린 시간, status를 남긴다. 순번은 `X-Request-Seq` 헤더로도 보내 Caddy 요청 로그와 맞대어 본다.
- 배포는 그 사이에 내 컴퓨터에서 한다. 숫자는 부하 발생기에서 [`../scripts/deploy-report.sh`](../scripts/deploy-report.sh)가 낸다: 배포 구간 길이, 구간 안 실패 건수, 가장 긴 끊김, 구간 밖 가용성(시간 기준, 요청 기준), 구간 안과 밖의 p99, 판정.
- 끊김은 마지막 성공부터 그 뒤 첫 성공까지다. 요청 간격 때문에 실제보다 최대 20ms(초당 100건일 때) 길게 잡힌다.
- 내 컴퓨터에서 돌리는 명령은 레포 루트에서 실행한다.
- 판정은 다음을 모두 지켜야 통과다: 배포 구간 요청 실패 0건, 배포 구간에 걸친 끊김 0개, 배포 구간 p99(nearest-rank)가 구간 밖 p99 + 1000ms 이하.
- 블루그린 배포는 첫 전환 배포를 한 번 재어 따로 기록하고, 이어서 세 번 잰다. 세 번 동안 색은 블루에서 그린, 그린에서 블루, 블루에서 그린으로 저절로 바뀐다.

### 준비

1. SUT에 붙어 `/etc/meterengine/deploy.conf`에 측정용 값을 더한다. 첫 전환 배포 전에 한 번만 한다.

```bash
aws ssm start-session --target "$(terraform -chdir=measurements/test-slo/sut/terraform output -raw sut_instance_id)"
```

```bash
sudo tee -a /etc/meterengine/deploy.conf <<'EOF'
DEPLOY_TIMING=on
CADDY_ACCESS_LOG="file /data/access.log"
EOF
```

- `CADDY_ACCESS_LOG`는 공개 사이트의 요청 로그를 Caddy 데이터 볼륨에 남긴다. 컨테이너 로그는 10MB 세 개에서 잘려 10분치를 담지 못한다. 파일은 Caddy 기본값대로 100MiB마다 gzip으로 묶이고 10개까지 남는다.
- 이 값은 다음 배포부터 Caddy reload로 반영되고 Caddy 컨테이너는 다시 만들어지지 않는다. 세 번을 다 잰 뒤에는 `CADDY_ACCESS_LOG` 줄을 지워 끈다.
- 백엔드 메모리 한도는 적지 않는다. 개발 서버에 나가는 기본값 그대로 잰다.

2. 배포할 이미지 SHA를 넷 고른다. 첫 전환 배포에 하나, 세 번에 하나씩이다.
   - SUT에 떠 있는 백엔드 이미지보다 새 main SHA를 시간 순서대로 고르고, 그 순서대로 배포한다.
   - k6가 키를 `Authorization: Bearer`로 보내므로 Bearer 인증이 들어간 SHA만 고른다.
   - SUT의 지금 SHA부터 마지막 SHA까지 마이그레이션이 없어야 한다. 아래 `git log`가 아무것도 찍지 않아야 한다.
   - 고를 수 있는 SHA는 [`deploy/README.md`](../../../deploy/README.md) "되돌릴 SHA 찾기"로 본다. 백엔드와 프론트엔드 이미지가 모두 ECR에 있어야 한다.

SUT에서 지금 이미지를 보고, 내 컴퓨터의 레포에서 고른 SHA를 확인한다.

```bash
sudo docker ps --filter label=com.docker.compose.project=meterengine-prod --format '{{.Names}} {{.Image}}' | grep backend
```

```bash
git log --oneline <SUT의 지금 SHA>..<마지막 SHA> -- backend/src/main/resources/db/migration
for r in meterengine-backend meterengine-frontend; do
  aws ecr describe-images --repository-name $r --image-ids imageTag=<고른 SHA> --query 'imageDetails[0].imagePushedAt' --output text
done
```

3. 블루그린이 main에 머지되기 전에는 배포 파일(`deploy.sh`, compose 파일, Caddyfile)을 측정할 브랜치에서 가져온다. 그 브랜치 끝 커밋의 SHA를 `REF`로 쓴다. 브랜치가 origin에 올라가 있어야 SUT가 받는다.

### 실행

첫 전환 배포와 세 번 모두 아래를 한 번씩 한다. 매번 다음 SHA를 쓴다.

1. 부하 발생기에서 k6를 띄운다.

```bash
cd ~/meterengine/measurements/test-slo/k6
RUN_ID=$(date +%Y%m%d-%H%M)-deploy
mkdir -p results/$RUN_ID
k6 run -e RUN_ID=$RUN_ID --console-output results/$RUN_ID/console.log deploy-ingest.js
```

2. 3분쯤 지나서 내 컴퓨터에서 배포하고, 끝날 때까지 기다린 뒤 출력에서 `측정` 두 줄을 본다.
   - `aws ssm wait command-executed`는 100초에서 끝나 배포보다 먼저 돌아오므로 상태를 직접 묻는다.

```bash
SUT=$(terraform -chdir=measurements/test-slo/sut/terraform output -raw sut_instance_id)
SHA=<고른 SHA 40자>
REF=<측정할 브랜치 끝 커밋 SHA 40자>
CMD=$(aws ssm send-command --instance-ids $SUT --document-name AWS-RunShellScript \
  --parameters commands="exec 8>/etc/meterengine/deploy-command.lock && flock -n 8 && cd /opt/meterengine && git fetch --quiet origin 8>&- 9>&- && git checkout --quiet --force $REF 8>&- 9>&- && ./deploy/deploy.sh $SHA $REF" \
  --query Command.CommandId --output text)
st=Pending
while [ "$st" = Pending ] || [ "$st" = InProgress ] || [ "$st" = Delayed ] || [ "$st" = Cancelling ]; do
  sleep 5
  st=$(aws ssm get-command-invocation --command-id $CMD --instance-id $SUT --query Status --output text) || st=Pending
done
aws ssm get-command-invocation --command-id $CMD --instance-id $SUT \
  --query StandardOutputContent --output text | grep '측정'
```

```
[deploy] 측정 시작 <시작 ms> backend=legacy:<ID 또는 없음>,blue:<ID 또는 없음>,green:<ID 또는 없음>
[deploy] 측정 끝 <끝 ms> backend=legacy:<ID 또는 없음>,blue:<ID 또는 없음>,green:<ID 또는 없음> 종료=0
```

3. k6가 끝나면 부하 발생기에서 두 줄의 값을 넣는다. backend 값은 `backend=` 뒤부터 공백 앞까지 그대로 넣는다. 결과는 `results/$RUN_ID/report.txt`에도 남는다.

```bash
../scripts/deploy-report.sh results/$RUN_ID <시작 ms> <끝 ms> <배포 전 backend 값> <배포 뒤 backend 값>
```

4. SUT에 붙어 배포 구간의 메모리를 본다. `측정` 두 줄의 시각을 넣고, 나온 값을 부하 발생기의 `results/$RUN_ID/memory.txt`에 옮겨 적는다.

```bash
START=<시작 ms>
END=<끝 ms>
P=$(sudo docker ps -q --filter label=com.docker.compose.project=meterengine-prod --filter label=com.docker.compose.service=prometheus)
T=$((END / 1000 + 15))
W=$(((END - START) / 1000 + 30))s
sudo docker exec $P promtool query instant --time=$T http://localhost:9090 "min_over_time(node_memory_MemAvailable_bytes[$W])"
sudo docker exec $P promtool query instant --time=$T http://localhost:9090 "max_over_time(node_vmstat_oom_kill[$W]) - min_over_time(node_vmstat_oom_kill[$W])"
for c in backend-blue backend-green; do
  id=$(sudo docker ps -aq --filter label=com.docker.compose.project=meterengine-prod --filter label=com.docker.compose.service=$c)
  [ -z "$id" ] || echo "$c OOMKilled=$(sudo docker inspect -f '{{.State.OOMKilled}}' $id)"
done
```

- 첫 값은 배포 구간과 앞뒤 15초 동안의 `MemAvailable` 최저값(바이트)이고, 둘째 값은 그동안 커널 OOM killer가 죽인 프로세스 수다. 둘 다 Prometheus `node` job이 node exporter에서 긁은 값이다.
- 메모리 판정은 세 번 모두 둘째 값이 0이고 두 색이 `OOMKilled=false`여야 통과다. 최저값은 기록만 한다.

5. Caddy 요청 로그를 내 컴퓨터의 `work/`로 가져온다. SUT에 붙어 로그를 묶고 HTTP로 잠깐 연다.

```bash
RUN_ID=<k6의 RUN_ID>
CADDY=$(sudo docker ps -q --filter label=com.docker.compose.project=meterengine-prod --filter label=com.docker.compose.service=caddy)
mkdir -p /tmp/results
sudo docker exec $CADDY sh -c 'cd /data && tar czf - access*.log*' > /tmp/results/$RUN_ID-access.tgz
python3 -m http.server 8000 --bind 127.0.0.1 --directory /tmp/results
```

내 컴퓨터에서 포워딩을 켜 두고 다른 터미널에서 받은 뒤, SUT의 서버를 Ctrl+C로 끈다.

```bash
aws ssm start-session --target "$(terraform -chdir=measurements/test-slo/sut/terraform output -raw sut_instance_id)" \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["8000"],"localPortNumber":["8000"]}'
RUN_ID=<k6의 RUN_ID>
curl -fo work/$RUN_ID-access.tgz http://localhost:8000/$RUN_ID-access.tgz
```

- 로그에는 앞선 실행도 쌓여 있다. 그 실행의 줄은 시각(`ts`, epoch 초)으로 고른다.
- 줄마다 `request.headers["X-Request-Seq"]`(k6 순번), `status`, `duration`(초), `upstream`(응답한 색)이 있다. 백엔드에 닿지 못한 요청에는 `upstream`이 없다.
- 로그에 없는 순번은 Caddy에도 닿지 못한 요청이다.

```bash
mkdir -p work/$RUN_ID-access && tar xzf work/$RUN_ID-access.tgz -C work/$RUN_ID-access
for f in work/$RUN_ID-access/*; do
  case $f in *.gz) gzip -dc "$f" ;; *) cat "$f" ;; esac
done | jq -c 'select(.status != 200) | {ts, seq: .request.headers["X-Request-Seq"][0], status, upstream, duration}'
```

### 판정이 실패하면

- 통과할 때까지 다시 재지 않는다. 실패한 순번을 Caddy 요청 로그와 `deploy.sh` 출력에 맞대어 원인을 찾고, 설계를 고칠지 기준을 고칠지 정한다.

### 결과를 쓰지 않는 경우

- 첫 줄에 "이 측정은 쓰지 않는다"가 나오면 그 이유를 고치고 다시 잰다.
  - 못 보낸 요청이 있으면 부하 발생기의 `instance_type`을 키운다.
  - 배포 뒤 블루와 그린이 모두 돌지 않으면 배포가 새 색을 남기지 못한 것이다. `deploy.sh` 출력을 본다.
  - 배포 뒤 백엔드 컨테이너가 모두 배포 전에도 돌던 것이면 배포가 새 색을 띄우지 않은 것이다. `deploy.sh` 출력을 본다.
  - k6의 첫 요청이 배포 시작 뒤였거나 마지막 요청이 배포 끝 전이면 배포 구간을 다 재지 못한 것이다. k6를 띄운 뒤 배포하고, 배포가 k6 실행(10분) 안에 끝나는지 본다.
- `측정 끝` 줄의 `종료`가 0이 아니면 배포가 실패한 것이라 쓰지 않는다.
- 구간 밖 가용성이 100%가 아니면 배포 말고 다른 실패가 섞인 것이다. 원인을 보고 쓸지 정한다.

## 대시보드

- `K6_WEB_DASHBOARD=true`를 붙여 돌리고, 내 컴퓨터의 다른 터미널에서 포트를 넘겨 http://localhost:5665 로 본다. 실행이 끝나면 대시보드도 닫힌다.

```bash
K6_WEB_DASHBOARD=true k6 run smoke.js
```

```bash
aws ssm start-session --target $LOADGEN \
  --document-name AWS-StartPortForwardingSession \
  --parameters 'portNumber=["5665"],localPortNumber=["5665"]'
```

## 시계

- 부하 발생기의 요청 시각과 SUT의 로그 시각을 맞대어 본다. 두 서버 모두 Amazon Time Sync(169.254.169.123)에 맞춘다.
- 2026-10-16에 잰 차이는 약 9µs다. 두 서버의 최대 오차(root delay의 절반 + root dispersion)를 더해도 약 0.3ms다. 두 서버에서 `chronyc tracking`으로 다시 잰다.
