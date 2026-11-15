#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"
. "$SCRIPT_DIR/lib.sh"

readonly STORE_WAIT_SECONDS="${STORE_WAIT_SECONDS:-600}"
readonly STORE_POLL_SECONDS=5

count_ids() {
	psql -X -q -A -t -F ' ' -v ON_ERROR_STOP=1 -v organization_ids="$1" -v prefix="$2" -v iterations="$3" \
		-v period="$4" -v failed_ids_file="$5" -v started_at="$6" <<'SQL'
CREATE TEMP TABLE failed (id text PRIMARY KEY);
\set copy '\\copy failed FROM ' :'failed_ids_file'
:copy
CREATE TEMP TABLE expected AS
SELECT :'prefix' || n AS id FROM generate_series(0, :iterations - 1) AS n
WHERE n % :period <> :period - 1;
CREATE TEMP TABLE stored AS
SELECT transaction_id AS id, received_at FROM event
WHERE organization_id = ANY (string_to_array(:'organization_ids', ',')::uuid[]) AND starts_with(transaction_id, :'prefix');
SELECT 'expected', count(*) FROM expected
UNION ALL SELECT 'stored', count(*) FROM stored
UNION ALL SELECT 'failed', count(*) FROM failed
UNION ALL SELECT 'stored_before_run', count(*) FROM stored WHERE received_at < :'started_at'::timestamptz
UNION ALL SELECT 'failed_but_stored', count(*) FROM failed f WHERE EXISTS (SELECT 1 FROM stored s WHERE s.id = f.id)
UNION ALL SELECT 'lost', count(*) FROM expected e
  WHERE NOT EXISTS (SELECT 1 FROM stored s WHERE s.id = e.id) AND NOT EXISTS (SELECT 1 FROM failed f WHERE f.id = e.id);
SQL
}

count_of() {
	awk -v key="$1" '$1 == key { print $2 }' <<<"$2"
}

# 카프카 경로는 202로 답한 뒤에 컨슈머가 저장한다. 받았다고 답한 것이 다 들어오거나 시간이 다할 때까지 다시 센다.
count_ids_when_stored() {
	local deadline=$((SECONDS + STORE_WAIT_SECONDS)) counts
	while :; do
		counts=$(count_ids "$@") || return 1
		if [ "$(count_of lost "$counts")" = 0 ] || [ "$SECONDS" -ge "$deadline" ]; then
			printf '%s\n' "$counts"
			return 0
		fi
		sleep "$STORE_POLL_SECONDS"
	done
}

main() {
	[ $# -eq 1 ] && [ -f "$1" ] || die "사용법: $0 <ingest 결과 JSON>"
	need_cmd jq psql
	local file="$1" console="${1%.json}.console.log"
	[ -f "$console" ] || die "콘솔 출력 파일이 없다: $console"
	[ "$(json "$file" '.k6.metrics.iterations.values.count')" = "$(json "$file" '.k6.metrics.events_started.values.count')" ] ||
		die "중간에 끊긴 실행이라 대조하지 않는다"
	# 조직 한 곳만 쓰던 때의 결과 파일과 장애 실험 결과에는 organization_id 하나만 있다.
	local organization_ids
	organization_ids=$(json "$file" '.metadata.organization_ids // [.metadata.organization_id // empty] | join(",")')
	[ -n "$organization_ids" ] || die "결과 파일에 metadata.organization_ids 가 없다: $file"
	[ -n "${PGHOST:-}" ] || use_sut_db

	local failed_ids counts accepted count=count_ids
	failed_ids=$(mktemp)
	trap "rm -f '$failed_ids'" EXIT
	{ grep -o 'FAILED_ID [^ "]*' "$console" || true; } | cut -d' ' -f2 | sort -u >"$failed_ids"
	accepted=$(json "$file" '.k6.metrics.events_accepted.values.count // 0')
	if [ "$accepted" -gt 0 ]; then
		log "카프카 경로 실행이다. 컨슈머가 다 저장할 때까지 최대 ${STORE_WAIT_SECONDS}초 기다린다"
		count=count_ids_when_stored
	fi
	counts=$("$count" "$organization_ids" \
		"$(json "$file" .run_id)-$(json "$file" .metadata.step)-" \
		"$(json "$file" .k6.metrics.iterations.values.count)" "$(json "$file" .metadata.duplicate_period)" \
		"$failed_ids" "$(json "$file" .metadata.started_at)") || die "대조 쿼리 실패"
	log "$(json "$file" .run_id) $(json "$file" .metadata.step): $(tr '\n' ' ' <<<"$counts")"

	[ "$(count_of stored_before_run "$counts")" = 0 ] || die "실행 전에 저장된 같은 id가 있다. RUN_ID를 다시 썼다"
	[ "$(count_of failed_but_stored "$counts")" = 0 ] || warn "실패로 기록했지만 저장된 이벤트가 있다"
	[ "$(count_of lost "$counts")" = 0 ] || die "성공 응답을 받았는데 DB에 없는 이벤트가 $(count_of lost "$counts") 건 있다"

	local resends answered_new answered_duplicate
	resends=$(($(json "$file" .k6.metrics.iterations.values.count) - $(count_of expected "$counts")))
	# 202에는 duplicate가 없어 응답으로는 재전송을 가릴 수 없다. 같은 id가 두 행이 될 수는 없으니(기본 키) 유실만 본다.
	if [ "$accepted" -gt 0 ]; then
		log "유실 0건, 재전송 ${resends}건. 중복 판정 건수는 컨슈머 지표 meterengine_event_consume_total 의 result=duplicate 로 본다"
		return 0
	fi
	answered_new=$(json "$file" '.k6.metrics.events_stored.values.count // 0')
	answered_duplicate=$(json "$file" '.k6.metrics.events_duplicate.values.count // 0')
	[ "$answered_new" -le "$(count_of stored "$counts")" ] || die "재전송을 새로 저장했다고 답한 이벤트가 있다"
	if [ "$(json "$file" '.k6.metrics.events_failed.values.count // 0')" = 0 ]; then
		[ "$answered_duplicate" = "$resends" ] || die "재전송 ${resends}건 중 duplicate 응답은 ${answered_duplicate}건이다"
	fi
	log "유실 0건, 재전송 ${resends}건 중 duplicate ${answered_duplicate}건"
}

main "$@"
