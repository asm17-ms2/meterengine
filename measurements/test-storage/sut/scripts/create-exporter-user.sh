#!/usr/bin/env bash
# 일하는 앱 서버 하나에 remote/upsert-exporter-user.sh 를 보내 SUT RDS에 postgres exporter 사용자를 만든다. 여러 번 돌려도 된다.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TF_DIR=$(cd "$SCRIPT_DIR/../terraform" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"

readonly REMOTE_SCRIPT_FILE="$SCRIPT_DIR/remote/upsert-exporter-user.sh"

main() {
	need_cmd aws terraform jq

	local asg_name param_path db_identifier instances sut_instance_id
	asg_name=$(tf_out asg_name)
	param_path=$(tf_out param_path)
	db_identifier=$(tf_out db_identifier)
	require_shell_safe param_path "$param_path"
	require_not_dev_server_path "$param_path"

	require_db_usable "$db_identifier"
	instances=$(require_in_service_instances "$asg_name") || exit 1
	sut_instance_id=$(printf '%s\n' "$instances" | head -n 1)
	wait_ssm_online "$sut_instance_id"
	log "SUT $sut_instance_id 에서 $param_path 값으로 exporter 사용자를 맞춘다"
	run_remote_script "$sut_instance_id" "create-exporter-user" "$REMOTE_SCRIPT_FILE" "$param_path"
	log "끝났다"
}

main "$@"
