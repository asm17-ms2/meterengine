#!/usr/bin/env bash
# SUT EC2에 remote/start-cpu-recording.sh 를 보내 부하 실험 동안 컨테이너별 CPU 기록을 켠다. 정한 시간이 지나면 스스로 멈춘다.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TF_DIR=$(cd "$SCRIPT_DIR/../terraform" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"

readonly REMOTE_SCRIPT_FILE="$SCRIPT_DIR/remote/start-cpu-recording.sh"
readonly RECORD_SECONDS="${RECORD_SECONDS:-5400}"
# 부하 발생기가 같은 RUN_ID를 다시 쓰므로 k6 lib/config.js의 RUN_ID_PATTERN과 같게 둔다.
readonly RUN_ID_PATTERN='^[A-Za-z0-9_-]{1,64}$'

main() {
	need_cmd aws terraform jq
	[ $# -eq 1 ] || die "사용법: $0 <RUN_ID>"
	[[ "$1" =~ $RUN_ID_PATTERN ]] || die "RUN_ID는 영문, 숫자, _, - 로 64자 이하여야 한다: $1"

	# 개발 서버도 compose 프로젝트 이름이 같아 대상은 test-storage 상태 파일로만 고른다.
	local sut_instance_id
	sut_instance_id=$(tf_out sut_instance_id)
	require_ec2_started "$sut_instance_id"
	wait_ssm_online "$sut_instance_id"
	log "SUT $sut_instance_id 에서 $1 CPU 기록을 켠다. ${RECORD_SECONDS}초 뒤 스스로 멈춘다"
	run_remote_script "$sut_instance_id" "start-cpu-recording" "$REMOTE_SCRIPT_FILE" "$1" "$RECORD_SECONDS"
	log "끝났다"
}

main "$@"
