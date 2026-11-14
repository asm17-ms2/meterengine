#!/usr/bin/env bash
# 잴 커밋의 backend, frontend 이미지를 이 컴퓨터에서 빌드해 이 환경의 ECR에 올린다. 이미 있는 이미지는 건너뛴다.
# 이 환경에는 CD가 없어, 머지 전 브랜치를 재려면 sut-deploy.sh 전에 한 번 부른다.
# 사용법: push-images.sh <SHA 40자>

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../../../.." && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"

readonly ENVIRONMENT_PATH=deploy/environments/test-storage.json
readonly OWN_REPOSITORY_PREFIX=meterengine-test-storage
readonly FULL_SHA_PATTERN='^[0-9a-f]{40}$'
# 서버가 arm64라 다른 아키텍처로 구운 이미지는 실행되지 않는다.
readonly IMAGE_PLATFORM=linux/arm64
readonly COMPONENTS="backend frontend"

source_dir=""

image_exists() {
	aws ecr describe-images --repository-name "$1" --image-ids imageTag="$2" >/dev/null 2>&1
}

# 옛 커밋의 환경 파일은 staging이나 production 저장소를 가리킨다. 거기에 올린 브랜치 이미지는 그 환경에 배포될 수 있다.
require_own_repository_prefix() {
	local sha="$1" prefix="$2"
	[ "$prefix" = "$OWN_REPOSITORY_PREFIX" ] ||
		die "커밋 $sha 의 $ENVIRONMENT_PATH 는 image_repository_prefix가 $prefix 다. $OWN_REPOSITORY_PREFIX 가 아니면 다른 환경의 저장소라 올리지 않는다. 이 환경의 저장소를 쓰는 커밋 위로 브랜치를 올린다"
}

main() {
	need_cmd aws git docker jq tar
	[ $# -eq 1 ] && [[ "$1" =~ $FULL_SHA_PATTERN ]] || die "사용법: $0 <SHA 40자>"
	local sha="$1" prefix account_id registry component repository

	git -C "$REPO_ROOT" cat-file -e "$sha^{commit}" 2>/dev/null || die "이 저장소에 커밋 $sha 가 없다"
	# 서버는 부팅하며 GitHub에서 이 커밋을 받는다. 올라가 있지 않으면 이미지가 있어도 뜨지 못한다.
	git -C "$REPO_ROOT" fetch --quiet origin || die "git fetch origin이 실패했다"
	[ -n "$(git -C "$REPO_ROOT" branch -r --contains "$sha")" ] ||
		die "커밋 $sha 가 origin에 없다. 브랜치를 먼저 push 한다"

	prefix=$(git -C "$REPO_ROOT" show "$sha:$ENVIRONMENT_PATH" | jq -er .image_repository_prefix) ||
		die "커밋 $sha 의 $ENVIRONMENT_PATH 에서 image_repository_prefix를 읽지 못했다"
	require_own_repository_prefix "$sha" "$prefix"
	account_id=$(aws sts get-caller-identity --query Account --output text) || die "AWS 자격을 확인하지 못했다"
	registry="$account_id.dkr.ecr.$AWS_REGION.amazonaws.com"

	# 작업 트리가 아니라 그 커밋의 파일로 구워야 태그와 내용이 같다.
	source_dir=$(mktemp -d)
	trap 'rm -rf "$source_dir"' EXIT
	git -C "$REPO_ROOT" archive "$sha" $COMPONENTS | tar -x -C "$source_dir"

	aws ecr get-login-password | docker login --username AWS --password-stdin "$registry" >/dev/null ||
		die "ECR 로그인에 실패했다"
	for component in $COMPONENTS; do
		repository="$prefix-$component"
		if image_exists "$repository" "$sha"; then
			log "$repository:$sha 는 이미 있어 건너뛴다"
			continue
		fi
		log "$repository:$sha 를 빌드해 올린다"
		docker buildx build --platform "$IMAGE_PLATFORM" --push \
			--tag "$registry/$repository:$sha" "$source_dir/$component" ||
			die "$repository 이미지를 올리지 못했다"
	done
	log "끝났다. sut-deploy.sh $sha 로 버전을 기록한다"
}

main "$@"
