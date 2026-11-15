#!/usr/bin/env bash
# 수집 부하와 따로 조회 요청을 조금씩 보내 응답 시간을 남긴다. 기본은 이벤트 한 건 찾기이고, READS=lookup,list 를 주면 한 고객의 이벤트 목록도 번갈아 부른다.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
K6_DIR=$(cd "$SCRIPT_DIR/../k6" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"
. "$SCRIPT_DIR/lib.sh"

readonly READ_RATE="${READ_RATE:-4}"
readonly READS="${READS:-lookup}"
readonly DURATION_SECONDS="${DURATION_SECONDS:-1800}"
readonly READ_TIMEOUT_SECONDS=10
# 수집 부하의 k6가 6565와 6566을 쓴다.
readonly K6_ADDRESS=127.0.0.1:6567

main() {
	need_cmd jq k6
	require_api_key
	# 수집 부하 스크립트는 결과 폴더가 이미 있으면 시작하지 않는다. 같이 돌릴 때는 RUN_ID를 다르게 준다.
	export RUN_ID="${RUN_ID:-$(date +%Y%m%d-%H%M)-read}"
	export RESULT_DIR="$K6_DIR/results/$RUN_ID"
	[ ! -e "$RESULT_DIR" ] || die "$RESULT_DIR 에 같은 RUN_ID 결과가 있다. RUN_ID를 바꾼다"
	mkdir -p "$RESULT_DIR"
	local prefix="$RESULT_DIR/$RUN_ID-read-probe"
	log "RUN_ID=$RUN_ID 초당 ${READ_RATE}건 ${DURATION_SECONDS}초, 조회 종류 $READS"
	k6 run -q --address "$K6_ADDRESS" -e RATE="$READ_RATE" -e READS="$READS" -e DURATION_SECONDS="$DURATION_SECONDS" \
		-e TIMEOUT_SECONDS="$READ_TIMEOUT_SECONDS" --console-output "$prefix.console.log" \
		--log-output "file=$prefix.k6.log" "$K6_DIR/read-probe.js" >/dev/null || die "k6 실패: $prefix.k6.log"
	log "끝: $prefix.console.log"
}

main "$@"
