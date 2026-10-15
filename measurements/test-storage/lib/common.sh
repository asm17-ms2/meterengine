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
