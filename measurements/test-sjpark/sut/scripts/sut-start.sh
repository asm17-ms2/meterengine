#!/usr/bin/env bash
# SUT RDS를 먼저 켜고, 그다음 SUT EC2를 켜고, https 응답이 올 때까지 기다린다.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TF_DIR=$(cd "$SCRIPT_DIR/../terraform" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"

start_db() {
	local db_identifier="$1" db_status
	db_status=$(rds_status "$db_identifier") || exit 1
	if is_db_usable_status "$db_status" || [ "$db_status" = starting ]; then
		log "RDS $db_identifier 는 이미 $db_status 라 시작 요청을 건너뛴다"
	else
		case "$db_status" in
		stopped)
			aws rds start-db-instance --db-instance-identifier "$db_identifier" >/dev/null ||
				die "RDS $db_identifier 시작 요청이 실패했다"
			log "RDS $db_identifier 시작 요청"
			;;
		stopping)
			die "RDS $db_identifier 가 중지 중이다. 중지가 끝난 뒤 다시 실행한다"
			;;
		*)
			log "RDS $db_identifier 상태가 $db_status 다. 시작 요청 없이 쓸 수 있는 상태가 되기를 기다린다"
			;;
		esac
	fi
	wait_db_usable "$db_identifier"
}

main() {
	need_cmd aws terraform curl

	local sut_instance_id db_identifier domain
	sut_instance_id=$(tf_out sut_instance_id)
	db_identifier=$(tf_out db_identifier)
	domain=$(tf_out domain)

	start_db "$db_identifier"
	start_ec2_instance "$sut_instance_id"
	wait_ssm_online "$sut_instance_id"
	wait_for_usage_ok "$domain" "배포한 적이 없으면 sut-deploy.sh 를 돌린다"
	log "SUT를 켰다"
}

main "$@"
