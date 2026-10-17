#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TF_DIR=$(cd "$SCRIPT_DIR/../terraform" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"

main() {
	need_cmd aws terraform

	local load_generator_instance_id
	load_generator_instance_id=$(tf_out load_generator_instance_id)

	start_ec2_instance "$load_generator_instance_id"
	wait_ssm_online "$load_generator_instance_id"

	log "부하 발생기를 켰다"
}

main "$@"
