#!/usr/bin/env bash
# 버전 파라미터에 커밋 SHA를 쓰고 앱 서버를 그 커밋으로 바꾼다. staging, production과 같은 deploy/rollout.sh를 쓴다.
# 사용법: sut-deploy.sh <m7g|t4g> [SHA]  (없으면 측정 ECR에 이미지가 있는 가장 최근 develop 커밋)
# 측정 ECR 이미지는 .github/workflows/deploy-test-sjpark.yml이 올린다.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"
. "$SCRIPT_DIR/target.sh"
select_target "${1:-}"
shift

readonly REPO_URL=https://github.com/asm17-ms2/meterengine.git
readonly FULL_SHA_PATTERN='^[0-9a-f]{40}$'
readonly ROLLOUT_SCRIPT="$REPO_ROOT/deploy/rollout.sh"
# 이 파일이 없는 커밋은 Caddy와 블루그린으로 뜨는 옛 배포라 부팅 스크립트의 deploy.sh 호출로 뜨지 못한다.
readonly ROLLOUT_PATH=deploy/rollout.sh

require_full_sha() {
	local sha="$1"
	[[ "$sha" =~ $FULL_SHA_PATTERN ]] || die "SHA는 소문자 16진수 40자여야 한다: $sha"
}

# 서버의 deploy.sh가 이 환경 파일의 접두로 이미지를 받으므로 같은 저장소를 확인한다.
ecr_repositories() {
	local prefix
	prefix=$(jq -er .image_repository_prefix "$ENVIRONMENT_FILE") ||
		die "$ENVIRONMENT_FILE 에서 image_repository_prefix를 읽지 못했다"
	printf '%s %s' "$prefix-backend" "$prefix-frontend"
}

ecr_sha_tags_newest_first() {
	local repository="$1" tags
	tags=$(aws ecr describe-images \
		--repository-name "$repository" \
		--filter tagStatus=TAGGED \
		--query 'reverse(sort_by(imageDetails,&imagePushedAt))[].imageTags[]' \
		--output text) || die "ECR $repository 이미지 목록을 읽지 못했다"
	printf '%s\n' "$tags" | tr '\t' '\n' | grep -E "$FULL_SHA_PATTERN" || true
}

fetch_operator_checkout() {
	git -C "$REPO_ROOT" fetch --quiet origin || die "운영자 체크아웃에서 git fetch origin이 실패했다"
}

# 서버의 deploy.sh는 배포 커밋의 환경 파일에서 접두를 읽으므로, 운영자 체크아웃의 접두와 같아야 확인한 ECR 이미지를 받는다.
uses_current_repositories() {
	local sha="$1" current deployed
	current=$(jq -er .image_repository_prefix "$ENVIRONMENT_FILE") || return 1
	deployed=$(git -C "$REPO_ROOT" show "$sha:deploy/environments/$ENVIRONMENT.json" 2>/dev/null |
		jq -er .image_repository_prefix 2>/dev/null) || return 1
	[ "$current" = "$deployed" ]
}

# 이 환경에 배포할 수 없는 커밋이면 이유를 찍는다. 버전 파라미터를 쓰기 전에 운영자 체크아웃에서 본다.
commit_problem() {
	local sha="$1"
	if ! git -C "$REPO_ROOT" cat-file -e "$sha^{commit}" 2>/dev/null; then
		echo "운영자 체크아웃에 없는 커밋이다"
	elif ! git -C "$REPO_ROOT" cat-file -e "$sha:$ROLLOUT_PATH" 2>/dev/null; then
		echo "$ROLLOUT_PATH 가 없는 옛 커밋이라 오토스케일링 그룹 서버로 뜨지 못한다"
	elif ! git -C "$REPO_ROOT" cat-file -e "$sha:deploy/environments/$ENVIRONMENT.json" 2>/dev/null; then
		echo "deploy/environments/$ENVIRONMENT.json 이 없는 커밋이라 서버의 deploy.sh가 이 환경을 배포하지 못한다"
	elif ! git -C "$REPO_ROOT" show "$sha:deploy/compose.prod.yml" 2>/dev/null | grep -q 'WEBHOOK_ENABLED:'; then
		echo "deploy/compose.prod.yml 에 WEBHOOK_ENABLED 가 없는 커밋이라 웹훅을 끌 수 없다"
	elif ! uses_current_repositories "$sha"; then
		echo "deploy/environments/$ENVIRONMENT.json 의 image_repository_prefix 가 지금과 다른 옛 커밋이라 서버가 다른 ECR에서 이미지를 찾는다. develop 위로 올린다"
	fi
}

require_deployable_commit() {
	local sha="$1" problem
	problem=$(commit_problem "$sha")
	[ -z "$problem" ] || die "$sha 는 $problem"
}

# 측정 ECR에는 실험 브랜치 이미지도 있어 develop에 들어간 커밋만 본다. 두 저장소에 다 있는 태그를 최근부터 보며 배포할 수 있는 첫 커밋을 고른다.
latest_deployable_sha() {
	local backend_repository="$1" frontend_repository="$2" backend_tags frontend_tags sha problem
	backend_tags=$(ecr_sha_tags_newest_first "$backend_repository") || exit 1
	frontend_tags=$(ecr_sha_tags_newest_first "$frontend_repository") || exit 1
	for sha in $backend_tags; do
		printf '%s\n' "$frontend_tags" | grep -Fxq "$sha" || continue
		git -C "$REPO_ROOT" merge-base --is-ancestor "$sha" origin/develop 2>/dev/null || continue
		problem=$(commit_problem "$sha")
		if [ -z "$problem" ]; then
			printf '%s' "$sha"
			return 0
		fi
		log "$sha 건너뜀: $problem" >&2
	done
	die "ECR $backend_repository, $frontend_repository 둘 다에 이미지가 있는 배포할 수 있는 develop 커밋이 없다. 배포 워크플로로 develop을 빌드하거나 SHA를 직접 준다"
}

log_when_behind_develop() {
	local sha="$1" develop_sha
	develop_sha=$(git ls-remote "$REPO_URL" refs/heads/develop | cut -f1) || develop_sha=""
	if [ -z "$develop_sha" ]; then
		warn "develop 최신 커밋을 읽지 못해 비교를 건너뛴다"
		return 0
	fi
	[ "$develop_sha" = "$sha" ] ||
		log "develop 최신 $develop_sha 는 측정 ECR에 이미지가 없다. 이미지가 있는 가장 최근 develop 커밋 $sha 를 쓴다. 최신을 재려면 배포 워크플로로 develop을 빌드한다"
}

require_ecr_images() {
	local sha="$1" repository aws_error
	shift
	for repository in "$@"; do
		if ! aws_error=$(aws ecr describe-images \
			--repository-name "$repository" \
			--image-ids "imageTag=$sha" 2>&1 >/dev/null); then
			case "$aws_error" in
			*ImageNotFoundException*)
				die "ECR $repository 에 태그 $sha 이미지가 없다. 배포 워크플로로 빌드하지 않았거나 ECR 수명 주기 정책으로 이미 지워진 커밋이다. 배포 워크플로가 빌드하고 배포까지 한다"
				;;
			*)
				die "ECR $repository 이미지를 확인하지 못했다: $aws_error"
				;;
			esac
		fi
	done
	log "ECR 이미지 확인: $*"
}

# CD와 같은 인자 순서로 운영자 체크아웃의 rollout.sh를 부른다. 결과 두 줄은 표준 출력으로 그대로 넘긴다.
rollout() {
	local sha="$1" asg_name version_parameter
	asg_name=$(tf_out asg_name)
	version_parameter=$(tf_out app_version_parameter_name)
	require_shell_safe version_parameter "$version_parameter"
	require_not_dev_server_path "$version_parameter"
	"$ROLLOUT_SCRIPT" "$asg_name" "$version_parameter" "$sha"
}

main() {
	need_cmd aws terraform git curl jq

	local sha="${1:-}" repositories
	repositories=$(ecr_repositories) || exit 1
	fetch_operator_checkout
	if [ -z "$sha" ]; then
		# shellcheck disable=SC2086 # 저장소 이름 두 개로 나눈다.
		sha=$(latest_deployable_sha $repositories) || exit 1
		log "SHA를 주지 않아 ECR에 이미지가 있는 가장 최근 커밋을 쓴다: $sha"
		log_when_behind_develop "$sha"
	fi
	require_full_sha "$sha"
	require_deployable_commit "$sha"

	local domain db_identifier result
	domain=$(tf_out domain)
	db_identifier=$(tf_out db_identifier)
	# shellcheck disable=SC2086 # 저장소 이름 두 개로 나눈다.
	require_ecr_images "$sha" $repositories
	require_db_usable "$db_identifier"

	log "$sha 를 버전 파라미터에 쓰고 앱 서버를 바꾼다. 서버의 deploy.sh가 오토스케일링 그룹 이름과 DB 식별자를 deploy/environments/$ENVIRONMENT.json과 대조한다"
	result=$(rollout "$sha") || die "rollout.sh가 실패했다. 위 출력을 확인한다"
	if printf '%s\n' "$result" | grep -Fxq recorded_only=true; then
		log "앱 서버가 0대라 버전만 기록했다. terraform -chdir=measurements/test-sjpark/sut/$SPEC apply -var asg_min_size=1 -var asg_max_size=1 로 띄우면 이 커밋으로 부팅한다"
	else
		wait_for_usage_ok "$domain" "ALB 대상 그룹 상태와 서버의 /var/log/cloud-init-output.log 를 본다"
	fi
	log "배포한 SHA: $sha"
}

main "$@"
