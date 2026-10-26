#!/usr/bin/env bash
# sut/scripts와 load-generator/scripts의 스크립트가 source 한다. macOS 기본 bash 3.2에서 돌아야 하므로 bash 4 문법을 쓰지 않는다.
# tf_out이 읽는 TF_DIR은 부르는 스크립트가 정한다.

set -euo pipefail

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
	printf 'common.sh 는 source 전용이다\n' >&2
	exit 1
fi

export AWS_REGION=ap-northeast-2

LOG_PREFIX=$(basename "$0" .sh)

readonly DEV_SERVER_PARAM_PATH=/meterengine/prod
readonly SSM_EXECUTION_TIMEOUT_SECONDS=900
readonly SSM_DELIVERY_TIMEOUT_SECONDS=600
readonly SSM_POLL_INTERVAL_SECONDS=5
readonly SSM_COMMAND_WAIT_LIMIT_SECONDS=$((SSM_EXECUTION_TIMEOUT_SECONDS + SSM_DELIVERY_TIMEOUT_SECONDS))
readonly SSM_MAX_CONSECUTIVE_POLL_ERRORS=6
readonly SSM_COMMENT_MAX_LENGTH=100
readonly SSM_ONLINE_WAIT_LIMIT_SECONDS=600
readonly REMOTE_SCRIPT_DELIMITER=MEASUREMENTS_REMOTE_SCRIPT_EOF
readonly USAGE_REQUEST_TIMEOUT_SECONDS=10
readonly USAGE_MAX_REDIRECTS=5
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

# 원격 셸 명령에 그대로 들어가는 값에서 셸이 해석할 문자를 막는다.
require_shell_safe() {
	local label="$1" value="$2"
	[[ "$value" =~ ^[A-Za-z0-9._/:=@+-]+$ ]] || die "$label 에 허용하지 않는 문자가 있다: $value"
	case "$value" in
	-*) die "$label 은 - 로 시작할 수 없다: $value" ;;
	esac
}

require_not_dev_server_path() {
	local param_path="$1"
	case "$param_path" in
	"$DEV_SERVER_PARAM_PATH" | "$DEV_SERVER_PARAM_PATH"/*)
		die "개발 서버 경로 $DEV_SERVER_PARAM_PATH 는 쓰지 않는다"
		;;
	esac
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

# pending은 곧 running이 되므로 통과시키고, 뒤따르는 wait_ssm_online이 기다린다.
require_ec2_started() {
	local instance_id="$1" state
	state=$(ec2_state "$instance_id") || exit 1
	case "$state" in
	running | pending)
		log "EC2 $instance_id 가 $state 다"
		;;
	stopped | stopping)
		die "EC2 $instance_id 상태가 $state 다. sut-start.sh 먼저 실행한다"
		;;
	*)
		die "EC2 $instance_id 상태가 $state 다"
		;;
	esac
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

require_db_usable() {
	local db_identifier="$1" db_status
	db_status=$(rds_status "$db_identifier") || exit 1
	if is_db_usable_status "$db_status"; then
		log_db_usable "$db_identifier" "$db_status"
		return 0
	fi
	case "$db_status" in
	stopped | stopping | starting)
		die "RDS $db_identifier 상태가 $db_status 다. sut-start.sh 먼저 실행한다"
		;;
	*)
		die "RDS $db_identifier 상태가 $db_status 다. 쓸 수 있는 상태($DB_USABLE_STATUSES)가 되면 다시 실행한다"
		;;
	esac
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

# 로그인 화면 리다이렉트를 따라간 마지막 응답을 "코드 URL"로 돌려준다. 연결하지 못하면 코드는 000이다.
usage_final_response() {
	local domain="$1"
	curl -s -L --max-redirs "$USAGE_MAX_REDIRECTS" -o /dev/null -w '%{http_code} %{url_effective}' --max-time "$USAGE_REQUEST_TIMEOUT_SECONDS" "https://$domain/usage" || true
}

wait_for_usage_ok() {
	local domain="$1" failure_hint="$2" attempt response="" code=""
	for attempt in $(seq 1 "$USAGE_RETRY_LIMIT"); do
		response=$(usage_final_response "$domain")
		code=${response%% *}
		if [ "$code" = "$HTTP_OK" ]; then
			log "https://$domain/usage -> ${response#* } 가 $HTTP_OK 을 돌려줬다(${attempt}회 시도)"
			return 0
		fi
		sleep "$USAGE_RETRY_INTERVAL_SECONDS"
	done
	die "https://$domain/usage 가 리다이렉트를 따라가도 $((USAGE_RETRY_LIMIT * USAGE_RETRY_INTERVAL_SECONDS))초 안에 $HTTP_OK 이 되지 않았다(마지막 응답: $response). $failure_hint"
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

ssm_invocation_json() {
	local command_id="$1" instance_id="$2"
	aws ssm get-command-invocation \
		--command-id "$command_id" \
		--instance-id "$instance_id" \
		--output json
}

# 끝난 명령의 상태를 표준 출력으로 돌려준다.
wait_ssm_command() {
	local command_id="$1" instance_id="$2"
	local deadline=$(($(date +%s) + SSM_COMMAND_WAIT_LIMIT_SECONDS))
	local poll_errors=0 invocation status
	while :; do
		# 보낸 직후에는 InvocationDoesNotExist가 오므로 연속 실패만 센다.
		if invocation=$(ssm_invocation_json "$command_id" "$instance_id" 2>/dev/null); then
			poll_errors=0
			status=$(printf '%s' "$invocation" | jq -r '.Status')
			case "$status" in
			Pending | InProgress | Delayed | Cancelling) ;;
			*)
				printf '%s' "$status"
				return 0
				;;
			esac
		else
			poll_errors=$((poll_errors + 1))
			[ "$poll_errors" -lt "$SSM_MAX_CONSECUTIVE_POLL_ERRORS" ] ||
				die "명령 $command_id 의 상태를 연속 ${poll_errors}회 읽지 못했다"
		fi
		[ "$(date +%s)" -lt "$deadline" ] ||
			die "명령 $command_id 이 ${SSM_COMMAND_WAIT_LIMIT_SECONDS}초 안에 끝나지 않았다"
		sleep "$SSM_POLL_INTERVAL_SECONDS"
	done
}

# get-command-invocation은 출력을 24000자까지만 준다.
print_ssm_output() {
	local command_id="$1" instance_id="$2" invocation stdout_content stderr_content
	invocation=$(ssm_invocation_json "$command_id" "$instance_id") ||
		die "명령 $command_id 의 출력을 읽지 못했다"
	stdout_content=$(printf '%s' "$invocation" | jq -r '.StandardOutputContent // ""')
	stderr_content=$(printf '%s' "$invocation" | jq -r '.StandardErrorContent // ""')
	[ -z "$stdout_content" ] || printf '%s\n' "$stdout_content"
	[ -z "$stderr_content" ] || printf '%s\n' "$stderr_content" >&2
}

send_shell_command() {
	local instance_id="$1" comment="$2" command="$3"
	local parameters command_id status
	need_cmd aws jq
	parameters=$(jq -n --arg command "$command" --arg timeout "$SSM_EXECUTION_TIMEOUT_SECONDS" \
		'{commands: [$command], executionTimeout: [$timeout]}') ||
		die "send-command 파라미터를 만들지 못했다"

	command_id=$(aws ssm send-command \
		--instance-ids "$instance_id" \
		--document-name AWS-RunShellScript \
		--comment "${comment:0:$SSM_COMMENT_MAX_LENGTH}" \
		--timeout-seconds "$SSM_DELIVERY_TIMEOUT_SECONDS" \
		--parameters "$parameters" \
		--query 'Command.CommandId' \
		--output text) || die "$instance_id 에 명령을 보내지 못했다"
	log "SSM 명령 $command_id 을 $instance_id 에 보냈다. 끝날 때까지 기다린다"

	status=$(wait_ssm_command "$command_id" "$instance_id") || exit 1
	print_ssm_output "$command_id" "$instance_id"
	[ "$status" = Success ] || die "원격 명령이 $status 로 끝났다(명령 ID $command_id)"
}

run_remote_command() {
	local instance_id="$1" comment="$2" command="$3"
	[ -n "$command" ] || die "run_remote_command에 실행할 명령이 없다"
	send_shell_command "$instance_id" "$comment" "$command"
}

# 로컬 스크립트 파일 내용을 원격 bash의 표준 입력으로 보낸다. 서버의 레포 사본에 의존하지 않는다.
run_remote_script() {
	local instance_id="$1" comment="$2" script_file="$3"
	shift 3
	[ -r "$script_file" ] || die "보낼 스크립트 $script_file 을 읽지 못했다"
	if grep -Fq "$REMOTE_SCRIPT_DELIMITER" "$script_file"; then
		die "$script_file 에 구분자 $REMOTE_SCRIPT_DELIMITER 가 들어 있다"
	fi

	local arg args=""
	for arg in "$@"; do
		require_shell_safe "원격 스크립트 인자" "$arg"
		args="$args $arg"
	done

	local content command
	content=$(cat "$script_file")
	command=$(printf "bash -s --%s <<'%s'\n%s\n%s" "$args" "$REMOTE_SCRIPT_DELIMITER" "$content" "$REMOTE_SCRIPT_DELIMITER")
	send_shell_command "$instance_id" "$comment" "$command"
}
