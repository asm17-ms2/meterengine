#!/usr/bin/env bash
# sut/scripts의 스크립트가 source 한다. macOS 기본 bash 3.2에서 돌아야 하므로 bash 4 문법을 쓰지 않는다.
# tf_out이 읽는 TF_DIR은 부르는 스크립트가 정한다.

set -euo pipefail

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
	printf 'common.sh 는 source 전용이다\n' >&2
	exit 1
fi

export AWS_REGION=ap-northeast-2

LOG_PREFIX=$(basename "$0" .sh)

readonly SSM_POLL_INTERVAL_SECONDS=5
readonly SSM_ONLINE_WAIT_LIMIT_SECONDS=600
readonly USAGE_REQUEST_TIMEOUT_SECONDS=10
readonly USAGE_RETRY_LIMIT=60
readonly USAGE_RETRY_INTERVAL_SECONDS=5
readonly HTTP_OK=200
readonly DB_USABLE_STATUSES="available backing-up storage-optimization"
readonly DB_USABLE_WAIT_LIMIT_SECONDS=1800
readonly DB_USABLE_POLL_INTERVAL_SECONDS=30

# terraform은 ~/.local/bin에 있고, 비대화형 셸의 PATH에는 빠져 있을 수 있다.
if ! command -v terraform >/dev/null 2>&1 && [ -x "$HOME/.local/bin/terraform" ]; then
	PATH="$HOME/.local/bin:$PATH"
fi

log() { printf '[%s] %s\n' "$LOG_PREFIX" "$*"; }
warn() { printf '[%s] 경고: %s\n' "$LOG_PREFIX" "$*" >&2; }
die() {
	printf '[%s] 실패: %s\n' "$LOG_PREFIX" "$*" >&2
	exit 1
}

need_cmd() {
	local cmd
	for cmd in "$@"; do
		command -v "$cmd" >/dev/null 2>&1 || die "$cmd 명령이 없다. 설치한 뒤 다시 실행한다"
	done
}

tf_out() {
	local name="$1" value
	[ -n "${TF_DIR:-}" ] || die "TF_DIR이 정해지지 않았다"
	[ -s "$TF_DIR/terraform.tfstate" ] ||
		die "$TF_DIR 에 상태 파일이 없다. terraform apply 먼저 한다"
	value=$(terraform -chdir="$TF_DIR" output -raw "$name" 2>/dev/null) ||
		die "terraform output $name 을 읽지 못했다. $TF_DIR 에서 terraform apply 먼저 한다"
	[ -n "$value" ] || die "terraform output $name 이 비어 있다. terraform apply 먼저 한다"
	printf '%s' "$value"
}

ec2_state() {
	local instance_id="$1"
	aws ec2 describe-instances \
		--instance-ids "$instance_id" \
		--query 'Reservations[0].Instances[0].State.Name' \
		--output text || die "EC2 $instance_id 상태를 읽지 못했다"
}

# 중지 중인 인스턴스는 start-instances가 거절하므로 중지가 끝날 때까지 먼저 기다린다.
start_ec2_instance() {
	local instance_id="$1" state
	state=$(ec2_state "$instance_id") || exit 1
	case "$state" in
	running | pending)
		log "EC2 $instance_id 는 이미 $state 라 시작 요청을 건너뛴다"
		;;
	stopping | stopped)
		if [ "$state" = stopping ]; then
			log "EC2 $instance_id 가 멈출 때까지 기다린다"
			aws ec2 wait instance-stopped --instance-ids "$instance_id" ||
				die "EC2 $instance_id 가 stopped 가 되지 않았다"
		fi
		aws ec2 start-instances --instance-ids "$instance_id" >/dev/null ||
			die "EC2 $instance_id 시작 요청이 실패했다"
		log "EC2 $instance_id 시작 요청"
		;;
	*)
		die "EC2 $instance_id 상태가 $state 라 시작할 수 없다"
		;;
	esac
	aws ec2 wait instance-running --instance-ids "$instance_id" ||
		die "EC2 $instance_id 가 running 이 되지 않았다"
	log "EC2 $instance_id 가 running 이다"
}

# 켜지는 중인 인스턴스는 stop-instances가 거절할 수 있으므로 running이 될 때까지 먼저 기다린다.
stop_ec2_instance() {
	local instance_id="$1" state
	state=$(ec2_state "$instance_id") || exit 1
	case "$state" in
	stopped)
		log "EC2 $instance_id 는 이미 stopped 라 건너뛴다"
		return 0
		;;
	stopping)
		log "EC2 $instance_id 가 이미 중지 중이다"
		;;
	running | pending)
		if [ "$state" = pending ]; then
			log "EC2 $instance_id 가 running 이 될 때까지 기다린다"
			aws ec2 wait instance-running --instance-ids "$instance_id" ||
				die "EC2 $instance_id 가 running 이 되지 않았다"
		fi
		aws ec2 stop-instances --instance-ids "$instance_id" >/dev/null ||
			die "EC2 $instance_id 중지 요청이 실패했다"
		log "EC2 $instance_id 중지 요청"
		;;
	*)
		die "EC2 $instance_id 상태가 $state 라 멈출 수 없다"
		;;
	esac
	aws ec2 wait instance-stopped --instance-ids "$instance_id" ||
		die "EC2 $instance_id 가 stopped 가 되지 않았다"
	log "EC2 $instance_id 가 stopped 다"
}

rds_status() {
	local db_identifier="$1"
	aws rds describe-db-instances \
		--db-instance-identifier "$db_identifier" \
		--query 'DBInstances[0].DBInstanceStatus' \
		--output text || die "RDS $db_identifier 상태를 읽지 못했다"
}

is_db_usable_status() {
	local db_status="$1"
	case " $DB_USABLE_STATUSES " in
	*" $db_status "*) return 0 ;;
	esac
	return 1
}

log_db_usable() {
	local db_identifier="$1" db_status="$2"
	log "RDS $db_identifier 상태가 $db_status 다. 쓸 수 있는 상태다"
}

wait_db_usable() {
	local db_identifier="$1" db_status=""
	local deadline=$(($(date +%s) + DB_USABLE_WAIT_LIMIT_SECONDS))
	while :; do
		db_status=$(rds_status "$db_identifier") || exit 1
		if is_db_usable_status "$db_status"; then
			log_db_usable "$db_identifier" "$db_status"
			return 0
		fi
		case "$db_status" in
		failed | incompatible-* | deleting | stopping)
			die "RDS $db_identifier 상태가 $db_status 라 더 기다리지 않는다"
			;;
		esac
		[ "$(date +%s)" -lt "$deadline" ] ||
			die "RDS $db_identifier 가 ${DB_USABLE_WAIT_LIMIT_SECONDS}초 안에 쓸 수 있는 상태($DB_USABLE_STATUSES)가 되지 않았다(마지막 상태: $db_status)"
		sleep "$DB_USABLE_POLL_INTERVAL_SECONDS"
	done
}

# 연결하지 못하면 curl이 찍는 000을 그대로 돌려준다.
usage_status_code() {
	local domain="$1"
	curl -s -o /dev/null -w '%{http_code}' --max-time "$USAGE_REQUEST_TIMEOUT_SECONDS" "https://$domain/usage" || true
}

wait_for_usage_ok() {
	local domain="$1" failure_hint="$2" attempt code=""
	for attempt in $(seq 1 "$USAGE_RETRY_LIMIT"); do
		code=$(usage_status_code "$domain")
		if [ "$code" = "$HTTP_OK" ]; then
			log "https://$domain/usage 가 $HTTP_OK 을 돌려줬다(${attempt}회 시도)"
			return 0
		fi
		sleep "$USAGE_RETRY_INTERVAL_SECONDS"
	done
	die "https://$domain/usage 가 $((USAGE_RETRY_LIMIT * USAGE_RETRY_INTERVAL_SECONDS))초 안에 $HTTP_OK 이 되지 않았다(마지막 코드: $code). $failure_hint"
}

wait_ssm_online() {
	local instance_id="$1" ping_status=""
	local deadline=$(($(date +%s) + SSM_ONLINE_WAIT_LIMIT_SECONDS))
	while :; do
		ping_status=$(aws ssm describe-instance-information \
			--filters "Key=InstanceIds,Values=$instance_id" \
			--query 'InstanceInformationList[0].PingStatus' \
			--output text 2>/dev/null || true)
		if [ "$ping_status" = Online ]; then
			log "$instance_id 가 SSM에 Online 이다"
			return 0
		fi
		[ "$(date +%s)" -lt "$deadline" ] ||
			die "$instance_id 가 ${SSM_ONLINE_WAIT_LIMIT_SECONDS}초 안에 SSM Online 이 되지 않았다(마지막 상태: ${ping_status:-없음})"
		sleep "$SSM_POLL_INTERVAL_SECONDS"
	done
}
