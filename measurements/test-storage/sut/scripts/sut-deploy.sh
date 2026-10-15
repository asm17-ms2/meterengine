#!/usr/bin/env bash
# SUT에 커밋 하나를 배포한다. 사용법: sut-deploy.sh [SHA]  (없으면 ECR에 이미지가 있는 가장 최근 main 커밋)

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
TF_DIR=$(cd "$SCRIPT_DIR/../terraform" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"

readonly REPO_URL=https://github.com/asm17-ms2/meterengine.git
readonly BACKEND_ECR_REPOSITORY=meterengine-backend
readonly FRONTEND_ECR_REPOSITORY=meterengine-frontend
readonly ECR_REPOSITORIES="$BACKEND_ECR_REPOSITORY $FRONTEND_ECR_REPOSITORY"
readonly FULL_SHA_PATTERN='^[0-9a-f]{40}$'
readonly REMOTE_REPO_DIR=/opt/meterengine
readonly REMOTE_DEPLOY_CONFIG=/etc/meterengine/deploy.conf
# 이 커밋보다 앞선 deploy.sh는 deploy.conf를 읽지 않아 개발 서버 파라미터 경로와 도메인을 쓴다.
readonly FIRST_SHA_WITH_DEPLOY_CONF=a4bdf42e963df521514bf54283393f217c0d93cf
readonly DEPLOY_DB_LINE_PREFIX='[deploy] DB: '

deploy_output_file=""

require_full_sha() {
	local sha="$1"
	[[ "$sha" =~ $FULL_SHA_PATTERN ]] || die "SHA는 소문자 16진수 40자여야 한다: $sha"
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

# CD는 main 커밋 SHA로만 태그를 올리므로, 두 레포에 다 있는 가장 최근 태그가 배포할 수 있는 가장 최근 main 커밋이다.
latest_deployable_sha() {
	local backend_tags frontend_tags sha
	backend_tags=$(ecr_sha_tags_newest_first "$BACKEND_ECR_REPOSITORY") || exit 1
	frontend_tags=$(ecr_sha_tags_newest_first "$FRONTEND_ECR_REPOSITORY") || exit 1
	for sha in $backend_tags; do
		if printf '%s\n' "$frontend_tags" | grep -Fxq "$sha"; then
			printf '%s' "$sha"
			return 0
		fi
	done
	die "ECR $ECR_REPOSITORIES 둘 다에 있는 커밋 SHA 태그가 없다. SHA를 직접 준다"
}

log_when_behind_main() {
	local sha="$1" main_sha
	main_sha=$(git ls-remote "$REPO_URL" refs/heads/main | cut -f1) || main_sha=""
	if [ -z "$main_sha" ]; then
		warn "main 최신 커밋을 읽지 못해 비교를 건너뛴다"
		return 0
	fi
	[ "$main_sha" = "$sha" ] ||
		log "main 최신 $main_sha 는 아직 이미지가 없다(CD가 도는 중이거나 CD가 이미지를 올리지 않는 커밋). 이미지가 있는 가장 최근 커밋 $sha 를 쓴다"
}

require_ecr_images() {
	local sha="$1" repository aws_error
	for repository in $ECR_REPOSITORIES; do
		if ! aws_error=$(aws ecr describe-images \
			--repository-name "$repository" \
			--image-ids "imageTag=$sha" 2>&1 >/dev/null); then
			case "$aws_error" in
			*ImageNotFoundException*)
				die "ECR $repository 에 태그 $sha 이미지가 없다. CD가 아직 도는 중이거나, CD가 이미지를 올리지 않는 커밋이거나, ECR 수명 주기 정책으로 이미 지워진 커밋이다. SHA를 빼면 이미지가 있는 가장 최근 커밋을 쓴다. 보존 범위는 measurements/test-storage/README.md \"다시 배포\"에 있다"
				;;
			*)
				die "ECR $repository 이미지를 확인하지 못했다: $aws_error"
				;;
			esac
		fi
	done
	log "ECR 이미지 확인: $ECR_REPOSITORIES"
}

# 도메인이 이 서버를 가리키지 않으면 Caddy 인증서 발급이 실패한다.
require_dns_points_to() {
	local domain="$1" expected_ip="$2" resolved
	resolved=$(dig +short A "$domain" || true)
	if ! printf '%s\n' "$resolved" | grep -Fxq "$expected_ip"; then
		die "$domain 이 $expected_ip 로 풀리지 않는다(지금 값: ${resolved:-없음}). Caddy 인증서 발급이 실패하므로 DNS가 맞춰진 뒤 다시 실행한다"
	fi
	log "DNS 확인: $domain -> $expected_ip"
}

# CD처럼 대상 커밋으로 먼저 checkout해 그 커밋의 deploy.sh를 돌린다. 하한 확인은 checkout 전에 해야 옛 Caddyfile이 깔리지 않는다.
deploy_command() {
	local sha="$1" param_path="$2" domain="$3"
	printf '%s' "grep -qx 'PARAM_PATH=$param_path' $REMOTE_DEPLOY_CONFIG && grep -qx 'SITE_DOMAIN=$domain' $REMOTE_DEPLOY_CONFIG || { echo '$REMOTE_DEPLOY_CONFIG 의 PARAM_PATH나 SITE_DOMAIN이 SUT 값이 아니다' >&2; exit 1; }
cd $REMOTE_REPO_DIR || exit 1
git fetch --quiet origin || exit 1
git merge-base --is-ancestor $FIRST_SHA_WITH_DEPLOY_CONF $sha || { echo '$sha 는 deploy.conf를 읽기 전 커밋(${FIRST_SHA_WITH_DEPLOY_CONF:0:7} 이전)이거나 원격에 없는 커밋이라 배포하지 않는다' >&2; exit 1; }
git checkout --quiet --force $sha || exit 1
./deploy/deploy.sh $sha"
}

deployed_db_host() {
	local output_file="$1"
	awk -v prefix="$DEPLOY_DB_LINE_PREFIX" 'index($0, prefix) == 1 { host = substr($0, length(prefix) + 1) } END { print host }' "$output_file"
}

require_sut_db() {
	local output_file="$1" db_address="$2" db_host
	db_host=$(deployed_db_host "$output_file")
	[ -n "$db_host" ] || die "배포 출력에 '$DEPLOY_DB_LINE_PREFIX' 줄이 없다"
	[ "$db_host" = "$db_address" ] ||
		die "배포한 DB($db_host)가 SUT DB($db_address)가 아니다. 개발 서버 DB에 붙었을 수 있다. 바로 확인한다"
	log "DB 확인: $db_address"
}

main() {
	need_cmd aws terraform git curl jq dig

	local sha="${1:-}"
	if [ -z "$sha" ]; then
		sha=$(latest_deployable_sha) || exit 1
		log "SHA를 주지 않아 ECR에 이미지가 있는 가장 최근 커밋을 쓴다: $sha"
		log_when_behind_main "$sha"
	fi
	require_full_sha "$sha"

	local domain param_path sut_public_ip sut_instance_id db_identifier db_address
	domain=$(tf_out domain)
	param_path=$(tf_out param_path)
	sut_public_ip=$(tf_out sut_public_ip)
	sut_instance_id=$(tf_out sut_instance_id)
	db_identifier=$(tf_out db_identifier)
	db_address=$(tf_out db_address)
	require_shell_safe domain "$domain"
	require_shell_safe param_path "$param_path"
	require_not_dev_server_path "$param_path"

	require_ecr_images "$sha"
	require_dns_points_to "$domain" "$sut_public_ip"
	require_db_usable "$db_identifier"
	require_ec2_started "$sut_instance_id"
	wait_ssm_online "$sut_instance_id"

	deploy_output_file=$(mktemp)
	trap 'rm -f "$deploy_output_file"' EXIT

	log "SUT $sut_instance_id 에 $sha 를 배포한다"
	if ! run_remote_command "$sut_instance_id" "sut-deploy ${sha:0:7}" \
		"$(deploy_command "$sha" "$param_path" "$domain")" | tee "$deploy_output_file"; then
		[ -z "$(deployed_db_host "$deploy_output_file")" ] || require_sut_db "$deploy_output_file" "$db_address"
		die "배포가 실패했다. 위 출력을 확인한다"
	fi
	require_sut_db "$deploy_output_file" "$db_address"
	wait_for_usage_ok "$domain" "첫 배포라면 Caddy가 인증서를 받는 중이거나 받지 못했을 수 있다. SUT에 들어가 caddy 컨테이너 로그에서 인증서 오류를 보고, $domain 이 $sut_public_ip 로 풀리는지 다시 확인한다"

	log "배포한 SHA: $sha"
}

main "$@"
