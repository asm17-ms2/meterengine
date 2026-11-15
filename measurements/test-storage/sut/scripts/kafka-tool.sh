#!/usr/bin/env bash
# 일하는 SUT 서버 하나에서 카프카 명령줄 도구를 돌려 결과를 찍는다. 읽기만 한다.
#   lag     컨슈머 그룹이 파티션마다 어디까지 읽었고 얼마나 밀렸는지
#   topics  토픽의 파티션, 리더, 복제본, 설정
#   dlq N   DLQ 토픽의 앞에서부터 N건(이유는 failure_reason 헤더)

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TF_DIR=$(cd "$SCRIPT_DIR/../terraform" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"

readonly REMOTE_SCRIPT_FILE="$SCRIPT_DIR/remote/kafka-tool.sh"

# 컨슈머 서버가 있으면 거기서, 없으면 수집 서버에서 돌린다. 둘 다 브로커에 붙을 수 있다.
sut_instance_id() {
	local instances
	instances=$(asg_in_service_instances "$(tf_out consumer_asg_name)") || exit 1
	[ -n "$instances" ] || instances=$(require_in_service_instances "$(tf_out asg_name)") || exit 1
	printf '%s\n' "$instances" | head -n 1
}

main() {
	need_cmd aws terraform jq
	case "${1:-}" in
	lag | topics) [ $# -eq 1 ] || die "사용법: $0 lag|topics|dlq <건수>" ;;
	dlq) [ $# -eq 2 ] || die "사용법: $0 dlq <건수>" ;;
	*) die "사용법: $0 lag|topics|dlq <건수>" ;;
	esac

	local instance_id
	instance_id=$(sut_instance_id) || exit 1
	wait_ssm_online "$instance_id"
	run_remote_script "$instance_id" "kafka-tool $1" "$REMOTE_SCRIPT_FILE" "$@"
}

main "$@"
