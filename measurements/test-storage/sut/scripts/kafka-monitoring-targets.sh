#!/usr/bin/env bash
# 공용 모니터링 서버의 Prometheus가 이 환경의 MSK 브로커를 긁게 대상 파일을 쓴다. 여러 번 돌려도 된다.
# 브로커 주소는 클러스터를 만들 때마다 바뀌므로, 카프카를 켠 apply 뒤에 register를, 끄기 전이나 뒤에 clear를 부른다.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TF_DIR=$(cd "$SCRIPT_DIR/../terraform" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"

readonly REMOTE_SCRIPT_FILE="$SCRIPT_DIR/remote/write-prometheus-targets.sh"
readonly SUT_NAME=test-storage

monitoring_instance_id() {
	local instance_id
	instance_id=$(aws ec2 describe-instances \
		--filters Name=tag:Role,Values=monitoring Name=instance-state-name,Values=running \
		--query 'Reservations[].Instances[].InstanceId' --output text) || die "공용 모니터링 서버를 찾지 못했다"
	[ -n "$instance_id" ] && [ "$instance_id" != None ] || die "실행 중인 공용 모니터링 서버가 없다"
	[ "$(wc -w <<<"$instance_id")" -eq 1 ] || die "공용 모니터링 서버가 여러 대로 보인다: $instance_id"
	printf '%s\n' "$instance_id"
}

# "b-1.example:9094,b-2.example:9094" 에서 호스트만 한 줄씩 낸다.
broker_hosts() {
	local brokers
	brokers=$(terraform -chdir="$TF_DIR" output -raw kafka_bootstrap_brokers 2>/dev/null) ||
		die "브로커 주소가 없다. -var kafka_enabled=true 로 apply 한 뒤에 부른다"
	[ -n "$brokers" ] || die "브로커 주소가 비어 있다. -var kafka_enabled=true 로 apply 한 뒤에 부른다"
	tr ',' '\n' <<<"$brokers" | sed 's/:[0-9]*$//'
}

main() {
	need_cmd aws terraform jq
	[ $# -eq 1 ] || die "사용법: $0 register|clear"

	local hosts="" instance_id
	case "$1" in
	register) hosts=$(broker_hosts) || exit 1 ;;
	clear) ;;
	*) die "사용법: $0 register|clear" ;;
	esac

	instance_id=$(monitoring_instance_id) || exit 1
	wait_ssm_online "$instance_id"
	# shellcheck disable=SC2086
	run_remote_script "$instance_id" "kafka-monitoring-targets $1" "$REMOTE_SCRIPT_FILE" "$SUT_NAME" $hosts
	log "끝났다. Prometheus가 대상 파일을 다시 읽는 데 몇 초 걸린다"
}

main "$@"
