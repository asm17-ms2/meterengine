#!/usr/bin/env bash
# 공용 모니터링 서버 안에서 root로 돈다. Prometheus가 읽는 대상 파일에 MSK 브로커 주소를 쓰거나, 주소가 없으면 지운다.
# Prometheus는 이 디렉터리의 파일이 바뀌면 스스로 다시 읽는다.
# 표준 입력으로 받아 실행되므로 본문은 main()에 두고 맨 끝에서 한 번 부른다.

set -euo pipefail

readonly TARGETS_DIR=/etc/meterengine/prometheus-targets
readonly JMX_PORT=11001
readonly NODE_PORT=11002

log() { printf '[kafka-monitoring-targets] %s\n' "$*"; }
die() {
	printf '[kafka-monitoring-targets] 실패: %s\n' "$*" >&2
	exit 1
}

# 파일 이름과 JSON에 그대로 들어가는 값이다.
require_name() {
	[[ "$2" =~ ^[a-z0-9][a-z0-9.-]*$ ]] || die "$1 에 허용하지 않는 문자가 있다: $2"
}

target_file() {
	local env="$1" port="$2" target targets=""
	shift 2
	for target in "$@"; do
		targets="$targets${targets:+, }\"$target:$port\""
	done
	printf '[{"targets": [%s], "labels": {"env": "%s"}}]\n' "$targets" "$env"
}

# 반쯤 쓴 파일을 Prometheus가 읽지 않게 옆에 다 쓴 뒤 이름을 바꾼다.
write_file() {
	local path="$1" content="$2" tmp
	tmp=$(mktemp "$path.XXXXXX")
	printf '%s' "$content" >"$tmp"
	chmod 644 "$tmp"
	mv -f "$tmp" "$path"
}

main() {
	[ $# -ge 1 ] || die "사용법: write-prometheus-targets.sh <환경 이름> [브로커 호스트...]"
	local env="$1" host
	shift
	require_name "환경 이름" "$env"
	for host in "$@"; do
		require_name "브로커 호스트" "$host"
	done

	local jmx_file="$TARGETS_DIR/kafka-jmx-$env.json" node_file="$TARGETS_DIR/kafka-node-$env.json"
	if [ $# -eq 0 ]; then
		rm -f "$jmx_file" "$node_file"
		log "$env 의 브로커 대상 파일을 지웠다"
		return 0
	fi

	mkdir -p "$TARGETS_DIR"
	chmod 755 "$TARGETS_DIR"
	write_file "$jmx_file" "$(target_file "$env" "$JMX_PORT" "$@")"
	write_file "$node_file" "$(target_file "$env" "$NODE_PORT" "$@")"
	log "$env 의 브로커 $# 대를 대상 파일에 썼다: $jmx_file, $node_file"
}

main "$@"
