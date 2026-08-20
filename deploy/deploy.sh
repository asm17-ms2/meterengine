#!/usr/bin/env bash
#
# 운영 배포 스크립트. EC2에서 root로 돈다.
#
#   /opt/meterengine/deploy/deploy.sh <git-sha>
#
# 호출 주체는 둘이다:
#   - CD 워크플로(MS2-167)가 SSM SendCommand로 실행
#   - 사람이 SSM 세션에 붙어 직접 실행 (첫 배포와 롤백)
#
# 하는 일: 레포를 그 SHA로 맞추고 -> Parameter Store에서 .env를 새로 만들고 ->
#          ECR에서 이미지를 받아 -> 스택을 올리고 -> 살아났는지 확인한다.
#
# 이 스크립트 전체가 main() 안에 있고 파일 맨 끝에서 한 번 호출된다. 왜냐하면 이 스크립트가
# 실행 중에 git checkout으로 자기 자신을 덮어쓰기 때문이다. bash는 스크립트를 실행하면서
# 조금씩 읽어 나가므로, 도중에 파일 내용이 바뀌면 읽던 위치가 어긋나 엉뚱한 줄을 실행한다.
# 함수로 감싸 두면 호출 시점에 이미 파일을 끝까지 읽은 뒤라 안전하다.

set -euo pipefail

REPO_DIR=/opt/meterengine
ENV_FILE=/etc/meterengine/prod.env
PARAM_PATH=/meterengine/prod

# 계정과 리전은 aws-access-check.yml에도 같은 값이 박혀 있다. 계정을 옮기면 두 곳을 함께 고친다.
export AWS_REGION=ap-northeast-2
ECR_REGISTRY=928618307647.dkr.ecr.ap-northeast-2.amazonaws.com

log() { printf '[deploy] %s\n' "$*"; }
die() { printf '[deploy] 실패: %s\n' "$*" >&2; exit 1; }

# Parameter Store 값으로 .env를 매번 새로 만든다. 서버에 손으로 만든 .env를 남겨 두지 않는
# 이유는, 그렇게 하면 파라미터를 고쳐도 서버가 옛 값을 계속 쓰는 상태를 눈치채기 어렵기 때문이다.
# RDS(MS2-164)가 생기면 여기서 읽는 값이 바뀌는 것만으로 접속 대상이 바뀐다.
write_env_file() {
	local image_tag="$1"

	mkdir -p "$(dirname "$ENV_FILE")"
	# 비밀번호가 들어가는 파일이다. 내용을 쓰기 전에 먼저 권한을 좁힌다.
	touch "$ENV_FILE"
	chmod 600 "$ENV_FILE"

	local tmp
	tmp=$(mktemp)
	chmod 600 "$tmp"

	# --with-decryption이 SecureString(db-password)을 KMS로 풀어 준다.
	# 파라미터 이름 db-host는 환경변수 DB_HOST가 된다.
	aws ssm get-parameters-by-path \
		--path "$PARAM_PATH" \
		--with-decryption \
		--query 'Parameters[].[Name,Value]' \
		--output text |
		while IFS=$'\t' read -r name value; do
			[ -n "$name" ] || continue
			key=$(basename "$name" | tr 'a-z-' 'A-Z_')
			# compose의 --env-file은 값을 그대로 읽는다. 값에 개행이 들어 있으면 그 뒤가
			# 통째로 무시되어 조용히 잘못된 설정으로 뜨므로, 여기서 끊는다.
			case "$value" in
			*$'\n'*) die "파라미터 $name 의 값에 개행이 있다. 값을 고쳐야 한다" ;;
			esac
			printf '%s=%s\n' "$key" "$value"
		done >>"$tmp"

	{
		printf 'IMAGE_TAG=%s\n' "$image_tag"
		printf 'ECR_REGISTRY=%s\n' "$ECR_REGISTRY"
	} >>"$tmp"

	mv "$tmp" "$ENV_FILE"
	chmod 600 "$ENV_FILE"

	grep -q '^DB_HOST=' "$ENV_FILE" || die "Parameter Store에서 db-host를 읽지 못했다"
}

# 서비스가 실제로 요청을 받을 때까지 기다린다. 컨테이너가 떴다(running)와 앱이 준비됐다는
# 다른 이야기다. 백엔드는 Flyway 마이그레이션을 끝내야 요청을 받는다.
wait_for_http() {
	local name="$1" url="$2" expect="$3" i
	for i in $(seq 1 45); do
		if curl -fsS --max-time 3 "$url" 2>/dev/null | grep -qi "$expect"; then
			log "$name 준비됨 (${i}회 시도)"
			return 0
		fi
		sleep 2
	done
	return 1
}

main() {
	local sha="${1:-}"
	[ -n "$sha" ] || die "배포할 git SHA를 인자로 줘야 한다. 예: $0 979e251..."

	log "배포 시작: $sha"

	# 1. 레포를 그 커밋으로 맞춘다. compose 파일과 Caddyfile이 이미지와 같은 커밋을 쓰게 된다.
	cd "$REPO_DIR"
	git fetch --quiet origin
	git checkout --quiet --force "$sha" || die "커밋 $sha 를 찾지 못했다"
	log "레포를 $(git rev-parse --short HEAD) 로 맞췄다"

	# 2. Parameter Store에서 설정을 받아 .env를 다시 만든다.
	write_env_file "$sha"

	# 3. DB가 아직 준비되지 않았으면 임시 postgres를 붙인다.
	#    MS2-164에서 db-host에 실제 RDS 엔드포인트가 들어가면 이 분기가 저절로 꺼진다.
	local compose_files=(-f "$REPO_DIR/deploy/compose.prod.yml")
	local db_host
	db_host=$(grep '^DB_HOST=' "$ENV_FILE" | cut -d= -f2-)
	if [ "$db_host" = "PLACEHOLDER" ]; then
		log "주의: db-host가 아직 PLACEHOLDER다. 임시 postgres 컨테이너로 띄운다 (MS2-164 대기 중)"
		compose_files+=(-f "$REPO_DIR/deploy/compose.local-db.yml")
	else
		log "DB: $db_host"
	fi

	local compose=(docker compose --env-file "$ENV_FILE" "${compose_files[@]}")

	# 4. ECR 로그인. 비밀번호가 로그에 남지 않도록 stdin으로 넘긴다.
	aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null
	log "ECR 로그인 완료"

	# 5. 이미지를 받고 스택을 올린다.
	#    --remove-orphans는 이전 배포에만 있던 서비스(예: RDS 전환 후의 postgres)를 치운다.
	"${compose[@]}" pull --quiet
	"${compose[@]}" up -d --remove-orphans
	log "컨테이너 기동 요청 완료"

	# Caddyfile은 bind mount라 파일 내용이 바뀌어도 compose가 컨테이너를 다시 만들지 않는다.
	# 서비스 정의(이미지, 환경변수, 볼륨 경로)만 보기 때문이다. 그래서 Caddyfile만 고친 배포는
	# up -d만으로 반영되지 않는다(MS2-166에서 www를 더할 때 실제로 겪었다).
	# reload는 설정이 같으면 아무 일도 하지 않고, 달라졌으면 연결을 끊지 않은 채 적용한다.
	# 컨테이너가 방금 떴으면 admin API가 아직 안 열렸을 수 있어 잠깐 재시도한다.
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

	# 6. 살아났는지 확인한다. 여기서 실패하면 CD 워크플로가 빨간불이 된다.
	if ! wait_for_http "백엔드" http://127.0.0.1:8080/actuator/health '"status":"UP"'; then
		"${compose[@]}" logs --tail 50 backend || true
		die "백엔드가 90초 안에 준비되지 않았다"
	fi
	if ! wait_for_http "프론트엔드" http://127.0.0.1:3000/usage '<!doctype html'; then
		"${compose[@]}" logs --tail 50 frontend || true
		die "프론트엔드가 90초 안에 준비되지 않았다"
	fi

	# 7. 오래된 이미지를 치운다. EBS가 20GB뿐이고 배포마다 이미지가 쌓인다.
	#    지금 쓰는 이미지는 대상이 아니고, 지워진 옛 이미지도 ECR에 남아 있어 롤백에 지장이 없다.
	docker image prune -af --filter "until=336h" >/dev/null 2>&1 || true

	log "배포 성공: $sha"
}

main "$@"
