#!/usr/bin/env bash
# SUT RDS를 켠다. 앱 서버가 떠 있으면 https 응답이 올 때까지 기다린다. 앱 서버는 apply -var asg_min_size=1로 띄운다.

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

	local asg_name db_identifier domain instances
	asg_name=$(tf_out asg_name)
	db_identifier=$(tf_out db_identifier)
	domain=$(tf_out domain)

	start_db "$db_identifier"
	instances=$(asg_in_service_instances "$asg_name") || exit 1
	if [ -z "$instances" ]; then
		log "앱 서버가 0대다. 버전을 기록한 적이 없으면 sut-deploy.sh 를 먼저 돌리고, terraform apply -var asg_min_size=1 로 띄운다"
		return 0
	fi
	wait_for_usage_ok "$domain" "앱 서버가 부팅 중이면 몇 분 더 걸린다. 대상 그룹 상태와 서버의 /var/log/cloud-init-output.log 를 본다"
	log "SUT를 켰다"
}

main "$@"
