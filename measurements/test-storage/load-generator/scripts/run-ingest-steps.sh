#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
K6_DIR=$(cd "$SCRIPT_DIR/../k6" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"
. "$SCRIPT_DIR/lib.sh"

readonly RATES="${RATES:-100 250 500 1000 1500 2000 2500 3000 3500 4000}"
readonly STEP_SECONDS="${STEP_SECONDS:-360}"
readonly WARMUP_RATE=100
readonly WARMUP_SECONDS=120
readonly ERROR_RATE_LIMIT=0.0001
readonly REQUEST='POST /v1/events'

run_step() {
	local step="$1" rate="$2" seconds="$3" prefix="$RESULT_DIR/$RUN_ID-ingest-$1"
	k6 run -q -e STEP="$step" -e RATE="$rate" -e DURATION_SECONDS="$seconds" \
		--console-output "$prefix.console.log" --log-output "file=$prefix.k6.log" \
		"$K6_DIR/ingest.js" >/dev/null || die "k6 실패: $prefix.k6.log"
}

step_row() {
	local file="$1" stored_total="$2"
	printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
		"$(json "$file" .metadata.rate)" \
		"$(json "$file" '.k6.metrics.iterations.values.rate // 0 | floor')" \
		"$(json "$file" '.k6.metrics.dropped_iterations.values.count // 0')" \
		"$(trend "$file" "http_req_duration{name:$REQUEST}" med)" \
		"$(trend "$file" "http_req_duration{name:$REQUEST}" 'p(99)')" \
		"$(json "$file" ".k6.metrics[\"http_req_failed{name:$REQUEST}\"].values.rate // 0")" \
		"$stored_total" \
		"$(json "$file" '.k6.metrics.vus_max.values.max // 0')/$(json "$file" .metadata.max_vus)" \
		"$(time_columns "$(epoch "$file" .metadata.started_at)" "$(epoch "$file" .metadata.ended_at)")"
}

over() {
	awk -v value="$1" -v limit="$2" 'BEGIN { exit !(value > limit) }'
}

all_vus_busy() {
	[ "${1%/*}" -ge "${1#*/}" ]
}

main() {
	need_cmd jq k6
	export RUN_ID="${RUN_ID:-$(date +%Y%m%d-%H%M)-ingest}"
	export RESULT_DIR="$K6_DIR/results/$RUN_ID"
	[ ! -e "$RESULT_DIR" ] || die "$RESULT_DIR 에 같은 RUN_ID 결과가 있다. RUN_ID를 바꾼다"
	mkdir -p "$RESULT_DIR"
	trap stop_cpu_sampler EXIT
	start_cpu_sampler "$RESULT_DIR/$RUN_ID-load-generator-cpu.tsv"

	log "RUN_ID=$RUN_ID 준비 ${WARMUP_SECONDS}초"
	run_step warmup "$WARMUP_RATE" "$WARMUP_SECONDS"
	local stored_total table="$RESULT_DIR/$RUN_ID-steps.tsv" rate file row
	stored_total=$(json "$RESULT_DIR/$RUN_ID-ingest-warmup.json" '.k6.metrics.events_stored.values.count // 0')
	printf 'rate\tsent_per_s\tdropped\tp50_ms\tp99_ms\terror_rate\tstored_total\tvus\tstart_kst\tend_kst\tgrafana\n' >"$table"

	for rate in $RATES; do
		log "초당 ${rate}건 ${STEP_SECONDS}초"
		run_step "r$rate" "$rate" "$STEP_SECONDS"
		file="$RESULT_DIR/$RUN_ID-ingest-r$rate.json"
		stored_total=$((stored_total + $(json "$file" '.k6.metrics.events_stored.values.count // 0')))
		row=$(step_row "$file" "$stored_total")
		printf '%s\n' "$row" >>"$table"
		if over "$(cut -f6 <<<"$row")" "$ERROR_RATE_LIMIT"; then
			log "초당 ${rate}건에서 오류율 ${ERROR_RATE_LIMIT} 초과로 멈춘다"
			break
		fi
		if all_vus_busy "$(cut -f8 <<<"$row")"; then
			log "초당 ${rate}건에서 서버 응답이 느려져 VU를 다 써서 멈춘다"
			break
		fi
	done

	print_table "$table"
}

main "$@"
