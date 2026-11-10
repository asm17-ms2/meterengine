#!/usr/bin/env bash
# sut/scripts의 스크립트가 common.sh 다음에 source 한다. 첫 인자로 받은 사양의 Terraform 루트와 환경 파일을 고른다.

TARGET_SUT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
REPO_ROOT=$(cd "$TARGET_SUT_DIR/../../.." && pwd)

# shellcheck disable=SC2034 # 고른 값은 부르는 스크립트와 common.sh가 쓴다.
select_target() {
	local spec="$1"
	case "$spec" in
	m7g | t4g) ;;
	*) die "첫 인자는 m7g나 t4g다: $spec" ;;
	esac
	SPEC="$spec"
	TF_DIR="$TARGET_SUT_DIR/$spec"
	ENVIRONMENT="test-sjpark-$spec"
	ENVIRONMENT_FILE="$REPO_ROOT/deploy/environments/$ENVIRONMENT.json"
}
