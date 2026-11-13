#!/usr/bin/env bash
# SUT EC2 안에서 root로 돈다. 카프카 이미지의 명령줄 도구로 브로커를 읽기만 한다.
# 브로커 보안 그룹이 SUT 서버만 들이므로 여기서 돌린다. 앱 이미지에는 카프카 도구가 없다.
# 표준 입력으로 받아 실행되므로 본문은 main()에 두고 맨 끝에서 한 번 부른다.

set -euo pipefail

readonly ENV_FILE=/etc/meterengine/prod.env
# 브로커(3.9)와 같은 버전의 도구를 쓴다.
readonly KAFKA_IMAGE=apache/kafka:3.9.1
readonly KAFKA_BIN=/opt/kafka/bin
readonly CLIENT_CONFIG_IN_CONTAINER=/tmp/client.properties
readonly CONSUMER_GROUP=event-ingest
readonly INGEST_TOPIC=event.ingest
readonly DEAD_LETTER_TOPIC=event.dlq
readonly DEAD_LETTER_PARTITION=0
readonly DEAD_LETTER_READ_TIMEOUT_MS=10000

client_config=""

log() { printf '[kafka-tool] %s\n' "$*"; }
die() {
	printf '[kafka-tool] 실패: %s\n' "$*" >&2
	exit 1
}

env_value() {
	local value
	value=$(grep "^$1=" "$ENV_FILE" | head -n 1 | cut -d= -f2-) || true
	[ -n "$value" ] || die "$ENV_FILE 에 $1 이 없다. 카프카를 켠 뒤 배포한 서버에서 부른다"
	printf '%s\n' "$value"
}

kafka() {
	local tool="$1"
	shift
	docker run --rm --network host -v "$client_config:$CLIENT_CONFIG_IN_CONTAINER:ro" \
		"$KAFKA_IMAGE" "$KAFKA_BIN/$tool" "$@"
}

main() {
	[ $# -ge 1 ] || die "사용법: kafka-tool.sh lag|topics|dlq [건수]"
	local bootstrap protocol
	bootstrap=$(env_value KAFKA_BOOTSTRAP_SERVERS)
	protocol=$(env_value KAFKA_SECURITY_PROTOCOL)
	client_config=$(mktemp)
	trap 'rm -f "$client_config"' EXIT
	printf 'security.protocol=%s\n' "$protocol" >"$client_config"
	chmod 644 "$client_config"

	case "$1" in
	# 파티션마다 어디까지 읽었고 얼마나 밀렸는지, 어느 스레드가 맡았는지.
	lag)
		kafka kafka-consumer-groups.sh --bootstrap-server "$bootstrap" --command-config "$CLIENT_CONFIG_IN_CONTAINER" \
			--describe --group "$CONSUMER_GROUP"
		;;
	# 토픽의 파티션, 리더, 복제본과 토픽에 따로 건 설정.
	topics)
		kafka kafka-topics.sh --bootstrap-server "$bootstrap" --command-config "$CLIENT_CONFIG_IN_CONTAINER" \
			--describe --exclude-internal
		kafka kafka-configs.sh --bootstrap-server "$bootstrap" --command-config "$CLIENT_CONFIG_IN_CONTAINER" \
			--describe --entity-type topics --entity-name "$INGEST_TOPIC"
		kafka kafka-configs.sh --bootstrap-server "$bootstrap" --command-config "$CLIENT_CONFIG_IN_CONTAINER" \
			--describe --entity-type topics --entity-name "$DEAD_LETTER_TOPIC"
		;;
	# DLQ의 앞에서부터 N건. 파티션을 직접 짚어 읽으므로 컨슈머 그룹을 만들지 않고 오프셋도 남기지 않는다.
	dlq)
		[[ "${2:-}" =~ ^[1-9][0-9]*$ ]] || die "사용법: kafka-tool.sh dlq <건수>"
		kafka kafka-console-consumer.sh --bootstrap-server "$bootstrap" --consumer.config "$CLIENT_CONFIG_IN_CONTAINER" \
			--topic "$DEAD_LETTER_TOPIC" --partition "$DEAD_LETTER_PARTITION" --offset earliest \
			--max-messages "$2" --timeout-ms "$DEAD_LETTER_READ_TIMEOUT_MS" \
			--property print.timestamp=true --property print.key=true --property print.headers=true ||
			log "읽을 메시지가 $2 건보다 적으면 시간 초과로 끝난다. 위에 찍힌 것이 전부다"
		;;
	*) die "사용법: kafka-tool.sh lag|topics|dlq [건수]" ;;
	esac
}

main "$@"
