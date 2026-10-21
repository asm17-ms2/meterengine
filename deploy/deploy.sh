#!/usr/bin/env bash
# 실행 중 git checkout이 이 파일을 덮어쓰므로, 본문은 모두 main()에 두고 맨 끝에서 한 번 부른다.

set -euo pipefail

REPO_DIR=/opt/meterengine
ENV_FILE=/etc/meterengine/prod.env
CONFIG_FILE=/etc/meterengine/deploy.conf
PARAM_PATH=/meterengine/prod
SITE_DOMAIN=meterengine.com
SITE_ALIASES=www.meterengine.com
DEPLOY_TIMING=off

if [ -f "$CONFIG_FILE" ]; then
	. "$CONFIG_FILE"
fi

# 계정이나 리전을 옮기면 .github/workflows의 같은 값도 함께 고친다.
export AWS_REGION=ap-northeast-2
ECR_REGISTRY=928618307647.dkr.ecr.ap-northeast-2.amazonaws.com

log() { printf '[deploy] %s\n' "$*"; }
die() { printf '[deploy] 실패: %s\n' "$*" >&2; exit 1; }

now_ms() { date +%s%3N; }

backend_container() {
	local id
	id=$(docker ps -q --filter label=com.docker.compose.service=backend | paste -sd, -)
	printf '%s' "${id:-없음}"
}

log_timing_end() {
	local code=$?
	log "측정 끝 $(now_ms) backend=$(backend_container) 종료=$code"
}

# Parameter Store 값으로 .env를 매번 새로 만든다.
write_env_file() {
	local image_tag="$1"

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
		printf 'ECR_REGISTRY=%s\n' "$ECR_REGISTRY"
		printf 'SITE_DOMAIN=%s\n' "$SITE_DOMAIN"
		printf 'SITE_ALIASES=%s\n' "$SITE_ALIASES"
	} >>"$tmp"

	mv "$tmp" "$ENV_FILE"
	chmod 600 "$ENV_FILE"

	grep -q '^DB_HOST=' "$ENV_FILE" || die "Parameter Store에서 db-host를 읽지 못했다"
}

# 서비스가 실제로 요청을 받을 때까지 기다린다.
wait_for_http() {
	local name="$1" url="$2" expect="$3" i body
	for i in $(seq 1 45); do
		if body=$(curl -fsS --max-time 3 "$url" 2>/dev/null) && grep -qi "$expect" <<<"$body"; then
			log "$name 준비됨 (${i}회 시도)"
			return 0
		fi
		sleep 2
	done
	return 1
}

# 방금 뜬 Prometheus는 HUP 처리기를 걸기 전이라 HUP에 죽는다.
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

main() {
	local sha="${1:-}"
	[ -n "$sha" ] || die "배포할 git SHA를 인자로 줘야 한다. 예: $0 979e251..."

	if [ "$DEPLOY_TIMING" = on ]; then
		log "측정 시작 $(now_ms) backend=$(backend_container)"
		trap log_timing_end EXIT
	fi

	log "배포 시작: $sha"
	log "파라미터 경로: $PARAM_PATH, 도메인: $SITE_DOMAIN, 별칭: ${SITE_ALIASES:-없음}"

	cd "$REPO_DIR"
	git fetch --quiet origin
	git checkout --quiet --force "$sha" || die "커밋 $sha 를 찾지 못했다"
	log "레포를 $(git rev-parse --short HEAD) 로 맞췄다"

	write_env_file "$sha"

	# 엉뚱한 DB에 붙은 채 성공한 배포를 알아챌 수 있게 남긴다.
	local db_host
	db_host=$(grep '^DB_HOST=' "$ENV_FILE" | cut -d= -f2-)
	log "DB: $db_host"

	local compose=(docker compose --env-file "$ENV_FILE" -f "$REPO_DIR/deploy/compose.prod.yml")

	# 비밀번호가 로그에 남지 않게 stdin으로 넘긴다.
	aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null
	log "ECR 로그인 완료"

	"${compose[@]}" pull --quiet
	"${compose[@]}" up -d --remove-orphans
	log "컨테이너 기동 요청 완료"

	# Caddyfile만 바뀐 배포는 up -d가 컨테이너를 다시 만들지 않아 reload로 반영한다.
	# 방금 뜬 컨테이너는 admin API가 아직 안 열렸을 수 있어 재시도한다.
	local reloaded=no i
	for i in $(seq 1 10); do
		if "${compose[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null 2>&1; then
			reloaded=yes
			break
		fi
		sleep 2
	done
	[ "$reloaded" = yes ] || die "Caddy 설정을 반영하지 못했다. Caddyfile을 확인한다"
	log "Caddy 설정 반영 완료"

	# prometheus.yml만 바뀐 배포도 up -d가 컨테이너를 다시 만들지 않아 HUP으로 다시 읽게 한다.
	wait_for_prometheus_ready "${compose[@]}" || die "Prometheus가 90초 안에 준비되지 않았다"
	"${compose[@]}" kill -s HUP prometheus
	log "Prometheus 설정 반영 완료"

	if ! wait_for_http "백엔드" http://127.0.0.1:8080/actuator/health '"status":"UP"'; then
		"${compose[@]}" logs --tail 50 backend || true
		die "백엔드가 90초 안에 준비되지 않았다"
	fi
	if ! wait_for_http "프론트엔드" http://127.0.0.1:3000/usage '<!doctype html'; then
		"${compose[@]}" logs --tail 50 frontend || true
		die "프론트엔드가 90초 안에 준비되지 않았다"
	fi

	# 지운 이미지도 ECR에 남아 있어 롤백할 수 있다.
	docker image prune -af --filter "until=336h" >/dev/null 2>&1 || true

	log "배포 성공: $sha"
}

main "$@"
