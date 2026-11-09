#!/usr/bin/env bash
# 10초 칸마다 이 서버의 CPU 사용률을 ASG 원하는 대수로 나눠 CloudWatch에 올린다. EC2 지표는 1분이라 판단에 3분이 걸린다.
# 확장 정책은 같은 칸 값의 합(준비된 서버 CPU의 합 / 원하는 대수)을 본다. 평균은 부팅 중인 서버가 빠져 대수를 부풀린다(MS2-567 측정).
# 실행 중 git checkout이 이 파일을 덮어쓰므로, 본문은 함수에 두고 맨 끝에서 main을 한 번 부른다.
set -uo pipefail

readonly PERIOD_SECONDS=10
# 칸 경계에서 바로 읽으면 앞뒤 칸이 섞여 조금 늦게 읽는다.
readonly READ_DELAY_SECONDS=2
readonly NAMESPACE=MeterEngine/Scaling
readonly SCALING_METRIC=CPUUtilizationPerDesiredInstance
readonly OBSERVED_METRIC=CPUUtilization
# 준비된 직후 1분은 JIT 컴파일로 CPU가 튀어 평균을 올려 한 대 더 띄운다(MS2-561 측정). EC2 지표의 인스턴스 워밍업과 같은 일이다.
readonly READY_HOLD_SECONDS=60
readonly PROC_STAT="${PROC_STAT:-/proc/stat}"
readonly READINESS_URL=http://127.0.0.1:8080/actuator/health/readiness
readonly IMDS=http://169.254.169.254/latest

imds() {
	local token
	token=$(curl -sf -X PUT "$IMDS/api/token" -H 'X-aws-ec2-metadata-token-ttl-seconds: 300') &&
		curl -sf -H "X-aws-ec2-metadata-token: $token" "$IMDS/meta-data/$1"
}

# 부팅 뒤 누적 CPU 시간(바쁜 몫, 전체)을 찍는다. idle과 iowait만 쉬는 시간으로 본다. guest는 user에 이미 들어 있다.
cpu_counters() {
	awk '$1 == "cpu" {busy = $2 + $3 + $4 + $7 + $8 + $9; printf "%d %d", busy, busy + $5 + $6; found = 1; exit} END {exit !found}'
}

# 백엔드 readiness의 HTTP 코드. 연결이 안 되거나 시간이 지나면 000이다.
readiness_code() {
	curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$READINESS_URL"
}

# 다음 칸 경계에서 READ_DELAY_SECONDS 지난 시각까지 자고, 방금 끝난 칸의 시작 시각(초)을 찍는다.
sleep_until_next_slot() {
	local now next
	now=$(date +%s.%N)
	next=$(awk -v t="$now" -v p="$PERIOD_SECONDS" -v d="$READ_DELAY_SECONDS" 'BEGIN {printf "%.3f", (int((t - d) / p) + 1) * p + d}')
	sleep "$(awk -v a="$next" -v b="$now" 'BEGIN {printf "%.3f", a - b}')"
	awk -v n="$next" -v p="$PERIOD_SECONDS" -v d="$READ_DELAY_SECONDS" 'BEGIN {printf "%d", n - d - p}'
}

desired_capacity() {
	aws autoscaling describe-auto-scaling-groups --region "$REGION" --auto-scaling-group-names "$ASG_NAME" \
		--query 'AutoScalingGroups[0].DesiredCapacity' --output text
}

metric_json() {
	local name="$1" timestamp="$2" value="$3"
	printf '{"MetricName":"%s","Dimensions":[{"Name":"AutoScalingGroupName","Value":"%s"}],"Timestamp":"%s","Value":%s,"Unit":"Percent","StorageResolution":1}' \
		"$name" "$ASG_NAME" "$timestamp" "$value"
}

# 칸 경계 시각으로 올려야 한 칸에 같은 서버 값이 두 번 들어가 합이 튀지 않는다.
put_metrics() {
	local slot="$1" capacity="$2" cpu="$3" timestamp per_desired
	timestamp=$(date -u -d "@$slot" +%Y-%m-%dT%H:%M:%SZ)
	per_desired=$(awk -v c="$cpu" -v n="$capacity" 'BEGIN {printf "%.1f", c / n}')
	aws cloudwatch put-metric-data --region "$REGION" --namespace "$NAMESPACE" --metric-data \
		"[$(metric_json "$OBSERVED_METRIC" "$timestamp" "$cpu"),$(metric_json "$SCALING_METRIC" "$timestamp" "$per_desired")]" ||
		echo "지표 올리기 실패, 다음 칸에 다시 한다"
}

# 두 누적값 사이 바쁜 몫(%). 건너뛴 칸이 있어도 실제로 지난 CPU 시간으로 나눈다.
busy_percent() {
	awk -v b="$1" -v t="$2" -v pb="$3" -v pt="$4" 'BEGIN {printf "%.1f", 100 * (b - pb) / (t - pt)}'
}

# main의 cpu와 상태(previous_busy, previous_total, ready_since)를 고친다. 올릴 값이 없는 칸이면 cpu가 빈다.
measure_slot() {
	local slot="$1" counters busy total
	cpu=""
	case "$(readiness_code)" in
	200) ;;
	503)
		# 기동이나 예열 중이다. 다시 준비되면 그때부터 유예를 센다.
		previous_busy=""
		previous_total=""
		ready_since=""
		return 0
		;;
	*) return 0 ;;
	esac
	counters=$(cpu_counters <"$PROC_STAT" 2>/dev/null) || return 0
	read -r busy total <<<"$counters"
	[ -z "$previous_total" ] || [ "$total" -gt "$previous_total" ] || return 0
	[ -n "$ready_since" ] || ready_since=$slot
	if [ -n "$previous_total" ] && [ $((slot - ready_since)) -ge "$READY_HOLD_SECONDS" ]; then
		cpu=$(busy_percent "$busy" "$total" "$previous_busy" "$previous_total")
	fi
	previous_busy=$busy
	previous_total=$total
}

main() {
	# 옛 서버처럼 ASG 밖이면 태그가 없다. 실패로 끝내면 systemd가 계속 다시 띄운다.
	ASG_NAME=$(imds tags/instance/aws:autoscaling:groupName) || {
		echo "ASG 서버가 아니라 지표를 올리지 않는다"
		exit 0
	}
	REGION=$(imds placement/region) || exit 1
	echo "$ASG_NAME 의 $NAMESPACE 지표를 ${PERIOD_SECONDS}초 칸마다 올린다"

	local previous_busy="" previous_total="" ready_since="" cpu="" slot capacity
	while true; do
		slot=$(sleep_until_next_slot)
		capacity=$(desired_capacity) || continue
		[ "$capacity" -gt 0 ] 2>/dev/null || continue
		measure_slot "$slot"
		# 준비되지 않았거나 유예 중인 서버는 올리지 않는다. 합에는 0으로 들어가는 것과 같다.
		[ -n "$cpu" ] && put_metrics "$slot" "$capacity" "$cpu"
	done
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
	main "$@"
fi
