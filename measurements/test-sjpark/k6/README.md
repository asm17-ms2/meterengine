# k6

부하 발생기에서 test-sjpark-m7g나 test-sjpark-t4g SUT로 k6 스크립트를 돌린다. 아래 `<사양>`에는 `m7g`나 `t4g`를 넣는다.

- SUT와 부하 발생기가 켜져 있어야 한다. 켜는 법과 아래의 `$LOADGEN`은 [`../README.md`](../README.md)와 [`../loadgen/README.md`](../loadgen/README.md)의 "켜고 끄기".
- `smoke.js`는 초당 5건을 2분 보내 연결을 확인한다.

## API 키와 대상 주소

- 모든 스크립트는 `API_KEY`와 `BASE_URL` 환경변수가 없으면 시작하지 않는다. 기본 대상이 없어 다른 환경으로 잘못 보내지 않는다.
- 대상 환경 전용 키를 쓴다. SUT 도입사의 계정으로 로그인한 세션에서 `ingest`, `read` 스코프 키를 발급한다(`POST /v1/api-keys`, 절차는 [`backend/README.md`](../../../backend/README.md#배포-환경의-운영-키-발급)). 운영자 계정은 도입사에 소속될 수 없어 발급하지 못한다. 원문은 발급 응답에만 나온다.
  - DB를 새로 만든 환경은 SUT 도입사를 가입으로 다시 만든다. 가입한 도입사는 실제 환경이 잠겨 있어 live 키 발급이 403 `live_environment_locked`이므로, [`deploy/README.md`](../../../deploy/README.md#수집-경로-예열) "수집 경로 예열" 2단계의 쿼리로 먼저 연다.
- 셸 기록에 남지 않게 아래 `read`로 입력받는다. 세션을 새로 열면 다시 입력한다.

## 실행

```bash
aws ssm start-session --target $LOADGEN
```

```bash
git clone --depth 1 https://github.com/asm17-ms2/meterengine.git ~/meterengine
cd ~/meterengine/measurements/test-sjpark/k6
read -rsp 'API_KEY: ' API_KEY
export API_KEY
export BASE_URL=https://test-sjpark-<사양>.meterengine.com
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
