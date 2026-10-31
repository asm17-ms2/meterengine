#!/usr/bin/env bash
# 실행 중 git checkout이 이 파일을 덮어쓰므로, 본문은 모두 main()에 두고 맨 끝에서 한 번 부른다.

set -euo pipefail
set +x
DEPLOY_CONTRACT_VERSION=1
RUNTIME_CONTRACT_VERSION=1

REPO_DIR=/opt/meterengine
ENV_FILE=/etc/meterengine/prod.env
RDS_CA_FILE=/etc/meterengine/rds-ca.pem
CONFIG_FILE=/etc/meterengine/deploy.conf
STATE_FILE=/etc/meterengine/deploy-state
DEPLOY_TIMING=off
BACKEND_MEM_LIMIT=768m
BACKEND_MAX_RAM_PERCENTAGE=50
CADDY_ACCESS_LOG=discard
# 셸에서 물려받은 값이 deploy.conf를 앞서지 않게 비운다. deploy.conf에 없으면 write_sso_file이 PARAM_PATH로 정한다.
unset CADDY_SSO_SITES

if [ -f "$CONFIG_FILE" ]; then
	. "$CONFIG_FILE"
fi

# 옛 스크립트가 FD 9와 deploy.lock을 다시 열어도 호출부의 잠금은 유지한다.
LOCK_FILE=/etc/meterengine/deploy-command.lock

export AWS_REGION
ENABLED_SERVICES=
# Caddyfile의 lb_try_duration과 같아야 한다. reload 전에 들어온 요청은 그 안에 옛 색에 넘어가거나 실패하고,
# 넘어간 요청은 옛 색의 Spring이 마치고 멈춘다.
DRAIN_SECONDS=5

# EXIT 처리기도 읽으므로 main 밖에 둔다.
compose=()
target_color=
live_color=
legacy_container=
serving=
switched=no
discard_target_on_failure=no
parameter_snapshot=
env_tmp_file=
deploy_timing_started=no

log() { printf '[deploy] %s\n' "$*"; }
die() { printf '[deploy] 실패: %s\n' "$*" >&2; exit 1; }

now_ms() { date +%s%3N; }

# 호출한 셸이 잡은 잠금을 이어받아 checkout부터 배포 끝까지 유지한다.
acquire_deploy_lock() {
	command -v flock >/dev/null || die "배포 잠금에 필요한 flock이 없다"
	if ! [ "$LOCK_FILE" -ef /proc/self/fd/8 ]; then
		exec 8>"$LOCK_FILE"
	fi
	flock -n 8 || die "다른 배포가 아직 돌고 있다. 끝난 뒤 다시 실행한다"
}

# 서비스의 실행 중인 컨테이너 ID를 찍는다.
running_container() {
	docker ps -q \
		--filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
		--filter "label=com.docker.compose.service=$1"
}

backend_container() {
	local legacy blue green
	legacy=$(running_container backend | paste -sd+ -)
	blue=$(running_container backend-blue | paste -sd+ -)
	green=$(running_container backend-green | paste -sd+ -)
	printf 'legacy:%s,blue:%s,green:%s' "${legacy:-없음}" "${blue:-없음}" "${green:-없음}"
}

# 실행 중인 서비스 컨테이너의 이미지 이름을 찍는다.
running_image() {
	local id
	id=$(running_container "$1")
	[ -n "$id" ] || return 1
	docker inspect -f '{{.Config.Image}}' "$id"
}

# 첫 전환 배포에서는 옛 env 파일에 색 태그가 없어 compose exec가 실패하므로 docker exec를 쓴다.
in_caddy() {
	local id
	id=$(running_container caddy)
	[ -n "$id" ] || return 1
	docker exec "$id" "$@"
}

# 그 색이 DB까지 포함한 전체 헬스에서 UP인지 compose 네트워크 안에서 본다.
backend_healthy() {
	local body
	body=$(in_caddy wget -qO- -T 3 "http://backend-$1:8080/actuator/health" 2>/dev/null) &&
		grep -q '"status":"UP"' <<<"$body"
}

# 그 색의 readiness가 UP인지 본다. Caddy가 보낼 곳을 고르는 기준과 같다.
# 응답이 한 번 늦었다고 받고 있는 색을 대상으로 골라 다시 만들지 않도록 몇 번 더 묻는다.
backend_ready() {
	local i body
	for i in 1 2 3; do
		if body=$(in_caddy wget -qO- -T 3 "http://backend-$1:8080/actuator/health/readiness" 2>/dev/null) &&
			grep -q '"status":"UP"' <<<"$body"; then
			return 0
		fi
		[ "$i" -eq 3 ] || sleep 1
	done
	return 1
}

# Caddy 내부 사이트를 거친 응답을 보낸 색을 찍는다. 알 수 없으면 아무것도 찍지 않는다.
# busybox wget은 응답 헤더를 stderr로 찍는다.
routed_color() {
	local headers
	headers=$(in_caddy wget -S -qO /dev/null -T 3 http://localhost:8081/actuator/health/readiness 2>&1) || return 0
	case "$headers" in
	*"X-Upstream: backend-blue:8080"*) echo blue ;;
	*"X-Upstream: backend-green:8080"*) echo green ;;
	esac
}

routed_to() {
	[ "$(routed_color)" = "$1" ]
}

# 기록 파일의 살아 있는 색을 찍는다.
recorded_live_color() {
	[ -f "$STATE_FILE" ] || return 0
	local color
	color=$(sed -n 's/^LIVE_COLOR=//p' "$STATE_FILE" | tail -n 1)
	case "$color" in
	blue | green) printf '%s' "$color" ;;
	esac
}

# 두 색이 도는지와 readiness가 UP인지(yes, no), 둘 다 UP일 때 살릴 색으로 "대상 색 살아 있는 색"을 찍는다.
# 살아 있는 색이 없으면 대상 색만 찍는다.
choose_colors() {
	local blue_running="$1" green_running="$2" blue_up="$3" green_up="$4" keep="$5"
	if [ "$blue_running" = yes ] && [ "$green_running" = yes ]; then
		if [ "$blue_up" = yes ] && [ "$green_up" = yes ]; then
			if [ "$keep" = green ]; then
				echo "blue green"
			else
				echo "green blue"
			fi
		elif [ "$blue_up" = yes ]; then
			echo "green blue"
		elif [ "$green_up" = yes ]; then
			echo "blue green"
		else
			echo "blue"
		fi
	elif [ "$blue_running" = yes ]; then
		if [ "$blue_up" = yes ]; then
			echo "green blue"
		else
			echo "green"
		fi
	elif [ "$green_running" = yes ]; then
		if [ "$green_up" = yes ]; then
			echo "blue green"
		else
			echo "blue"
		fi
	else
		echo "blue"
	fi
}

# 대상이 아닌 색의 컨테이너가 돌고 있으면 그 색을 찍는다.
previous_running_color() {
	local target="$1" blue_running="$2" green_running="$3"
	if [ "$target" = blue ] && [ "$green_running" = yes ]; then
		echo green
	elif [ "$target" = green ] && [ "$blue_running" = yes ]; then
		echo blue
	fi
}

# 실패 정리가 필요한지 찍는다. 삭제 여부는 정리 시점의 응답으로 정한다.
target_on_exit() {
	local code="$1" discard_on_failure="$2"
	if [ "$code" -eq 0 ] || [ "$discard_on_failure" != yes ]; then
		echo none
	else
		echo discard
	fi
}

# 배포를 시작할 때 Caddy가 보낼 백엔드를 찍는다. 돌고 있는 이전 색과 블루그린 이전 backend다.
# 살아 있는 색과 블루그린 이전 backend가 모두 없으면 대상 색도 넣는다. 그때 대상 색은 실패해도 남는다.
serving_upstreams() {
	local target="$1" live="$2" previous="$3" legacy="$4" list=()
	[ -z "$previous" ] || list+=("backend-$previous:8080")
	[ -z "$legacy" ] || list+=("backend:8080")
	if [ -z "$live" ] && [ -z "$legacy" ]; then
		list+=("backend-$target:8080")
	fi
	echo "${list[*]}"
}

cleanup_upstream_ready() {
	local body
	body=$(in_caddy wget -qO- -T 3 "http://$1/actuator/health/readiness" 2>/dev/null) &&
		grep -q '"status":"UP"' <<<"$body"
}

cleanup_previous_upstreams() {
	local upstream list=()
	for upstream in $serving; do
		[ "$upstream" != "backend-$target_color:8080" ] || continue
		case "$upstream" in
		backend-blue:8080 | backend-green:8080 | backend:8080)
			if cleanup_upstream_ready "$upstream"; then list+=("$upstream"); fi
			;;
		esac
	done
	echo "${list[*]}"
}

# reload 성공만으로는 실제 요청이 돌아왔는지 알 수 없다.
cleanup_routed_to() {
	local headers upstream
	headers=$(in_caddy wget -S -qO /dev/null -T 3 http://localhost:8081/actuator/health/readiness 2>&1) || return 1
	for upstream in $1; do
		if grep -Eq "^[[:space:]]*X-Upstream: ${upstream}[[:space:]]*$" <<<"$headers" &&
			cleanup_upstream_ready "$upstream"; then
			return 0
		fi
	done
	return 1
}

keep_target() {
	log "이전 백엔드의 복귀를 확인하지 못해 새 백엔드(backend-$target_color)를 남긴다"
	if cleanup_upstream_ready "backend-$target_color:8080"; then
		if write_upstreams_file "backend-$target_color:8080" && reload_caddy &&
			cleanup_routed_to "backend-$target_color:8080"; then
			log "Caddy가 새 백엔드(backend-$target_color)로 다시 보낸다"
		else
			log "Caddy를 새 백엔드(backend-$target_color)로 복구하지 못했다. 컨테이너는 남긴다"
		fi
	fi
	return 0
}

discard_target() {
	local previous
	if ! previous=$(cleanup_previous_upstreams) || [ -z "$previous" ]; then
		keep_target
		return
	fi
	if ! { write_upstreams_file "$previous" && reload_caddy && cleanup_routed_to "$previous"; }; then
		keep_target
		return
	fi
	if [ "$switched" = yes ]; then
		log "Caddy가 이전 백엔드($previous)로 다시 보낸다"
	fi
	if ! sleep "$DRAIN_SECONDS" || ! cleanup_routed_to "$previous"; then
		keep_target
		return
	fi
	log "실패해서 새 백엔드(backend-$target_color)를 멈추고 지운다. 원래 백엔드가 계속 받는다"
	"${compose[@]}" stop "backend-$target_color" || true
	"${compose[@]}" rm -f "backend-$target_color" || true
}

on_exit() {
	local code=$?
	[ -z "${parameter_snapshot:-}" ] || rm -f "$parameter_snapshot"
	[ -z "${env_tmp_file:-}" ] || rm -f "$env_tmp_file"
	case "$(target_on_exit "$code" "$discard_target_on_failure")" in
	discard)
		discard_target || true
		;;
	esac
	if [ "$DEPLOY_TIMING" = on ] && [ "${deploy_timing_started:-no}" = yes ]; then
		log "측정 끝 $(now_ms) backend=$(backend_container) 종료=$code" || true
	fi
	exit "$code"
}

# 검증한 파라미터를 checkout 전에 한 번만 읽어 보관한다.
fetch_parameter_snapshot() {
	parameter_snapshot=$(mktemp "${TMPDIR:-/tmp}/meterengine-parameters.XXXXXX")
	chmod 600 "$parameter_snapshot"

	if [ "$environment" = staging ] || [ "$environment" = production ]; then
		[ -r "$RDS_CA_FILE" ] || die "RDS CA가 없다. rds-ca.sh install로 먼저 설치한다"
		python3 "$script_dir/runtime_parameters.py" --environment "$environment" --expected-db-host "$EXPECTED_DB_HOST" --services $ENABLED_SERVICES >"$parameter_snapshot" || die "runtime 파라미터를 읽거나 검증하지 못했다"
		printf 'DB_SSL_ROOT_CERT=%s\n' "$RDS_CA_FILE" >>"$parameter_snapshot"
		return
	fi

	aws ssm get-parameters-by-path \
		--path "$PARAM_PATH" \
		--with-decryption \
		--query 'Parameters[].[Name,Value]' \
		--output text |
		while IFS=$'\t' read -r name value; do
			[ -n "$name" ] || continue
			key=$(basename "$name" | tr 'a-z-' 'A-Z_')
			case "$value" in
			*$'\n'*) die "파라미터 $name 의 값에 개행이 있다. 값을 고쳐야 한다" ;;
			esac
			printf '%s=%s\n' "$key" "$value"
		done >>"$parameter_snapshot"

	local required="DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD CRYPTO_ENCRYPTION_KEY OPERATOR_EMAIL OPERATOR_PASSWORD" key
	if service_enabled postgres-exporter; then required="$required DB_EXPORTER_PASSWORD"; fi
	if service_enabled grafana; then required="$required GRAFANA_ADMIN_PASSWORD SLACK_WEBHOOK_URL"; fi
	if service_enabled oauth2-proxy; then required="$required OAUTH2_PROXY_CLIENT_ID OAUTH2_PROXY_CLIENT_SECRET OAUTH2_PROXY_COOKIE_SECRET"; fi
	for key in $required; do
		if ! grep -q "^$key=.*[^[:space:]]" "$parameter_snapshot"; then
			die "$key is required for enabled_services"
		fi
	done
	if [ "$(sed -n 's/^DB_HOST=//p' "$parameter_snapshot")" != "$EXPECTED_DB_HOST" ]; then
		die "db-host changed after environment preflight"
	fi
}

# 검증한 스냅샷에 이미지 참조를 더한 뒤 env 파일을 교체한다.
write_env_file() {
	local image_tag="$1" blue_tag="$2" green_tag="$3" blue_image="$4" green_image="$5" frontend_image="$6"
	if grep -q '^METERENGINE_DEPLOYMENT_ENVIRONMENT=' "$parameter_snapshot"; then
		die "deployment_environment must come from the verified manifest"
	fi
	mkdir -p "$(dirname "$ENV_FILE")"
	env_tmp_file=$(mktemp "$ENV_FILE.XXXXXX")
	chmod 600 "$env_tmp_file"
	cat "$parameter_snapshot" >"$env_tmp_file"
	if [ "$environment" = staging ] || [ "$environment" = production ]; then
		local parameter_name parameter_value
		while IFS='=' read -r parameter_name parameter_value; do
			export -n "$parameter_name"
		done <"$parameter_snapshot"
	fi

	{
		printf 'METERENGINE_DEPLOYMENT_ENVIRONMENT=%s\n' "$METERENGINE_DEPLOYMENT_ENVIRONMENT"
		printf 'IMAGE_TAG=%s\n' "$image_tag"
		printf 'BACKEND_BLUE_TAG=%s\n' "$blue_tag"
		printf 'BACKEND_GREEN_TAG=%s\n' "$green_tag"
		printf 'BACKEND_BLUE_IMAGE=%s\n' "$blue_image"
		printf 'BACKEND_GREEN_IMAGE=%s\n' "$green_image"
		printf 'FRONTEND_IMAGE=%s\n' "$frontend_image"
		printf 'ECR_REGISTRY=%s\n' "$ECR_REGISTRY"
		printf 'SITE_DOMAIN=%s\n' "$SITE_DOMAIN"
		printf 'SITE_ALIASES=%s\n' "$SITE_ALIASES"
		printf 'BACKEND_MEM_LIMIT=%s\n' "$BACKEND_MEM_LIMIT"
		printf 'BACKEND_MAX_RAM_PERCENTAGE=%s\n' "$BACKEND_MAX_RAM_PERCENTAGE"
	} >>"$env_tmp_file"
	export -n METERENGINE_DEPLOYMENT_ENVIRONMENT
	export -n METERENGINE_STAGING_WEBHOOK_ALLOWED_URLS

	mv "$env_tmp_file" "$ENV_FILE"
	env_tmp_file=
	rm -f "$parameter_snapshot"
	parameter_snapshot=
	chmod 600 "$ENV_FILE"

	grep -q '^DB_HOST=' "$ENV_FILE" || die "Parameter Store에서 db-host를 읽지 못했다"
}

# Caddyfile이 가져오는 요청 로그 출력을 쓴다. 값을 env로 넘기면 바뀔 때마다 Caddy 컨테이너가 다시 만들어져 끊긴다.
# Caddy가 반쯤 쓴 파일을 읽지 않도록 같은 디렉터리에 쓰고 mv한다.
write_access_log_file() {
	local file="$REPO_DIR/deploy/caddy/access-log.caddy" tmp
	[ -n "$CADDY_ACCESS_LOG" ] || die "deploy.conf의 CADDY_ACCESS_LOG가 비어 있다. 끄려면 줄을 지우거나 discard를 적는다"
	tmp=$(mktemp "$file.XXXXXX")
	printf 'output %s\n' "$CADDY_ACCESS_LOG" >"$tmp"
	mv "$tmp" "$file"
}

# 검증된 production의 SSO 서비스가 모두 선택됐을 때만 사이트를 기본 공개한다.
sso_sites_default() {
	if [ "$METERENGINE_DEPLOYMENT_ENVIRONMENT" = production ] &&
		service_enabled oauth2-proxy && service_enabled grafana && service_enabled prometheus; then
		echo on
	else
		echo off
	fi
}

sso_sites_value() {
	local value=${CADDY_SSO_SITES-$(sso_sites_default)} service
	case "$value" in
	on)
		for service in oauth2-proxy grafana prometheus; do
			service_enabled "$service" || die "CADDY_SSO_SITES=on requires $service in enabled_services"
		done
		;;
	off) ;;
	*) die "deploy.conf의 CADDY_SSO_SITES는 on이나 off여야 한다. 지금 값: '$value'" ;;
	esac
	printf '%s\n' "$value"
}

# Caddyfile이 가져오는 SSO 사이트 설정을 쓴다. deploy.conf의 CADDY_SSO_SITES가 있으면 그 값을 따른다.
# Caddy가 반쯤 쓴 파일을 읽지 않도록 같은 디렉터리에 쓰고 mv한다.
write_sso_file() {
	local file="$REPO_DIR/deploy/caddy/sso.caddy" value content tmp
	value=$(sso_sites_value) || return 1
	case "$value" in
	on) content='import sso-sites.caddy' ;;
	off) content='# CADDY_SSO_SITES=off' ;;
	*) die "deploy.conf의 CADDY_SSO_SITES는 on이나 off여야 한다. 지금 값: '$value'" ;;
	esac
	tmp=$(mktemp "$file.XXXXXX")
	printf '%s\n' "$content" >"$tmp"
	mv "$tmp" "$file"
	log "SSO 사이트: $value"
}

# Caddyfile이 가져오는 백엔드 목록을 쓴다. Caddy가 반쯤 쓴 파일을 읽지 않도록 같은 디렉터리에 쓰고 mv한다.
write_upstreams_file() {
	local file="$REPO_DIR/deploy/caddy/upstreams.caddy" tmp
	tmp=$(mktemp "$file.XXXXXX") || return 1
	printf 'to %s\n' "$*" >"$tmp" && mv "$tmp" "$file"
}

# 방금 뜬 컨테이너는 admin API가 아직 안 열렸을 수 있어 재시도한다.
reload_caddy() {
	local i
	for i in $(seq 1 10); do
		if "${compose[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null 2>&1; then
			return 0
		fi
		sleep 2
	done
	return 1
}

# 반쯤 쓴 기록 파일이 읽히지 않도록 같은 디렉터리에 쓰고 mv한다.
write_state_file() {
	local tmp
	tmp=$(mktemp "$STATE_FILE.XXXXXX")
	printf 'LIVE_COLOR=%s\nLIVE_SHA=%s\n' "$1" "$2" >"$tmp"
	if [ "${environment:-}" = staging ] || [ "${environment:-}" = production ]; then
		{
			printf 'SOURCE_REF=%s\nSOURCE_TREE=%s\nDEPLOY_RUN_ID=%s\n' "$git_ref" "$source_tree" "$DEPLOY_RUN_ID"
			printf 'BACKEND_IMAGE_DIGEST=%s\nFRONTEND_IMAGE_DIGEST=%s\n' "$BACKEND_IMAGE_DIGEST" "$FRONTEND_IMAGE_DIGEST"
			printf 'BACKEND_IMAGE_ID=%s\nFRONTEND_IMAGE_ID=%s\n' "$BACKEND_IMAGE_ID" "$FRONTEND_IMAGE_ID"
		} >>"$tmp"
	fi
	mv "$tmp" "$STATE_FILE"
}

observe_deployment() {
	local before after observations
	before=$(routed_color)
	[ "$before" = "$target_color" ] || die "관측 전 Caddy 색이 다르다"
	backend_healthy "$before" || die "관측 전 backend health가 실패했다"
	observations=$(python3 "$script_dir/observe_images.py" "$environment" --backend-service "backend-$before" --backend-digest "$BACKEND_IMAGE_DIGEST" --frontend-digest "$FRONTEND_IMAGE_DIGEST") || die "실제 이미지 관측이 실패했다"
	after=$(routed_color)
	[ "$before" = "$after" ] || die "관측 중 Caddy 색이 바뀌었다"
	[ "$(git rev-parse HEAD 8>&- 9>&-)" = "$git_ref" ] &&
		[ "$(git rev-parse 'HEAD^{tree}' 8>&- 9>&-)" = "$source_tree" ] &&
		git diff --quiet "$git_ref" -- 8>&- 9>&- || die "관측 중 source가 바뀌었다"
	eval "$observations"
}

# 명령이 성공할 때까지 정한 횟수와 간격으로 다시 시도한다.
wait_until() {
	local name="$1" tries="$2" interval="$3" i
	shift 3
	for i in $(seq 1 "$tries"); do
		if "$@"; then
			log "$name (${i}회 시도)"
			return 0
		fi
		sleep "$interval"
	done
	return 1
}

http_ok() {
	local body
	body=$(curl -fsS --max-time 3 "$1" 2>/dev/null) && grep -qi "$2" <<<"$body"
}

# 서비스가 실제로 요청을 받을 때까지 기다린다.
wait_for_http() {
	wait_until "$1 준비됨" 45 2 http_ok "$2" "$3"
}

# 방금 뜬 Prometheus는 HUP 처리기를 걸기 전이라 HUP을 놓친다.
wait_for_prometheus_ready() {
	local i
	for i in $(seq 1 45); do
		if "$@" exec -T prometheus wget -qO- http://localhost:9090/-/ready >/dev/null 2>&1; then
			return 0
		fi
		sleep 2
	done
	return 1
}

# 비밀번호는 curl 설정의 표준 입력으로 전달하고, 각 요청은 남은 대기 시간 안에 끝낸다.
reload_grafana_alerting() {
	local end=$((SECONDS + 90)) left password
	if [ "${environment:-sut}" = staging ] || [ "${environment:-sut}" = production ]; then
		password=$(python3 "$script_dir/runtime_parameters.py" read-env "$ENV_FILE" GRAFANA_ADMIN_PASSWORD) || return 1
	else
		password=$(grep '^GRAFANA_ADMIN_PASSWORD=' "$ENV_FILE" | cut -d= -f2-) || return 1
	fi
	password=$(printf '%s' "$password" | sed 's/[\\"]/\\&/g') || return 1
	while :; do
		left=$((end - SECONDS))
		[ "$left" -gt 0 ] || return 1
		[ "$left" -le 5 ] || left=5
		if printf 'user = "admin:%s"\n' "$password" |
			curl -fsS --max-time "$left" -K - -X POST http://127.0.0.1:3001/api/admin/provisioning/alerting/reload >/dev/null; then
			return 0
		fi
		[ $((end - SECONDS)) -gt 2 ] || return 1
		sleep 2
	done
}

service_enabled() {
	case " $ENABLED_SERVICES " in *" $1 "*) return 0 ;; *) return 1 ;; esac
}

verify_image_digest() {
	local repository="$1" sha="$2" expected="$3" actual
	[[ "$expected" =~ ^sha256:[0-9a-f]{64}$ ]] || die "$repository image digest가 필요하다"
	actual=$(aws ecr describe-images --repository-name "$repository" --image-ids "imageTag=$sha" --query 'imageDetails[0].imageDigest' --output text) || die "$repository image digest를 읽지 못했다"
	[ "$actual" = "$expected" ] || die "$repository SHA tag digest가 다르다"
}

deploy_main() {
	local environment="${1:-}" sha="${2:-}" git_ref="${3:-${2:-}}"
	case "$environment" in staging | production | test-slo | test-storage) ;; *) die "environment 인자가 필요하다: staging|production|test-slo|test-storage IMAGE_SOURCE_SHA [SOURCE_REF]" ;; esac
	[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || die "IMAGE_SOURCE_SHA는 소문자 git SHA 40자여야 한다"
	[[ "$git_ref" != -* ]] && [ -n "$git_ref" ] || die "SOURCE_REF가 잘못되었다"
	if [ "$environment" = staging ] || [ "$environment" = production ]; then
		[[ "${DEPLOY_RUN_ID:-}" =~ ^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$ ]] || die "DEPLOY_RUN_ID가 잘못되었다"
	fi
	if [[ "$environment" == test-* ]]; then
		[ "${DEPLOYMENT_TARGET:-}" = "$environment" ] || die "deploy.conf DEPLOYMENT_TARGET가 environment와 다르다"
	fi
	local script_dir target_values configured_path="${PARAM_PATH:-}" configured_domain="${SITE_DOMAIN:-}" configured_aliases="${SITE_ALIASES-}"
	script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
	target_values=$(python3 "$script_dir/environment.py" preflight --environment "$environment") || exit 2
	eval "$target_values"
	sso_sites_value >/dev/null || return 1
	[ -z "$configured_path" ] || [ "$configured_path" = "$PARAM_PATH" ] || die "deploy.conf PARAM_PATH가 environment와 다르다"
	[ -z "$configured_domain" ] || [ "$configured_domain" = "$SITE_DOMAIN" ] || die "deploy.conf SITE_DOMAIN이 environment와 다르다"
	[ -z "$configured_aliases" ] || [ "$configured_aliases" = "$SITE_ALIASES" ] || die "deploy.conf SITE_ALIASES가 environment와 다르다"
	if [ "$environment" = staging ] || [ "$environment" = production ]; then
		verify_image_digest meterengine-backend "$sha" "${BACKEND_IMAGE_DIGEST:-}"
		verify_image_digest meterengine-frontend "$sha" "${FRONTEND_IMAGE_DIGEST:-}"
	fi

	acquire_deploy_lock
	target_values=$(python3 "$script_dir/environment.py" preflight --environment "$environment") || exit 2
	eval "$target_values"
	cd "$REPO_DIR"
	git fetch --quiet origin 8>&- 9>&-
	git_ref=$(git rev-parse --verify "$git_ref^{commit}" 8>&- 9>&-) || die "SOURCE_REF를 찾지 못했다"
	git show "$git_ref:deploy/deploy.sh" 8>&- 9>&- | grep -x 'DEPLOY_CONTRACT_VERSION=1' >/dev/null || die "SOURCE_REF의 deploy.sh에 environment 계약이 없다"
	git show "$git_ref:deploy/environment.py" 8>&- 9>&- | cmp -s - "$script_dir/environment.py" || die "SOURCE_REF에 같은 environment 계약이 없다"
	git show "$git_ref:deploy/environments/$environment.json" 8>&- 9>&- | cmp -s - "$script_dir/environments/$environment.json" || die "SOURCE_REF의 environment manifest가 다르다"

	if [ "$environment" = staging ] || [ "$environment" = production ]; then
		git show "$git_ref:deploy/deploy.sh" 8>&- 9>&- | grep -x 'RUNTIME_CONTRACT_VERSION=1' >/dev/null || die "SOURCE_REF에 runtime 계약이 없다"
		local runtime_file
		for runtime_file in runtime_parameters.py runtime-parameters.v1.json compose.runtime.yml observe_images.py; do
			git show "$git_ref:deploy/$runtime_file" 8>&- 9>&- | cmp -s - "$script_dir/$runtime_file" || die "SOURCE_REF의 runtime 계약이 다르다"
		done
	fi
	local source_tree
	source_tree=$(git rev-parse "$git_ref^{tree}" 8>&- 9>&-) || die "SOURCE_TREE를 읽지 못했다"
	[[ "$source_tree" =~ ^[0-9a-f]{40}$ ]] || die "SOURCE_TREE가 잘못되었다"

	trap on_exit EXIT
	fetch_parameter_snapshot
	if [ "$DEPLOY_TIMING" = on ]; then
		deploy_timing_started=yes
		log "측정 시작 $(now_ms) backend=$(backend_container)"
	fi

	log "배포 시작: $sha"
	if [ "$git_ref" != "$sha" ]; then
		log "레포는 이미지와 다른 $git_ref 로 맞춘다"
	fi
	log "파라미터 경로: $PARAM_PATH, 도메인: $SITE_DOMAIN, 별칭: ${SITE_ALIASES:-없음}"

	local blue_running=no green_running=no blue_up=no green_up=no recorded routed='' running
	running=$(running_container backend-blue) || die "backend-blue가 도는지 docker에서 읽지 못했다"
	if [ -n "$running" ]; then blue_running=yes; fi
	running=$(running_container backend-green) || die "backend-green이 도는지 docker에서 읽지 못했다"
	if [ -n "$running" ]; then green_running=yes; fi
	if [ "$blue_running" = yes ] && backend_ready blue; then blue_up=yes; fi
	if [ "$green_running" = yes ] && backend_ready green; then green_up=yes; fi
	recorded=$(recorded_live_color)
	# 전환 뒤 기록 전에 배포가 끊기면 기록 파일은 옛 색이라, 둘 다 UP이면 Caddy가 지금 보내는 색을 먼저 믿는다.
	if [ "$blue_up" = yes ] && [ "$green_up" = yes ]; then
		routed=$(routed_color)
	fi
	read -r target_color live_color <<<"$(choose_colors "$blue_running" "$green_running" "$blue_up" "$green_up" "${routed:-$recorded}")"
	legacy_container=$(running_container backend) || die "블루그린 이전 backend가 도는지 docker에서 읽지 못했다"

	# 대상이 아닌 색이 돌고 있으면 UP이 아니어도 전환 뒤에 멈추고, 그때까지 다시 만들지 않게 지금 태그를 둔다.
	local previous_color
	previous_color=$(previous_running_color "$target_color" "$blue_running" "$green_running")

	if [ -n "$routed" ]; then
		log "블루와 그린이 모두 UP이라 Caddy가 지금 보내는 색($routed)을 살린다"
	elif [ "$blue_up" = yes ] && [ "$green_up" = yes ]; then
		log "블루와 그린이 모두 UP이고 Caddy가 보내는 색을 알 수 없어 기록 파일의 색(${recorded:-없음})을 따른다. 기록이 없으면 블루를 살아 있는 색으로 둔다"
	fi
	if [ -n "$previous_color" ] && [ -z "$live_color" ]; then
		log "경고: 돌고 있는 이전 백엔드(backend-$previous_color)의 readiness가 UP이 아니다. 받고 있는 색 없이 진행한다"
	fi
	log "대상 색: $target_color, 살아 있는 색: ${live_color:-없음}, 멈출 색: ${previous_color:-없음}, 블루그린 이전 backend: ${legacy_container:-없음}"

	cd "$REPO_DIR"
	git checkout --quiet --force "$git_ref" 8>&- 9>&- || die "커밋 $git_ref 를 찾지 못했다"
	git diff --quiet "$git_ref" -- 8>&- 9>&- || die "SOURCE_REF와 체크아웃 tree가 다르다"
	log "레포를 $(git rev-parse --short HEAD 8>&- 9>&-) 로 맞췄다"
	# 체크아웃한 Caddyfile이 이 파일들을 import하므로, 뒤에서 실패해도 Caddy가 다시 뜰 때 읽을 수 있게 바로 쓴다.
	write_access_log_file
	write_sso_file
	serving=$(serving_upstreams "$target_color" "$live_color" "$previous_color" "$legacy_container")
	write_upstreams_file "$serving" || die "Caddy 백엔드 목록 파일을 쓰지 못했다"
	log "Caddy가 보낼 백엔드: $serving"

	local want_image="$ECR_REGISTRY/meterengine-backend:$sha" frontend_image="$ECR_REGISTRY/meterengine-frontend:$sha"
	if [ "$environment" = staging ] || [ "$environment" = production ]; then
		want_image="$ECR_REGISTRY/meterengine-backend@$BACKEND_IMAGE_DIGEST"
		frontend_image="$ECR_REGISTRY/meterengine-frontend@$FRONTEND_IMAGE_DIGEST"
	fi
	local blue_image="$want_image" green_image="$want_image" previous_image
	if [ -n "$previous_color" ]; then
		previous_image=$(running_image "backend-$previous_color") || die "이전 백엔드(backend-$previous_color)의 이미지를 읽지 못했다"
		if [ "$previous_color" = blue ]; then
			blue_image="$previous_image"
		else
			green_image="$previous_image"
		fi
	fi
	write_env_file "$sha" "$sha" "$sha" "$blue_image" "$green_image" "$frontend_image"

	# 엉뚱한 DB에 붙은 채 성공한 배포를 알아챌 수 있게 남긴다.
	local db_host
	db_host=$(grep '^DB_HOST=' "$ENV_FILE" | cut -d= -f2-)
	export DB_HOST="$db_host" BACKEND_BLUE_IMAGE="$blue_image" BACKEND_GREEN_IMAGE="$green_image" FRONTEND_IMAGE="$frontend_image"
	log "DB: $db_host"

	compose=(docker compose --project-name "$COMPOSE_PROJECT" --env-file "$ENV_FILE" -f "$REPO_DIR/deploy/compose.prod.yml")
	if [ "$environment" = staging ] || [ "$environment" = production ]; then
		compose+=(-f "$REPO_DIR/deploy/compose.runtime.yml")
	fi

	# 비밀번호가 로그에 남지 않게 stdin으로 넘긴다.
	aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null
	log "ECR 로그인 완료"

	# 살아 있는 색과 블루그린 이전 backend는 그대로 두고 나머지만 받고 올린다.
	# 살아 있는 색의 태그는 ECR에서 이미 지워졌을 수 있어 받지 않는다.
	local service others=()
	for service in $ENABLED_SERVICES; do
		case "$service" in backend-*) ;; *) others+=("$service") ;; esac
	done
	"${compose[@]}" pull --quiet "${others[@]}" "backend-$target_color"
	"${compose[@]}" up -d --no-deps "${others[@]}"
	discard_target_on_failure=yes
	"${compose[@]}" up -d --no-deps "backend-$target_color"
	log "컨테이너 기동 요청 완료"

	# 첫 전환 배포에서 다시 만들어진 프론트엔드가 부를 :8081을 대상 색 기동을 기다리기 전에 연다.
	# Caddyfile만 바뀐 배포는 up -d가 컨테이너를 다시 만들지 않아 reload로 반영한다.
	reload_caddy || die "Caddy 설정을 반영하지 못했다. Caddyfile을 확인한다"
	log "Caddy 설정 반영 완료"

	if ! wait_until "backend-$target_color 준비됨" 45 2 backend_healthy "$target_color"; then
		"${compose[@]}" logs --tail 50 "backend-$target_color" || true
		die "새 백엔드(backend-$target_color)가 90초 안에 준비되지 않았다"
	fi

	local target_image
	target_image=$(running_image "backend-$target_color") || die "새 백엔드(backend-$target_color)의 이미지를 읽지 못했다"
	[ "$target_image" = "$want_image" ] || die "새 백엔드(backend-$target_color)의 이미지가 다르다. 기대: $want_image, 실제: $target_image"

	# prometheus.yml만 바뀐 배포도 up -d가 컨테이너를 다시 만들지 않아 HUP으로 다시 읽게 한다.
	if service_enabled prometheus; then
		wait_for_prometheus_ready "${compose[@]}" || die "Prometheus가 90초 안에 준비되지 않았다"
		"${compose[@]}" exec -T prometheus kill -HUP 1
		log "Prometheus 설정 반영 완료"
	fi
	# cloudwatch exporter도 기동할 때만 config.yml을 읽는다.
	# 실패해도 새 색은 멀쩡하므로 이것 때문에 새 색을 버리지 않는다.
	if service_enabled cloudwatch-exporter; then
		if "${compose[@]}" exec -T cloudwatch-exporter kill -HUP 1; then
			log "cloudwatch exporter 설정 반영 완료"
		else
			log "cloudwatch exporter가 설정을 다시 읽지 못했다"
		fi
	fi

	# 옛 색을 멈추기 전에 목록에서 빼야 Caddy가 멈추는 색에 새 요청을 보내지 않는다.
	switched=yes
	write_upstreams_file "backend-$target_color:8080" || die "Caddy 백엔드 목록 파일을 쓰지 못했다"
	reload_caddy || die "Caddy가 새 백엔드(backend-$target_color)로만 보내게 하지 못했다"
	log "Caddy 백엔드 목록을 새 백엔드(backend-$target_color)로 바꿨다"
	wait_until "Caddy가 새 백엔드(backend-$target_color)로 보낸다" 60 1 routed_to "$target_color" ||
		die "Caddy가 60초 안에 새 백엔드(backend-$target_color)로 보내지 않았다"

	# 여기부터는 대상 색이 유일하게 받는 백엔드일 수 있어 실패해도 지우지 않는다.
	discard_target_on_failure=no
	if [ -n "$previous_color" ] || [ -n "$legacy_container" ]; then
		sleep "$DRAIN_SECONDS"
	fi
	if [ -n "$previous_color" ]; then
		# 지우지 않고 멈춰야 이미지가 정리되지 않아 롤백이 빠르다.
		"${compose[@]}" stop "backend-$previous_color"
		log "이전 백엔드(backend-$previous_color)를 멈췄다"
	fi
	# 비활성 서비스를 건드리지 않고 블루그린 이전 backend만 지운다.
	if [ -n "$legacy_container" ]; then
		docker stop "$legacy_container"
		docker rm "$legacy_container"
		log "블루그린 이전 backend 컨테이너를 정리했다"
	fi

	if ! wait_for_http "프론트엔드" http://127.0.0.1:3000/health '^ok$'; then
		"${compose[@]}" logs --tail 50 frontend || true
		die "프론트엔드가 90초 안에 준비되지 않았다"
	fi

	# 지운 이미지도 ECR에 남아 있어 롤백할 수 있다.
	docker image prune -af --filter "until=336h" >/dev/null 2>&1 || true

	# 경보 provisioning은 기동할 때만 읽혀, bind mount로 바뀐 alerting.yml을 reload API로 다시 읽게 한다.
	# 앱이 쓰는 경로가 아니라서 앱 확인과 이미지 정리가 끝난 뒤에 한다.
	if service_enabled grafana; then
		wait_for_http "Grafana" http://127.0.0.1:3001/api/health '"database"' || die "Grafana가 90초 안에 준비되지 않았다"
		reload_grafana_alerting || die "Grafana 경보 설정을 반영하지 못했다. 401이면 admin 비밀번호가 grafana-admin-password와 다르고, 아니면 alerting.yml을 확인한다"
		log "Grafana 경보 설정 반영 완료"
	fi

	if [ "$environment" = staging ] || [ "$environment" = production ]; then
		observe_deployment
	fi
	write_state_file "$target_color" "$sha"
	log "배포 성공: $sha"
}

probe_main() {
	local environment="${1:-}" sha="${2:-}" git_ref="${3:-}" script_dir target_values source_tree target_color BACKEND_IMAGE_ID FRONTEND_IMAGE_ID
	case "$environment" in staging | production) ;; *) die "probe environment가 잘못되었다" ;; esac
	[[ "$sha" =~ ^[0-9a-f]{40}$ ]] && [[ "$git_ref" =~ ^[0-9a-f]{40}$ ]] || die "probe source가 잘못되었다"
	[[ "${DEPLOY_RUN_ID:-}" =~ ^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$ ]] || die "DEPLOY_RUN_ID가 잘못되었다"
	script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
	acquire_deploy_lock
	target_values=$(python3 "$script_dir/environment.py" preflight --environment "$environment") || exit 2
	eval "$target_values"
	cd "$REPO_DIR"
	source_tree=$(git rev-parse "$git_ref^{tree}" 8>&- 9>&-)
	target_color=$(recorded_live_color)
	observe_deployment
	local field
	for field in "LIVE_SHA=$sha" "SOURCE_REF=$git_ref" "SOURCE_TREE=$source_tree" "DEPLOY_RUN_ID=$DEPLOY_RUN_ID" \
		"BACKEND_IMAGE_DIGEST=$BACKEND_IMAGE_DIGEST" "FRONTEND_IMAGE_DIGEST=$FRONTEND_IMAGE_DIGEST" \
		"BACKEND_IMAGE_ID=$BACKEND_IMAGE_ID" "FRONTEND_IMAGE_ID=$FRONTEND_IMAGE_ID"; do
		grep -Fqx "$field" "$STATE_FILE" || die "state와 실제 배포 관측이 다르다"
	done
}

main() {
	if [ "${1:-}" = --probe ]; then
		shift
		probe_main "$@"
	else
		deploy_main "$@"
	fi
}

main "$@"
