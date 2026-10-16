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
