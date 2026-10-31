#!/usr/bin/env bash
# 실행 중 git checkout이 이 파일을 덮어쓰므로, 본문은 모두 main()에 두고 맨 끝에서 한 번 부른다.

set -euo pipefail

REPO_DIR=/opt/meterengine
ENV_FILE=/etc/meterengine/prod.env
CONFIG_FILE=/etc/meterengine/deploy.conf
STATE_FILE=/etc/meterengine/deploy-state
PARAM_PATH=/meterengine/prod
SITE_DOMAIN=meterengine.com
SITE_ALIASES=www.meterengine.com
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

# 계정이나 리전을 옮기면 .github/workflows의 같은 값도 함께 고친다.
export AWS_REGION=ap-northeast-2
ECR_REGISTRY=928618307647.dkr.ecr.ap-northeast-2.amazonaws.com
# compose.prod.yml의 name과 같아야 한다.
COMPOSE_PROJECT=meterengine-prod
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
	case "$(target_on_exit "$code" "$discard_target_on_failure")" in
	discard)
		discard_target || true
		;;
	esac
	if [ "$DEPLOY_TIMING" = on ]; then
		log "측정 끝 $(now_ms) backend=$(backend_container) 종료=$code" || true
	fi
	exit "$code"
}

# Parameter Store 값으로 .env를 매번 새로 만든다.
write_env_file() {
	local image_tag="$1" blue_tag="$2" green_tag="$3"

	mkdir -p "$(dirname "$ENV_FILE")"
	# 비밀번호가 들어가므로 쓰기 전에 권한부터 좁힌다.
	touch "$ENV_FILE"
	chmod 600 "$ENV_FILE"

	local tmp
	tmp=$(mktemp)
	chmod 600 "$tmp"

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
		done >>"$tmp"

	{
		printf 'IMAGE_TAG=%s\n' "$image_tag"
		printf 'BACKEND_BLUE_TAG=%s\n' "$blue_tag"
		printf 'BACKEND_GREEN_TAG=%s\n' "$green_tag"
		printf 'ECR_REGISTRY=%s\n' "$ECR_REGISTRY"
		printf 'SITE_DOMAIN=%s\n' "$SITE_DOMAIN"
		printf 'SITE_ALIASES=%s\n' "$SITE_ALIASES"
		printf 'BACKEND_MEM_LIMIT=%s\n' "$BACKEND_MEM_LIMIT"
		printf 'BACKEND_MAX_RAM_PERCENTAGE=%s\n' "$BACKEND_MAX_RAM_PERCENTAGE"
	} >>"$tmp"

	mv "$tmp" "$ENV_FILE"
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

# SUT도 이 Caddyfile을 쓰는데 DNS 없는 이름은 meterengine.com 공용 Let's Encrypt 한도로 발급을 계속 재시도하고, 이미 있는 SUT는 user-data를 다시 돌지 않아 PARAM_PATH로 정한다.
sso_sites_default() {
	if [ "$PARAM_PATH" = /meterengine/prod ]; then echo on; else echo off; fi
}

# Caddyfile이 가져오는 SSO 사이트 설정을 쓴다. deploy.conf의 CADDY_SSO_SITES가 있으면 그 값을 따른다.
# Caddy가 반쯤 쓴 파일을 읽지 않도록 같은 디렉터리에 쓰고 mv한다.
write_sso_file() {
	local file="$REPO_DIR/deploy/caddy/sso.caddy" value content tmp
	value=${CADDY_SSO_SITES-$(sso_sites_default)}
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
	mv "$tmp" "$STATE_FILE"
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
	password=$(grep '^GRAFANA_ADMIN_PASSWORD=' "$ENV_FILE" | cut -d= -f2- | sed 's/[\\"]/\\&/g') || return 1
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

main() {
	local sha="${1:-}"
	[ -n "$sha" ] || die "배포할 이미지의 git SHA를 인자로 줘야 한다. 예: $0 979e251... [체크아웃할 git ref]"
	local git_ref="${2:-$sha}"

	# CD가 기다리다 포기해도 SSM은 이 스크립트를 계속 돌린다. 두 배포가 겹치면 살아 있는 색을 멈출 수 있다.
	acquire_deploy_lock

	trap on_exit EXIT
	if [ "$DEPLOY_TIMING" = on ]; then
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
	git fetch --quiet origin 8>&- 9>&-
	git checkout --quiet --force "$git_ref" 8>&- 9>&- || die "커밋 $git_ref 를 찾지 못했다"
	log "레포를 $(git rev-parse --short HEAD) 로 맞췄다"
	# 체크아웃한 Caddyfile이 이 파일들을 import하므로, 뒤에서 실패해도 Caddy가 다시 뜰 때 읽을 수 있게 바로 쓴다.
	write_access_log_file
	write_sso_file
	serving=$(serving_upstreams "$target_color" "$live_color" "$previous_color" "$legacy_container")
	write_upstreams_file "$serving" || die "Caddy 백엔드 목록 파일을 쓰지 못했다"
	log "Caddy가 보낼 백엔드: $serving"

	local blue_tag="$sha" green_tag="$sha" previous_image
	if [ -n "$previous_color" ]; then
		previous_image=$(running_image "backend-$previous_color") || die "이전 백엔드(backend-$previous_color)의 이미지를 읽지 못했다"
		if [ "$previous_color" = blue ]; then
			blue_tag="${previous_image##*:}"
		else
			green_tag="${previous_image##*:}"
		fi
	fi
	write_env_file "$sha" "$blue_tag" "$green_tag"

	# 엉뚱한 DB에 붙은 채 성공한 배포를 알아챌 수 있게 남긴다.
	local db_host
	db_host=$(grep '^DB_HOST=' "$ENV_FILE" | cut -d= -f2-)
	log "DB: $db_host"

	compose=(docker compose --env-file "$ENV_FILE" -f "$REPO_DIR/deploy/compose.prod.yml")

	# 비밀번호가 로그에 남지 않게 stdin으로 넘긴다.
	aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null
	log "ECR 로그인 완료"

	# 살아 있는 색과 블루그린 이전 backend는 그대로 두고 나머지만 받고 올린다.
	# 살아 있는 색의 태그는 ECR에서 이미 지워졌을 수 있어 받지 않는다.
	local service_list service others=()
	service_list=$("${compose[@]}" config --services | grep -v '^backend-')
	while IFS= read -r service; do
		others+=("$service")
	done <<<"$service_list"
	"${compose[@]}" pull --quiet "${others[@]}" "backend-$target_color"
	"${compose[@]}" up -d "${others[@]}"
	discard_target_on_failure=yes
	"${compose[@]}" up -d "backend-$target_color"
	log "컨테이너 기동 요청 완료"

	# 첫 전환 배포에서 다시 만들어진 프론트엔드가 부를 :8081을 대상 색 기동을 기다리기 전에 연다.
	# Caddyfile만 바뀐 배포는 up -d가 컨테이너를 다시 만들지 않아 reload로 반영한다.
	reload_caddy || die "Caddy 설정을 반영하지 못했다. Caddyfile을 확인한다"
	log "Caddy 설정 반영 완료"

	if ! wait_until "backend-$target_color 준비됨" 45 2 backend_healthy "$target_color"; then
		"${compose[@]}" logs --tail 50 "backend-$target_color" || true
		die "새 백엔드(backend-$target_color)가 90초 안에 준비되지 않았다"
	fi

	local want_image="$ECR_REGISTRY/meterengine-backend:$sha" target_image
	target_image=$(running_image "backend-$target_color") || die "새 백엔드(backend-$target_color)의 이미지를 읽지 못했다"
	[ "$target_image" = "$want_image" ] || die "새 백엔드(backend-$target_color)의 이미지가 다르다. 기대: $want_image, 실제: $target_image"

	# prometheus.yml만 바뀐 배포도 up -d가 컨테이너를 다시 만들지 않아 HUP으로 다시 읽게 한다.
	wait_for_prometheus_ready "${compose[@]}" || die "Prometheus가 90초 안에 준비되지 않았다"
	"${compose[@]}" exec -T prometheus kill -HUP 1
	log "Prometheus 설정 반영 완료"

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
	# 서비스 이름을 주면 orphan(블루그린 이전 backend)만 graceful하게 지우고 다른 서비스는 건드리지 않는다.
	"${compose[@]}" up -d --remove-orphans "backend-$target_color"
	if [ -n "$legacy_container" ]; then
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
	wait_for_http "Grafana" http://127.0.0.1:3001/api/health '"database"' || die "Grafana가 90초 안에 준비되지 않았다"
	reload_grafana_alerting || die "Grafana 경보 설정을 반영하지 못했다. 401이면 admin 비밀번호가 grafana-admin-password와 다르고, 아니면 alerting.yml을 확인한다"
	log "Grafana 경보 설정 반영 완료"

	write_state_file "$target_color" "$sha"
	log "배포 성공: $sha"
}

main "$@"
