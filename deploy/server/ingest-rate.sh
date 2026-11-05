#!/usr/bin/env bash
# 10초 칸마다 이 서버가 받은 수집 요청의 초당 값을 ASG 원하는 대수로 나눠 CloudWatch에 올린다.
# 확장 정책은 모든 서버 값의 합(전체 초당 요청 / 원하는 대수)을 본다. 서버별 평균은 부팅 중인 서버가 빠져 대수를 부풀린다.
# 실행 중 git checkout이 이 파일을 덮어쓰므로, 본문은 함수에 두고 맨 끝에서 main을 한 번 부른다.
set -uo pipefail

readonly PERIOD_SECONDS=10
# 칸 경계에서 바로 읽으면 앞뒤 칸이 섞여 조금 늦게 읽는다.
readonly READ_DELAY_SECONDS=2
readonly NAMESPACE=MeterEngine/Scaling
readonly SCALING_METRIC=IngestRequestsPerSecondPerDesiredInstance
readonly OBSERVED_METRIC=IngestRequestsPerSecond
readonly PROMETHEUS_URL=http://127.0.0.1:8080/actuator/prometheus
readonly IMDS=http://169.254.169.254/latest

imds() {
	local token
	token=$(curl -sf -X PUT "$IMDS/api/token" -H 'X-aws-ec2-metadata-token-ttl-seconds: 300') &&
		curl -sf -H "X-aws-ec2-metadata-token: $token" "$IMDS/meta-data/$1"
}

# 백엔드가 기동한 뒤 받은 수집 요청 수의 합. 상태 코드와 결과 라벨을 모두 더한다.
ingest_request_total() {
	curl -sf --max-time 3 "$PROMETHEUS_URL" |
		awk '/^http_server_requests_seconds_count\{/ && /method="POST"/ && /uri="\/v1\/(events|event-batches)"/ {sum += $NF} END {printf "%.0f", sum + 0}'
}

desired_capacity() {
	aws autoscaling describe-auto-scaling-groups --region "$REGION" --auto-scaling-group-names "$ASG_NAME" \
		--query 'AutoScalingGroups[0].DesiredCapacity' --output text
}

# 다음 칸 경계에서 READ_DELAY_SECONDS 지난 시각까지 자고, 방금 끝난 칸의 시작 시각(초)을 찍는다.
sleep_until_next_slot() {
	local now next
	now=$(date +%s.%N)
	next=$(awk -v t="$now" -v p="$PERIOD_SECONDS" -v d="$READ_DELAY_SECONDS" 'BEGIN {printf "%.3f", (int((t - d) / p) + 1) * p + d}')
	sleep "$(awk -v a="$next" -v b="$now" 'BEGIN {printf "%.3f", a - b}')"
	awk -v n="$next" -v p="$PERIOD_SECONDS" -v d="$READ_DELAY_SECONDS" 'BEGIN {printf "%d", n - d - p}'
}

metric_json() {
	local name="$1" timestamp="$2" value="$3"
	printf '{"MetricName":"%s","Dimensions":[{"Name":"AutoScalingGroupName","Value":"%s"}],"Timestamp":"%s","Value":%s,"Unit":"Count/Second","StorageResolution":1}' \
		"$name" "$ASG_NAME" "$timestamp" "$value"
}

# 칸 경계 시각으로 올려야 한 칸에 같은 서버 값이 두 번 들어가 합이 튀지 않는다.
put_metrics() {
	local slot="$1" capacity="$2" rate="$3" timestamp per_desired
	timestamp=$(date -u -d "@$slot" +%Y-%m-%dT%H:%M:%SZ)
	per_desired=$(awk -v r="$rate" -v c="$capacity" 'BEGIN {printf "%.1f", r / c}')
	aws cloudwatch put-metric-data --region "$REGION" --namespace "$NAMESPACE" --metric-data \
		"[$(metric_json "$OBSERVED_METRIC" "$timestamp" "$rate"),$(metric_json "$SCALING_METRIC" "$timestamp" "$per_desired")]" ||
		echo "지표 올리기 실패, 다음 칸에 다시 한다"
}

main() {
	# 옛 서버처럼 ASG 밖이면 태그가 없다. 실패로 끝내면 systemd가 계속 다시 띄운다.
	ASG_NAME=$(imds tags/instance/aws:autoscaling:groupName) || {
		echo "ASG 서버가 아니라 지표를 올리지 않는다"
		exit 0
	}
	REGION=$(imds placement/region) || exit 1
	echo "$ASG_NAME 의 $NAMESPACE 지표를 ${PERIOD_SECONDS}초 칸마다 올린다"

	local previous_total="" slot capacity rate total
	while true; do
		slot=$(sleep_until_next_slot)
		capacity=$(desired_capacity) || continue
		[ "$capacity" -gt 0 ] 2>/dev/null || continue
		rate=""
		if total=$(ingest_request_total) && [ -n "$total" ]; then
			# 백엔드가 다시 떠 카운터가 0부터 다시 세면 그 칸은 건너뛴다.
			if [ -n "$previous_total" ] && [ "$total" -ge "$previous_total" ]; then
				rate=$(awk -v a="$total" -v b="$previous_total" -v p="$PERIOD_SECONDS" 'BEGIN {printf "%.1f", (a - b) / p}')
			fi
			previous_total=$total
		fi
		# 백엔드가 아직 안 뜬 서버는 올리지 않는다. 합에는 0으로 들어가는 것과 같다.
		[ -n "$rate" ] && put_metrics "$slot" "$capacity" "$rate"
	done
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
	main "$@"
fi
