#!/usr/bin/env bash
# 공용 모니터링 서버에서 root로 돈다. 저장소를 먼저 <SHA>로 체크아웃한 뒤 부른다.
# 스스로 체크아웃하지 않는다. 도는 중에 이 파일이 바뀌면 bash가 바뀐 내용을 이어 읽는다.

set -euo pipefail

readonly REPO_DIR=/opt/meterengine
readonly ENV_FILE=/etc/meterengine/monitoring.env
readonly LOCK_FILE=/etc/meterengine/monitoring.lock
readonly PARAMETERS_FILE=$REPO_DIR/deploy/monitoring/parameters.json
readonly COMPOSE_FILE=$REPO_DIR/deploy/compose.monitoring.yml
readonly REGION=ap-northeast-2
# Terraform이 비밀 파라미터를 만들 때 넣는 값이다. 사람이 실제 값을 넣기 전에는 띄우지 않는다.
readonly PLACEHOLDER=unset
readonly MONITORING_GROUP=monitoring
# 파라미터가 없는 환경을 건너뛰라는 env_lines의 반환 코드다.
readonly MISSING_PARAMETERS=3
readonly PROMETHEUS_READY_URL=http://127.0.0.1:9090/-/ready
readonly PROMETHEUS_READY_ATTEMPTS=60
readonly PROMETHEUS_READY_INTERVAL_SECONDS=2

# EXIT 처리기도 읽으므로 main 밖에 둔다.
new_env=

die() { printf '[monitoring] 실패: %s\n' "$*" >&2; exit 1; }
log() { printf '[monitoring] %s\n' "$*"; }

# 묶음마다 "묶음 이름 파라미터 이름..." 한 줄을 찍는다. GetParameters가 한 번에 10개까지라 묶음별로 부른다.
parameter_groups() {
	python3 -c '
import json, sys
spec = json.load(open(sys.argv[1]))
prefix = spec["path_prefix"]
groups = {sys.argv[2]: spec["monitoring"], **{env: spec["environment_parameters"] for env in spec["environments"]}}
for group, names in groups.items():
    print(group, *(f"{prefix}/{group}/{name}" for name in names))
' "$PARAMETERS_FILE" "$MONITORING_GROUP"
}

# get-parameters 응답을 env 파일 줄로 바꾼다. 환경 묶음은 변수 이름 앞에 환경 이름을 붙인다.
env_lines() {
	python3 -c '
import json, sys
group, monitoring, placeholder, missing = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
response = json.load(sys.stdin)
if response.get("InvalidParameters"):
    print("없는 파라미터: " + " ".join(response["InvalidParameters"]), file=sys.stderr)
    sys.exit(missing)
prefix = "" if group == monitoring else group.upper().replace("-", "_") + "_"
for parameter in response["Parameters"]:
    if parameter["Value"] == placeholder or "\n" in parameter["Value"]:
        sys.exit(parameter["Name"] + " 값이 자리 값이거나 여러 줄이다. put-parameter --overwrite로 실제 값을 넣는다")
    print(prefix + parameter["Name"].rsplit("/", 1)[1].upper().replace("-", "_") + "=" + parameter["Value"])
' "$1" "$MONITORING_GROUP" "$PLACEHOLDER" "$MISSING_PARAMETERS"
}

# 막 다시 만든 Prometheus는 신호 처리기를 걸기 전이라 SIGHUP에 죽는다(exit 129). 준비된 뒤에 보낸다.
wait_prometheus_ready() {
	local attempt
	for ((attempt = 1; attempt <= PROMETHEUS_READY_ATTEMPTS; attempt++)); do
		curl -fsS -o /dev/null "$PROMETHEUS_READY_URL" && return 0
		sleep "$PROMETHEUS_READY_INTERVAL_SECONDS"
	done
	die "Prometheus가 $((PROMETHEUS_READY_ATTEMPTS * PROMETHEUS_READY_INTERVAL_SECONDS))초 안에 준비되지 않아 설정을 다시 읽히지 않았다"
}

main() {
	[ $# -eq 1 ] && [[ $1 =~ ^[0-9a-f]{40}$ ]] || die "사용법: start.sh <40자 커밋 SHA>"
	local sha=$1
	exec 9>"$LOCK_FILE"
	flock -n 9 || die "다른 start.sh가 아직 돌고 있다"
	[ "$(git -C "$REPO_DIR" rev-parse HEAD)" = "$sha" ] || die "저장소가 $sha 가 아니다. git checkout --force $sha 뒤에 부른다"

	local group names response status
	local services=(prometheus grafana cloudwatch-exporter) skipped=()
	# 환경 파일만 root 전용으로 만든다. umask를 스크립트 전체에 걸면 Prometheus가 설정 디렉터리를 못 연다.
	new_env=$(mktemp "$ENV_FILE.XXXXXX")
	trap 'rm -f "$new_env"' EXIT
	chmod 600 "$new_env"
	while read -r group names; do
		# shellcheck disable=SC2086
		response=$(aws ssm get-parameters --region "$REGION" --with-decryption --output json --names $names)
		status=0
		env_lines "$group" <<<"$response" >>"$new_env" || status=$?
		if [ "$status" -eq 0 ]; then
			[ "$group" = "$MONITORING_GROUP" ] || services+=("postgres-exporter-$group")
		elif [ "$status" -eq "$MISSING_PARAMETERS" ] && [ "$group" != "$MONITORING_GROUP" ]; then
			log "$group 파라미터가 다 있지 않아 postgres-exporter-$group 를 띄우지 않는다"
			skipped+=("postgres-exporter-$group")
		else
			die "$group 파라미터를 읽지 못했다"
		fi
	done < <(parameter_groups)
	mv -f "$new_env" "$ENV_FILE"

	local compose=(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE")
	"${compose[@]}" up -d "${services[@]}"
	[ ${#skipped[@]} -eq 0 ] || "${compose[@]}" rm --stop --force "${skipped[@]}"
	# 설정 디렉터리만 바뀌면 up -d가 컨테이너를 다시 만들지 않아 직접 다시 읽힌다.
	wait_prometheus_ready
	"${compose[@]}" kill -s SIGHUP prometheus
	# 기본 인증을 꺼서 provisioning 다시 읽기 API를 부를 수 없다. 대시보드와 경보는 재시작으로 다시 읽힌다.
	"${compose[@]}" restart grafana
	log "띄웠다: ${services[*]}"
}

main "$@"
