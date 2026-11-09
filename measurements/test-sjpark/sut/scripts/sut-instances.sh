#!/usr/bin/env bash
# 앱 서버 오토스케일링 그룹에서 일하는 서버 ID를 한 줄에 하나씩 찍는다. 서버 접속과 포트 포워딩의 --target에 쓴다.
# 사용법: sut-instances.sh <m7g|t4g>

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"
. "$SCRIPT_DIR/target.sh"
select_target "${1:-}"
shift

main() {
	need_cmd aws terraform

	local asg_name
	asg_name=$(tf_out asg_name)
	require_in_service_instances "$asg_name"
}

main "$@"
