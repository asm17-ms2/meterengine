#!/usr/bin/env bash
# 수집 서버와 컨슈머 서버 오토스케일링 그룹을 0대로 내린 뒤 SUT RDS를 멈춘다. 지우지는 않는다. MSK는 apply로 지운다.

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

# 04:00 예약처럼 최소와 원하는 대수를 0으로 내린다. 최소 0은 Terraform 기본값이라 다음 apply와 어긋나지 않는다.
stop_servers() {
	local asg_name="$1"
	aws autoscaling update-auto-scaling-group --auto-scaling-group-name "$asg_name" \
		--min-size 0 --desired-capacity 0 ||
		die "오토스케일링 그룹 $asg_name 을 0대로 내리지 못했다"
	log "오토스케일링 그룹 $asg_name 을 0대로 내렸다"
	wait_asg_empty "$asg_name"
}

# 컨슈머 그룹을 더하기 전에 apply한 상태에는 출력도 그룹도 없다. 그때는 건너뛰고 나머지를 끈다.
stop_consumer_servers() {
	local consumer_asg_name
	consumer_asg_name=$(tf_out_or_empty consumer_asg_name)
	if [ -z "$consumer_asg_name" ]; then
		log "terraform output consumer_asg_name 이 없어 컨슈머 서버는 건너뛴다"
		return 0
	fi
	if ! asg_exists "$consumer_asg_name"; then
		log "오토스케일링 그룹 $consumer_asg_name 이 없어 건너뛴다"
		return 0
	fi
	stop_servers "$consumer_asg_name"
}

main() {
	need_cmd aws terraform

	local asg_name db_identifier
	asg_name=$(tf_out asg_name)
	db_identifier=$(tf_out db_identifier)

	# 발행하는 쪽을 먼저 내리고, DB에 쓰는 컨슈머 서버가 다 빠진 뒤에 DB를 멈춘다.
	stop_servers "$asg_name"
	stop_consumer_servers
	stop_db "$db_identifier"

	log "RDS는 꺼 둔 지 7일 뒤 저절로 켜진다. 자동 중지가 켜져 있으면 다음 04:00에 다시 꺼진다"
}

main "$@"
