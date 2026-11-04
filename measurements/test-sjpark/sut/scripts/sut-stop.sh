#!/usr/bin/env bash
# SUT EC2를 멈춘 뒤 SUT RDS를 멈춘다. 지우지는 않는다.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TF_DIR=$(cd "$SCRIPT_DIR/../terraform" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"

stop_db() {
	local db_identifier="$1" db_status
	db_status=$(rds_status "$db_identifier") || exit 1
	case "$db_status" in
	available)
		aws rds stop-db-instance --db-instance-identifier "$db_identifier" >/dev/null ||
			die "RDS $db_identifier 중지 요청이 실패했다"
		log "RDS $db_identifier 중지 요청. 멈추는 데 몇 분 걸린다"
		;;
	stopped | stopping)
		log "RDS $db_identifier 는 이미 $db_status 라 건너뛴다"
		;;
	*)
		die "RDS $db_identifier 상태가 $db_status 라 지금은 멈출 수 없다. available이 된 뒤 다시 실행한다"
		;;
	esac
}

main() {
	need_cmd aws terraform

	local sut_instance_id db_identifier
	sut_instance_id=$(tf_out sut_instance_id)
	db_identifier=$(tf_out db_identifier)

	stop_ec2_instance "$sut_instance_id"
	stop_db "$db_identifier"

	log "RDS는 꺼 둔 지 7일 뒤 저절로 켜진다. 자동 중지가 켜져 있으면 다음 04:00에 다시 꺼진다"
}

main "$@"
