readonly GRAFANA_DASHBOARD_URL="http://localhost:3001/d/meterengine-storage?var-db=meterengine-test-storage-db"
readonly CLOUDWATCH_LAG_SECONDS=180
readonly CPU_SAMPLE_SECONDS=10
readonly CPU_LIMIT_PERCENT=80
readonly SUT_PARAM_PATH=/meterengine/test-storage

CPU_SAMPLER_PID=""

json() {
	jq -r "$2" "$1"
}

require_api_key() {
	[ -n "${API_KEY:-}" ] || die "API_KEY 환경변수가 없다. ingest, read 스코프 키를 export API_KEY 로 넣는다"
	export API_KEY
}

use_sut_db() {
	local name value
	while IFS=$'\t' read -r name value; do
		case "${name##*/}" in
		db-host) export PGHOST="$value" ;;
		db-port) export PGPORT="$value" ;;
		db-name) export PGDATABASE="$value" ;;
		db-username) export PGUSER="$value" ;;
		db-password) export PGPASSWORD="$value" ;;
		esac
	done < <(aws ssm get-parameters --with-decryption --query 'Parameters[].[Name,Value]' --output text \
		--names "$SUT_PARAM_PATH"/{db-host,db-port,db-name,db-username,db-password})
	[ -n "${PGPASSWORD:-}" ] || die "$SUT_PARAM_PATH 에서 DB 접속값을 읽지 못했다"
	export PGSSLMODE=require
}

trend() {
	json "$1" ".k6.metrics[\"$2\"].values[\"$3\"] // 0 | . * 10 | round / 10"
}

epoch() {
	json "$1" "$2 | sub(\"\\\\.[0-9]+Z$\"; \"Z\") | fromdateiso8601"
}

kst() {
	TZ=Asia/Seoul jq -rn --argjson epoch "$1" '$epoch | localtime | strftime("%Y-%m-%d %H:%M:%S")'
}

time_columns() {
	printf '%s\t%s\t%s&from=%s000&to=%s000' "$(kst "$1")" "$(kst "$2")" "$GRAFANA_DASHBOARD_URL" "$1" "$(($2 + CLOUDWATCH_LAG_SECONDS))"
}

print_table() {
	log "결과: $1"
	column -t -s "$(printf '\t')" "$1" 2>/dev/null || cat "$1"
}

sample_cpu() {
	local label user nice system idle iowait irq softirq steal rest total busy last_total="" last_busy=""
	printf 'time_kst\tcpu_percent\n' >"$CPU_FILE"
	while :; do
		read -r label user nice system idle iowait irq softirq steal rest </proc/stat
		total=$((user + nice + system + idle + iowait + irq + softirq + steal))
		busy=$((total - idle - iowait))
		if [ -n "$last_total" ]; then
			printf '%s\t%s\n' "$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S')" \
				"$((100 * (busy - last_busy) / (total - last_total)))" >>"$CPU_FILE"
		fi
		last_total=$total
		last_busy=$busy
		sleep "$CPU_SAMPLE_SECONDS"
	done
}

start_cpu_sampler() {
	CPU_FILE="$1"
	sample_cpu &
	CPU_SAMPLER_PID=$!
}

stop_cpu_sampler() {
	[ -n "$CPU_SAMPLER_PID" ] || return 0
	pkill -P "$CPU_SAMPLER_PID" 2>/dev/null || true
	kill "$CPU_SAMPLER_PID" 2>/dev/null || true
	wait "$CPU_SAMPLER_PID" 2>/dev/null || true
	local max
	max=$(awk -F'\t' 'NR > 1 && $2 > max { max = $2 } END { print max + 0 }' "$CPU_FILE")
	if [ "$max" -gt "$CPU_LIMIT_PERCENT" ]; then
		warn "부하 발생기 CPU 최대 ${max}%. ${CPU_LIMIT_PERCENT}% 를 넘은 구간은 결과로 쓰지 않는다: $CPU_FILE"
	else
		log "부하 발생기 CPU 최대 ${max}%"
	fi
}
