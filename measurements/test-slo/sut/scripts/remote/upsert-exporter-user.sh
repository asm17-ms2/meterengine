#!/usr/bin/env bash
# SUT EC2 안에서 root로 돈다. SUT RDS에 pg_monitor 권한의 meterengine_exporter 사용자를 만들거나 비밀번호를 맞춘다.
# 비밀번호가 로그에 남으므로 set -x를 켜지 않는다.
# 표준 입력으로 받아 실행되므로 본문은 main()에 두고 맨 끝에서 한 번 부른다.

set -euo pipefail

export AWS_REGION=ap-northeast-2

readonly DEV_SERVER_PARAM_PATH=/meterengine/prod
readonly EXPORTER_ROLE=meterengine_exporter
readonly POSTGRES_CLIENT_IMAGE=postgres:18
readonly EXPECTED_ROLE_ROW="$EXPORTER_ROLE|t|t"

log() { printf '[create-exporter-user] %s\n' "$*"; }
die() {
	printf '[create-exporter-user] 실패: %s\n' "$*" >&2
	exit 1
}

db_host=""
db_port=""
db_name=""
db_username=""
db_password=""
db_exporter_password=""

require_sut_param_path() {
	local param_path="$1"
	[ -n "$param_path" ] || die "파라미터 경로를 인자로 줘야 한다. 예: /meterengine/test-slo"
	case "$param_path" in
	"$DEV_SERVER_PARAM_PATH" | "$DEV_SERVER_PARAM_PATH"/*) die "개발 서버 경로 $DEV_SERVER_PARAM_PATH 는 쓰지 않는다" ;;
	esac
	[[ "$param_path" =~ ^/meterengine/[a-z0-9-]+$ ]] || die "파라미터 경로 형식이 아니다: $param_path"
}

read_parameters() {
	local param_path="$1" parameters name value
	parameters=$(aws ssm get-parameters-by-path \
		--path "$param_path" \
		--with-decryption \
		--query 'Parameters[].[Name,Value]' \
		--output text) || die "$param_path 파라미터를 읽지 못했다"

	while IFS=$'\t' read -r name value; do
		case "${name##*/}" in
		db-host) db_host="$value" ;;
		db-port) db_port="$value" ;;
		db-name) db_name="$value" ;;
		db-username) db_username="$value" ;;
		db-password) db_password="$value" ;;
		db-exporter-password) db_exporter_password="$value" ;;
		esac
	done <<<"$parameters"

	local required variable
	for required in db-host db-port db-name db-username db-password db-exporter-password; do
		variable="${required//-/_}"
		[ -n "${!variable}" ] || die "$param_path/$required 가 없다"
	done
}

# 접속 문자열에 그대로 들어가므로 공백과 따옴표를 막는다.
require_connection_values() {
	[[ "$db_host" =~ ^[A-Za-z0-9.-]+$ ]] || die "db-host 형식이 아니다: $db_host"
	[[ "$db_port" =~ ^[0-9]+$ ]] || die "db-port 형식이 아니다: $db_port"
	[[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || die "db-name 형식이 아니다: $db_name"
	[[ "$db_username" =~ ^[A-Za-z0-9_]+$ ]] || die "db-username 형식이 아니다: $db_username"
}

# 비밀번호는 docker -e 에 이름만 넘겨 호스트 환경에서 받게 하고, psql의 \getenv로 읽는다.
apply_exporter_role() {
	PGPASSWORD="$db_password" EXPORTER_PASSWORD="$db_exporter_password" \
		docker run --rm -i -e PGPASSWORD -e EXPORTER_PASSWORD "$POSTGRES_CLIENT_IMAGE" \
		psql "host=$db_host port=$db_port dbname=$db_name user=$db_username sslmode=require" \
		-X -q -A -t -v ON_ERROR_STOP=1 <<'SQL'
\getenv exporter_password EXPORTER_PASSWORD
SELECT format(
	CASE
		WHEN EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'meterengine_exporter')
			THEN 'ALTER USER meterengine_exporter WITH LOGIN PASSWORD %L'
		ELSE 'CREATE USER meterengine_exporter WITH PASSWORD %L'
	END,
	:'exporter_password')
\gexec
GRANT pg_monitor TO meterengine_exporter;
SELECT rolname, rolcanlogin, pg_has_role(rolname, 'pg_monitor', 'MEMBER')
FROM pg_roles
WHERE rolname = 'meterengine_exporter';
SQL
}

restart_postgres_exporter() {
	local containers container
	containers=$(docker ps --filter name=postgres-exporter --format '{{.Names}}') ||
		die "docker ps 가 실패했다"
	if [ -z "$containers" ]; then
		log "postgres-exporter 컨테이너가 없다. sut-deploy.sh 를 먼저 돌려라. 배포하면 이 사용자로 붙는다"
		return 0
	fi
	while read -r container; do
		docker restart "$container" >/dev/null || die "$container 재시작이 실패했다"
		log "$container 를 재시작했다"
	done <<<"$containers"
}

main() {
	local param_path="${1:-}"
	require_sut_param_path "$param_path"
	command -v docker >/dev/null 2>&1 || die "docker가 없다"

	read_parameters "$param_path"
	require_connection_values
	log "DB: $db_host:$db_port/$db_name, 마스터 사용자: $db_username"

	local role_row
	role_row=$(apply_exporter_role </dev/null) || die "exporter 사용자를 맞추지 못했다"
	role_row=$(printf '%s\n' "$role_row" | tail -n 1)
	[ "$role_row" = "$EXPECTED_ROLE_ROW" ] ||
		die "확인 결과가 기대와 다르다(받은 값: ${role_row:-없음}, 기대: $EXPECTED_ROLE_ROW)"
	log "$EXPORTER_ROLE 로그인 가능, pg_monitor 멤버 확인"

	restart_postgres_exporter
}

main "$@"
