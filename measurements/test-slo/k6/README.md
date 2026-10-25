# k6

부하 발생기에서 test-slo SUT로 k6 스크립트를 돌린다.

- SUT와 부하 발생기가 켜져 있어야 한다. 켜는 법과 아래의 `$LOADGEN`은 [`../README.md`](../README.md)와 [`../loadgen/README.md`](../loadgen/README.md)의 "켜고 끄기".
- `smoke.js`는 초당 5건을 2분 보내 연결을 확인한다.

## 실행

```bash
aws ssm start-session --target $LOADGEN
```

```bash
git clone --depth 1 https://github.com/asm17-ms2/meterengine.git ~/meterengine
cd ~/meterengine/measurements/test-slo/k6
k6 run smoke.js
```

- `checks`와 `dropped_iterations` threshold가 통과해야 한다. `dropped_iterations`가 0이 아니면 k6가 정한 속도로 보내지 못한 것이니 부하 발생기의 `instance_type`을 키운다.

## 배포 중 실패 측정

- `deploy-ingest.js`는 수집 요청(`POST /v1/events`)을 초당 100건, 10분 보내고 요청마다 보낸 시각, 걸린 시간, status를 남긴다.
- 배포는 그 사이에 내 컴퓨터에서 한다. 숫자는 부하 발생기에서 [`../scripts/deploy-report.sh`](../scripts/deploy-report.sh)가 낸다: 배포 구간 길이, 구간 안 실패 건수, 가장 긴 끊김, 구간 밖 가용성(시간 기준, 요청 기준).
- 끊김은 마지막 성공부터 그 뒤 첫 성공까지다. 요청 간격 때문에 실제보다 최대 20ms(초당 100건일 때) 길게 잡힌다.
- 내 컴퓨터에서 돌리는 명령은 레포 루트에서 실행한다.

### 준비

1. SUT의 `/etc/meterengine/deploy.conf`에 `DEPLOY_TIMING=on`이 있는지 본다. 없으면 SUT에 붙어 더한다. 한 번만 하면 된다.

```bash
echo DEPLOY_TIMING=on | sudo tee -a /etc/meterengine/deploy.conf
```

2. SUT에 떠 있는 SHA와 다른 SHA를 고른다. 같은 SHA를 다시 배포하면 백엔드가 바뀌지 않는다. 고를 수 있는 SHA는 [`deploy/README.md`](../../../deploy/README.md) "되돌릴 SHA 찾기"로 본다.
   - 배포는 그 SHA의 `deploy.sh`로 돈다. `DEPLOY_TIMING`이 없는 옛 SHA는 시각을 찍지 않으니 고르지 않는다.

```bash
aws ssm start-session --target "$(terraform -chdir=measurements/test-slo/sut/terraform output -raw sut_instance_id)"
git -C /opt/meterengine rev-parse HEAD
git -C /opt/meterengine fetch --quiet origin
git -C /opt/meterengine show <고른 SHA>:deploy/deploy.sh | grep -c DEPLOY_TIMING
```

### 실행

1. 부하 발생기에서 k6를 띄운다.

```bash
cd ~/meterengine/measurements/test-slo/k6
RUN_ID=$(date +%Y%m%d-%H%M)-deploy
mkdir -p results/$RUN_ID
k6 run -e RUN_ID=$RUN_ID --console-output results/$RUN_ID/console.log deploy-ingest.js
```

2. 3분쯤 지나서 내 컴퓨터에서 배포하고, 출력에서 `측정` 두 줄을 본다.

```bash
SUT=$(terraform -chdir=measurements/test-slo/sut/terraform output -raw sut_instance_id)
SHA=<고른 SHA 40자>
CMD=$(aws ssm send-command --instance-ids $SUT --document-name AWS-RunShellScript \
  --parameters commands="cd /opt/meterengine && git fetch --quiet origin && git checkout --quiet --force $SHA && ./deploy/deploy.sh $SHA" \
  --query Command.CommandId --output text)
aws ssm wait command-executed --command-id $CMD --instance-id $SUT || true
aws ssm get-command-invocation --command-id $CMD --instance-id $SUT \
  --query StandardOutputContent --output text | grep '측정'
```

```
[deploy] 측정 시작 <시작 ms> backend=<배포 전 ID>
[deploy] 측정 끝 <끝 ms> backend=<배포 뒤 ID> 종료=0
```

3. k6가 끝나면 부하 발생기에서 두 줄의 값을 넣는다. 결과는 `results/$RUN_ID/report.txt`에도 남는다.

```bash
../scripts/deploy-report.sh results/$RUN_ID <시작 ms> <끝 ms> <배포 전 ID> <배포 뒤 ID>
```

### 결과를 쓰지 않는 경우

- 첫 줄에 "이 측정은 쓰지 않는다"가 나오면 그 이유를 고치고 다시 잰다. 못 보낸 요청이 있으면 부하 발생기의 `instance_type`을 키우고, 배포 전후 컨테이너가 같으면 다른 SHA를 고른다.
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
