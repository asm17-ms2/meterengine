#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
K6_DIR=$(cd "$SCRIPT_DIR/../k6" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"
. "$SCRIPT_DIR/lib.sh"

readonly DURATION_SECONDS="${DURATION_SECONDS:-1800}"
readonly LOAD_STEP=load
readonly LOAD_RATE=500
readonly LOAD_TIMEOUT_SECONDS=65
readonly APP_PROBE_STEP=app_probe
readonly APP_PROBE_RATE=5
readonly APP_PROBE_TIMEOUT_SECONDS=3
readonly APP_PROBE_K6_ADDRESS=127.0.0.1:6566
readonly APP_PROBE_START_DELAY_SECONDS=10
readonly BASELINE_SECONDS=180
readonly DB_PROBE_INTERVAL_SECONDS=1
readonly DB_PROBE_CONNECT_TIMEOUT_SECONDS=2
readonly DB_PROBE_KILL_SECONDS=3
readonly DB_PROBE_ERROR_LENGTH=120
readonly TIMEOUT_EXIT_CODE=124

DB_PROBER_PID=""
APP_PROBE_PID=""

now_ms() {
	date +%s%3N
}

probe_db_once() {
	local sent_ms ip ok=0 error=""
	sent_ms=$(now_ms)
	ip=$(getent ahostsv4 "$PGHOST" | awk 'NR == 1 { print $1 }') || true
	if [ -z "$ip" ]; then
		printf '%s\t-\t0\t%s\t이름 풀이 실패\n' "$sent_ms" "$(($(now_ms) - sent_ms))" >>"$DB_PROBE_FILE"
		return 0
	fi
	if error=$(PGCONNECT_TIMEOUT=$DB_PROBE_CONNECT_TIMEOUT_SECONDS timeout "$DB_PROBE_KILL_SECONDS" \
		psql "host=$PGHOST hostaddr=$ip" -X -A -t -c 'select 1' 2>&1 >/dev/null); then
		ok=1
	else
		[ $? -ne "$TIMEOUT_EXIT_CODE" ] || error="시간 초과"
	fi
	printf '%s\t%s\t%s\t%s\t%s\n' "$sent_ms" "$ip" "$ok" "$(($(now_ms) - sent_ms))" \
		"$(head -n 1 <<<"$error" | tr '\t' ' ' | cut -c "1-$DB_PROBE_ERROR_LENGTH")" >>"$DB_PROBE_FILE"
}

probe_db_every_second() {
	printf 'sent_ms\tresolved_ip\tok\telapsed_ms\terror\n' >"$DB_PROBE_FILE"
	while :; do
		probe_db_once &
		sleep "$DB_PROBE_INTERVAL_SECONDS"
	done
}

start_db_prober() {
	DB_PROBE_FILE="$1"
	probe_db_every_second &
	DB_PROBER_PID=$!
}

stop_db_prober() {
	[ -n "$DB_PROBER_PID" ] || return 0
	pkill -P "$DB_PROBER_PID" 2>/dev/null || true
	kill "$DB_PROBER_PID" 2>/dev/null || true
	wait "$DB_PROBER_PID" 2>/dev/null || true
	DB_PROBER_PID=""
	log "DB 탐침 실패 $(awk -F'\t' 'NR > 1 && $3 == 0' "$DB_PROBE_FILE" | wc -l)회: $DB_PROBE_FILE"
}

stop_app_probe() {
	[ -n "$APP_PROBE_PID" ] || return 0
	pkill -P "$APP_PROBE_PID" 2>/dev/null || true
	kill "$APP_PROBE_PID" 2>/dev/null || true
	wait "$APP_PROBE_PID" 2>/dev/null || true
	APP_PROBE_PID=""
}

cleanup() {
	stop_app_probe
	stop_db_prober
	stop_cpu_sampler
}

run_k6() {
	local step="$1" rate="$2" timeout_seconds="$3" address="$4" prefix="$RESULT_DIR/$RUN_ID-ingest-$1"
	k6 run -q --address "$address" -e STEP="$step" -e RATE="$rate" -e DURATION_SECONDS="$DURATION_SECONDS" \
		-e TIMEOUT_SECONDS="$timeout_seconds" --console-output "$prefix.console.log" --log-output "file=$prefix.k6.log" \
		"$K6_DIR/ingest-fault.js" >/dev/null || die "k6 실패: $prefix.k6.log"
}

run_app_probe() {
	sleep "$APP_PROBE_START_DELAY_SECONDS"
	run_k6 "$APP_PROBE_STEP" "$APP_PROBE_RATE" "$APP_PROBE_TIMEOUT_SECONDS" "$APP_PROBE_K6_ADDRESS"
}

print_step_result() {
	local file="$RESULT_DIR/$RUN_ID-ingest-$1.json"
	log "$1: 보냄 $(json "$file" '.k6.metrics.iterations.values.count // 0')," \
		"못 보냄 $(json "$file" '.k6.metrics.dropped_iterations.values.count // 0')," \
		"실패 $(json "$file" '.k6.metrics.events_failed.values.count // 0'): $file"
}

main() {
	need_cmd jq k6 psql aws getent timeout
	export RUN_ID="${RUN_ID:-$(date +%Y%m%d-%H%M)-fault}"
	export RESULT_DIR="$K6_DIR/results/$RUN_ID"
	[ ! -e "$RESULT_DIR" ] || die "$RESULT_DIR 에 같은 RUN_ID 결과가 있다. RUN_ID를 바꾼다"
	use_sut_db
	mkdir -p "$RESULT_DIR"
	trap cleanup EXIT

	start_cpu_sampler "$RESULT_DIR/$RUN_ID-load-generator-cpu.tsv"
	start_db_prober "$RESULT_DIR/$RUN_ID-db-probe.tsv"
	run_app_probe &
	APP_PROBE_PID=$!

	local started_at
	started_at=$(date +%s)
	log "RUN_ID=$RUN_ID 초당 ${LOAD_RATE}건 ${DURATION_SECONDS}초"
	log "장애 주입은 $(kst $((started_at + BASELINE_SECONDS))) 이후, 부하는 $(kst $((started_at + DURATION_SECONDS))) 쯤 끝난다"
	run_k6 "$LOAD_STEP" "$LOAD_RATE" "$LOAD_TIMEOUT_SECONDS" localhost:6565
	wait "$APP_PROBE_PID" || die "앱 탐침 k6가 실패했다"
	APP_PROBE_PID=""
	cleanup
	trap - EXIT

	print_step_result "$LOAD_STEP"
	print_step_result "$APP_PROBE_STEP"
}

main "$@"
