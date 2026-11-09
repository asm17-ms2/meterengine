#!/usr/bin/env bash
# 실행 중 git checkout이 이 파일을 덮어쓰므로, 본문은 모두 main()에 두고 맨 끝에서 한 번 부른다.

set -euo pipefail

# 환경별 값은 deploy/environments/<환경>.json에 있다.
REPO_DIR=/opt/meterengine
ENV_FILE=/etc/meterengine/prod.env
UNIT_FILE=/etc/systemd/system/meterengine-cpu-metric.service

# 옛 스크립트가 FD 9와 deploy.lock을 다시 열어도 호출부의 잠금은 유지한다.
LOCK_FILE=/etc/meterengine/deploy-command.lock

# 계정이나 리전을 옮기면 .github/workflows의 같은 값도 함께 고친다.
export AWS_REGION=ap-northeast-2
ECR_REGISTRY=928618307647.dkr.ecr.ap-northeast-2.amazonaws.com
# compose.prod.yml의 name과 같아야 한다.
COMPOSE_PROJECT=meterengine-prod

compose=()

log() { printf '[deploy] %s\n' "$*"; }
die() {
	printf '[deploy] 실패: %s\n' "$*" >&2
	exit 1
}

# 호출한 셸이 잡은 잠금을 이어받아 checkout부터 배포 끝까지 유지한다.
acquire_deploy_lock() {
	command -v flock >/dev/null || die "배포 잠금에 필요한 flock이 없다"
	if ! [ "$LOCK_FILE" -ef /proc/self/fd/8 ]; then
		exec 8>"$LOCK_FILE"
	fi
	flock -n 8 || die "다른 배포가 아직 돌고 있다. 끝난 뒤 다시 실행한다"
}

# 실행 중인 서비스 컨테이너의 이미지 이름을 찍는다.
running_image() {
	local id
	id=$(docker ps -q \
		--filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
		--filter "label=com.docker.compose.service=$1")
	[ -n "$id" ] || return 1
	docker inspect -f '{{.Config.Image}}' "$id"
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
		printf 'IMAGE_REPOSITORY_PREFIX=%s\n' "$IMAGE_REPOSITORY_PREFIX"
		printf 'SITE_DOMAIN=%s\n' "$SITE_DOMAIN"
		printf 'SITE_ALIASES=%s\n' "$SITE_ALIASES"
		printf 'BACKEND_MEM_LIMIT=%s\n' "$BACKEND_MEM_LIMIT"
		printf 'BACKEND_MAX_RAM_PERCENTAGE=%s\n' "$BACKEND_MAX_RAM_PERCENTAGE"
		printf 'COMPOSE_PROFILES=%s\n' "$COMPOSE_PROFILES"
	} >>"$tmp"

	# 빠진 값이 있으면 지금 env 파일을 그대로 둔다.
	local key
	for key in $REQUIRED_PARAMETERS; do
		if ! grep -q "^$key=." "$tmp"; then
			rm -f "$tmp"
			die "Parameter Store $PARAM_PATH 에 $key 값이 없다"
		fi
	done

	mv "$tmp" "$ENV_FILE"
	chmod 600 "$ENV_FILE"
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

# 10초 CPU 지표는 서버마다 systemd가 띄운다. checkout으로 바뀐 스크립트를 읽도록 매번 다시 띄운다.
# 앱은 이미 떠 있으므로 실패해도 배포를 멈추지 않는다. 지표가 없으면 확장만 늦어진다.
install_cpu_metric_unit() {
	if install -m 0644 "$REPO_DIR/deploy/server/cpu-metric.service" "$UNIT_FILE" &&
		systemctl daemon-reload &&
		systemctl enable --quiet "$(basename "$UNIT_FILE")" &&
		systemctl restart "$(basename "$UNIT_FILE")"; then
		log "CPU 지표 유닛을 다시 띄웠다"
	else
		log "경고: CPU 지표 유닛을 띄우지 못했다. 확장 정책의 평균에 이 서버가 빠진다"
	fi
}

main() {
	local environment="${1:-}" sha="${2:-}"
	if [ -z "$environment" ] || ! [[ "$sha" =~ ^[0-9a-f]{40}$ ]]; then
		die "사용법: $0 <환경> <이미지 git SHA 40자> [체크아웃할 git ref]"
	fi
	local git_ref="${3:-$sha}"

	# 부팅 배포와 사람이 부른 배포가 겹치지 않게 한다.
	acquire_deploy_lock

	# eval "$(...)"로 바로 쓰면 실패해도 종료 코드가 0이라 먼저 받는다.
	local target_values
	target_values=$(python3 "$REPO_DIR/deploy/environment.py" preflight --environment "$environment") ||
		die "환경 $environment 확인에 실패해 아무것도 바꾸지 않았다"
	eval "$target_values"

	log "배포 시작: $environment $sha"
	if [ "$git_ref" != "$sha" ]; then
		log "레포는 이미지와 다른 $git_ref 로 맞춘다"
	fi
	log "파라미터 경로: $PARAM_PATH, 도메인: $SITE_DOMAIN"

	cd "$REPO_DIR"
	git fetch --quiet origin 8>&- 9>&-
	git checkout --quiet --force "$git_ref" 8>&- 9>&- || die "커밋 $git_ref 를 찾지 못했다"
	log "레포를 $(git rev-parse --short HEAD) 로 맞췄다"

	write_env_file "$sha"

	# 엉뚱한 DB에 붙은 채 성공한 배포를 알아챌 수 있게 남긴다. 로그가 공개될 수 있어 전체 주소를 찍지 않는다.
	local db_identifier
	db_identifier=$(grep '^DB_HOST=' "$ENV_FILE" | cut -d= -f2- | cut -d. -f1)
	log "DB: $db_identifier"
	[ "$db_identifier" = "$EXPECTED_DB_IDENTIFIER" ] || die "DB($db_identifier)가 환경 $environment 의 값($EXPECTED_DB_IDENTIFIER)과 다르다"

	compose=(docker compose --env-file "$ENV_FILE" -f "$REPO_DIR/deploy/compose.prod.yml")

	# 비밀번호가 로그에 남지 않게 stdin으로 넘긴다.
	aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null
	log "ECR 로그인 완료"

	# 서비스 이름을 주면 orphan만 지우고 감시 에이전트는 건드리지 않는다.
	"${compose[@]}" pull --quiet backend frontend
	"${compose[@]}" up -d --remove-orphans backend frontend
	log "컨테이너 기동 요청 완료"

	if ! wait_until "백엔드 준비됨" 90 2 http_ok http://127.0.0.1:8080/actuator/health '"status":"UP"'; then
		"${compose[@]}" logs --tail 50 backend || true
		die "백엔드가 180초 안에 준비되지 않았다"
	fi

	local want_image="$ECR_REGISTRY/$IMAGE_REPOSITORY_PREFIX-backend:$sha" backend_image
	backend_image=$(running_image backend) || die "백엔드의 이미지를 읽지 못했다"
	[ "$backend_image" = "$want_image" ] || die "백엔드의 이미지가 다르다. 기대: $want_image, 실제: $backend_image"

	if ! wait_until "프론트엔드 준비됨" 45 2 http_ok http://127.0.0.1:3000/health '^ok$'; then
		"${compose[@]}" logs --tail 50 frontend || true
		die "프론트엔드가 90초 안에 준비되지 않았다"
	fi

	# 감시 에이전트 이미지는 다른 레지스트리에서 받는다. 받지 못해도 앱은 그대로 둔다.
	if [ -n "$COMPOSE_PROFILES" ]; then
		if "${compose[@]}" up -d; then
			log "감시 에이전트($COMPOSE_PROFILES) 기동 완료"
		else
			log "경고: 감시 에이전트($COMPOSE_PROFILES)를 띄우지 못했다. 앱은 그대로 둔다"
		fi
	fi

	install_cpu_metric_unit

	# 지운 이미지도 ECR에 남아 있어 롤백할 수 있다.
	docker image prune -af --filter "until=336h" >/dev/null 2>&1 || true

	log "배포 성공: $sha"
}

main "$@"
