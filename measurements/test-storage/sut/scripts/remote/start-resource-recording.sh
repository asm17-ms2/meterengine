#!/usr/bin/env bash
# SUT EC2 안에서 root로 돈다. 컨테이너별 cgroup CPU와 메모리, /proc/stat와 /proc/meminfo, 백엔드 JVM 힙과 GC를 10초마다 쌓는 기록을 systemd 단위로 띄운다.
# 표준 입력으로 받아 실행되므로 본문은 main()에 두고 맨 끝에서 한 번 부른다.

set -euo pipefail

readonly COMPOSE_PROJECT=meterengine-prod
readonly SAMPLE_SECONDS=10
readonly RESULT_DIR=/var/tmp/results
readonly UNIT_PREFIX=meterengine-resource-record
readonly SYSTEM_UNITS="docker.service containerd.service amazon-ssm-agent.service"
readonly MICROSECONDS_PER_SECOND=1000000
readonly FIRST_ROUND_WAIT_SECONDS=3
# 부하 발생기가 같은 RUN_ID를 다시 쓰므로 k6 lib/config.js의 RUN_ID_PATTERN과 같게 둔다.
readonly RUN_ID_PATTERN='^[A-Za-z0-9_-]{1,64}$'
readonly BYTES_PER_KIB=1024
readonly BACKEND_PORT=8080
# 백엔드는 호스트 포트를 열지 않아 컨테이너 IP로 읽고, 응답이 수십 KB라 힙과 GC 묶음만 받는다.
readonly JVM_METRICS_PATH='/actuator/prometheus?includedNames=jvm_memory_used_bytes,jvm_memory_max_bytes,jvm_gc_pause_seconds,jvm_gc_pause_seconds_max,jvm_gc_concurrent_phase_time_seconds'
readonly JVM_CONNECT_TIMEOUT_SECONDS=1
# 두 색이 다 답하지 못해도 회차가 보고서의 표본 간격 한도 안에 끝나게 묶는다.
readonly JVM_READ_TIMEOUT_SECONDS=2
readonly MEMORY_ROUND_WAIT_SECONDS=10

log() { printf '[start-resource-recording] %s\n' "$*"; }
die() { printf '[start-resource-recording] 실패: %s\n' "$*" >&2; exit 1; }

# cpu.stat 첫 줄이 usage_usec라 프로세스를 띄우지 않고 읽는다.
cpu_usage() {
	local key value
	{ read -r key value <"/sys/fs/cgroup$1/cpu.stat"; } 2>/dev/null && [ "$key" = usage_usec ] && printf '%s' "$value"
}

# 회차마다 한 번만 물어 CPU와 메모리 기록이 같은 컨테이너 목록을 쓴다. 비어도 되는 IP를 맨 뒤에 둔다.
inspect_containers() {
	local ids
	ids=$(docker ps -q --no-trunc --filter "label=com.docker.compose.project=$COMPOSE_PROJECT") || return 1
	[ -n "$ids" ] || return 0
	docker inspect -f '{{.Id}}{{"\t"}}{{index .Config.Labels "com.docker.compose.service"}}{{"\t"}}{{.State.Pid}}{{"\t"}}{{.RestartCount}}{{"\t"}}{{if .State.OOMKilled}}1{{else}}0{{end}}{{"\t"}}{{if .State.Restarting}}1{{else}}0{{end}}{{"\t"}}{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' $ids 2>/dev/null || true
}

# cgroup 드라이버(systemd, cgroupfs)마다 경로가 달라 주 프로세스의 /proc/<pid>/cgroup에서 읽는다.
record_round() {
	local now="$1" usec_per_tick="$2" label user nice system idle iowait irq softirq steal rest field id name pid cgroup usage unit
	read -r label user nice system idle iowait irq softirq steal rest </proc/stat
	for field in user nice system idle iowait irq softirq steal; do
		printf '%s\thost\t%s\t-\t-\t%s\n' "$now" "$field" "$((${!field} * usec_per_tick))"
	done
	while IFS=$'\t' read -r id name pid rest; do
		[ "${pid:-0}" -gt 0 ] || continue
		cgroup=$(sed -n 's/^0:://p' "/proc/$pid/cgroup" 2>/dev/null) && [ -n "$cgroup" ] || continue
		usage=$(cpu_usage "$cgroup") || continue
		printf '%s\tcontainer\t%s\t%s\t%s\t%s\n' "$now" "$name" "${id:0:12}" "$pid" "$usage"
	done <<<"$3"
	for unit in $SYSTEM_UNITS; do
		usage=$(cpu_usage "/system.slice/$unit") || continue
		printf '%s\tunit\t%s\t-\t-\t%s\n' "$now" "$unit" "$usage"
	done
	if usage=$(cpu_usage "$(sed -n 's/^0:://p' /proc/$$/cgroup)"); then
		printf '%s\tunit\tsampler\t-\t-\t%s\n' "$now" "$usage"
	fi
}

# kB를 cgroup 값과 같은 바이트로 바꿔 남기고, 프로세스는 띄우지 않는다.
host_memory_rows() {
	local now="$1" key value rest
	while read -r key value rest; do
		case "$key" in
		MemTotal: | MemAvailable: | SwapTotal:) printf '%s\thost\t-\t-\t-\t%s\t%s\n' "$now" "${key%:}" "$((value * BYTES_PER_KIB))" ;;
		SwapFree:)
			printf '%s\thost\t-\t-\t-\tSwapFree\t%s\n' "$now" "$((value * BYTES_PER_KIB))"
			break
			;;
		esac
	done </proc/meminfo
	while read -r key value; do
		[ "$key" = oom_kill ] || continue
		printf '%s\thost\t-\t-\t-\tvmstat_oom_kill\t%s\n' "$now" "$value"
		break
	done </proc/vmstat
	return 0
}

# 없는 파일(커널 5.19 전 memory.peak 등)은 그 줄만 빠지고 회차는 남는다.
cgroup_memory_rows() {
	local prefix="$1" dir="$2" file key value
	for file in current peak max; do
		{ read -r value <"$dir/memory.$file"; } 2>/dev/null || continue
		printf '%s\tmemory.%s\t%s\n' "$prefix" "$file" "$value"
	done
	if [ -r "$dir/memory.events" ]; then
		while read -r key value; do printf '%s\tmemory.events.%s\t%s\n' "$prefix" "$key" "$value"; done <"$dir/memory.events"
	fi
	if [ -r "$dir/memory.stat" ]; then
		while read -r key value; do
			case "$key" in anon | file | inactive_file) printf '%s\tmemory.stat.%s\t%s\n' "$prefix" "$key" "$value" ;; esac
		done <"$dir/memory.stat"
	fi
	return 0
}

# 답하지 못한 회차도 무너진 증거라 건너뛰지 않고 scrape_error로 남긴다.
jvm_rows() {
	local prefix="$1" ip="$2" started body status line
	if [ -z "$ip" ]; then
		printf '%s\tscrape_error\t-1\n' "$prefix"
		return 0
	fi
	started=${EPOCHREALTIME//[!0-9]/}
	body=$(curl -fsS --connect-timeout "$JVM_CONNECT_TIMEOUT_SECONDS" --max-time "$JVM_READ_TIMEOUT_SECONDS" "http://$ip:$BACKEND_PORT$JVM_METRICS_PATH" 2>/dev/null) && status=0 || status=$?
	if [ "$status" -ne 0 ]; then
		printf '%s\tscrape_error\t%s\n' "$prefix" "$status"
		return 0
	fi
	printf '%s\tscrape_usec\t%s\n' "$prefix" "$((${EPOCHREALTIME//[!0-9]/} - started))"
	while IFS= read -r line; do
		[[ $line == jvm_* ]] || continue
		printf '%s\t%s\t%s\n' "$prefix" "${line% *}" "${line##* }"
	done <<<"$body"
	return 0
}

# 컨테이너 메모리를 다 읽은 뒤 JVM을 읽어 curl 대기가 다른 값을 늦추지 않게 한다. 재시작 중(pid 0)에도 docker 줄은 남긴다.
record_memory_round() {
	local now="$1" id name pid restarts oom_killed restarting ips cid prefix cgroup i
	local -a jvm_prefix=() jvm_ip=()
	host_memory_rows "$now"
	while IFS=$'\t' read -r id name pid restarts oom_killed restarting ips; do
		[ -n "$id" ] || continue
		cid=${id:0:12}
		printf '%s\tdocker\t%s\t%s\t-\trestart_count\t%s\n' "$now" "$name" "$cid" "$restarts"
		printf '%s\tdocker\t%s\t%s\t-\toom_killed\t%s\n' "$now" "$name" "$cid" "$oom_killed"
		printf '%s\tdocker\t%s\t%s\t-\trestarting\t%s\n' "$now" "$name" "$cid" "$restarting"
		[ "${pid:-0}" -gt 0 ] || continue
		prefix="$now"$'\t'container$'\t'"$name"$'\t'"$cid"$'\t'"$pid"
		cgroup=$(sed -n 's/^0:://p' "/proc/$pid/cgroup" 2>/dev/null) && [ -n "$cgroup" ] && cgroup_memory_rows "$prefix" "/sys/fs/cgroup$cgroup"
		case "$name" in
		backend | backend-blue | backend-green)
			jvm_prefix+=("$now"$'\t'jvm$'\t'"$name"$'\t'"$cid"$'\t'"$pid")
			jvm_ip+=("${ips%% *}")
			;;
		esac
	done <<<"$2"
	for i in "${!jvm_ip[@]}"; do jvm_rows "${jvm_prefix[$i]}" "${jvm_ip[$i]}"; done
	return 0
}

# 한 회차를 다 읽은 뒤 한 번에 덧붙여, 반쯤 읽은 회차가 파일에 남지 않게 한다. 두 파일은 같은 시각과 같은 컨테이너 목록을 쓴다.
record_rounds() {
	local cpu_file="$1" memory_file="$2" usec_per_tick="$3" inspected now rows
	while :; do
		if inspected=$(inspect_containers); then
			now=$(date +%s%3N)
			rows=$(record_round "$now" "$usec_per_tick" "$inspected") && printf '%s\n' "$rows" >>"$cpu_file"
			rows=$(record_memory_round "$now" "$inspected") && printf '%s\n' "$rows" >>"$memory_file"
		fi
		sleep "$SAMPLE_SECONDS"
	done
}

write_info() {
	local unit="$1" seconds="$2"
	printf 'started_kst: %s\n' "$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S')"
	printf 'unit: %s\nrecord_seconds: %s\n' "$unit" "$seconds"
	printf 'kernel: %s\nnproc: %s\nclk_tck: %s\n' "$(uname -r)" "$(nproc)" "$(getconf CLK_TCK)"
	printf 'docker: %s\n' "$(docker info -f '{{.ServerVersion}} {{.CgroupDriver}} cgroup v{{.CgroupVersion}}')"
	printf 'irq_time_accounting: %s\n' "$(grep -h '^CONFIG_IRQ_TIME_ACCOUNTING' "/boot/config-$(uname -r)" 2>/dev/null || echo 없음)"
	printf 'instance: %s\n' "$(cat /sys/devices/virtual/dmi/id/board_asset_tag 2>/dev/null || hostname)"
}

main() {
	[ $# -eq 2 ] || die "사용법: start-resource-recording.sh <RUN_ID> <기록 초>"
	local run_id="$1" seconds="$2" unit cpu_file memory_file clock_ticks body i
	[[ "$run_id" =~ $RUN_ID_PATTERN ]] || die "RUN_ID는 영문, 숫자, _, - 로 64자 이하여야 한다: $run_id"
	[[ "$seconds" =~ ^[1-9][0-9]*$ ]] || die "기록 초가 양의 정수가 아니다: $seconds"
	[ -f /sys/fs/cgroup/cgroup.controllers ] || die "cgroup v2가 아니다. 이 기록은 cgroup v2만 읽는다"
	command -v curl >/dev/null || die "curl이 없다. 백엔드 JVM 힙과 GC를 읽지 못한다"
	unit="$UNIT_PREFIX-$run_id"
	cpu_file="$RESULT_DIR/$run_id-sut-cpu.tsv"
	memory_file="$RESULT_DIR/$run_id-sut-memory.tsv"
	! systemctl list-units --plain --no-legend --state=active "$UNIT_PREFIX-*" | grep -q . ||
		die "다른 자원 기록이 돌고 있다. SUT에서 sudo systemctl stop $UNIT_PREFIX-<RUN_ID> 로 먼저 멈춘다"
	[ ! -e "$cpu_file" ] || die "$cpu_file 이 이미 있다. RUN_ID를 바꾼다"
	[ ! -e "$memory_file" ] || die "$memory_file 이 이미 있다. RUN_ID를 바꾼다"
	clock_ticks=$(getconf CLK_TCK)
	[ $((MICROSECONDS_PER_SECOND % clock_ticks)) -eq 0 ] || die "CLK_TCK $clock_ticks 로 마이크로초를 나눠떨어지게 바꿀 수 없다"

	mkdir -p "$RESULT_DIR"
	write_info "$unit" "$seconds" | tee "$RESULT_DIR/$run_id-sut-info.txt"
	printf 'epoch_ms\tkind\tname\tcontainer_id\tpid\tusage_usec\n' >"$cpu_file"
	printf 'epoch_ms\tkind\tname\tcontainer_id\tpid\tmetric\tvalue\n' >"$memory_file"
	body="$(declare -p COMPOSE_PROJECT SAMPLE_SECONDS SYSTEM_UNITS BYTES_PER_KIB BACKEND_PORT JVM_METRICS_PATH JVM_CONNECT_TIMEOUT_SECONDS JVM_READ_TIMEOUT_SECONDS); $(declare -f cpu_usage inspect_containers record_round host_memory_rows cgroup_memory_rows jvm_rows record_memory_round record_rounds); record_rounds \"\$@\""
	# systemd가 서비스 명령 줄의 ${...}와 $$를 먼저 풀어 버려, $를 모두 $$로 바꿔 넘긴다.
	# 설명을 주지 않으면 여러 줄 명령이 설명이 되어 daemon-reload 뒤 단위가 깨지고 RuntimeMaxSec도 사라진다.
	systemd-run --quiet --collect --unit="$unit" --description="컨테이너별 CPU와 메모리 기록 $run_id" \
		--property=RuntimeMaxSec="$seconds" bash -c "${body//\$/\$\$}" \
		start-resource-recording "$cpu_file" "$memory_file" "$((MICROSECONDS_PER_SECOND / clock_ticks))"
	sleep "$FIRST_ROUND_WAIT_SECONDS"
	systemctl is-active --quiet "$unit" || { rm -f "$cpu_file" "$memory_file"; die "기록이 바로 멈췄다. SUT에서 sudo journalctl -u $unit 로 본다"; }
	if ! grep -q "$(printf '\tcontainer\t')" "$cpu_file"; then
		systemctl stop "$unit"
		rm -f "$cpu_file" "$memory_file"
		die "$COMPOSE_PROJECT 컨테이너를 하나도 읽지 못했다. 배포했는지 본다"
	fi
	# 첫 메모리 회차는 curl까지 끝나야 덧붙여져 CPU 회차보다 늦다.
	for ((i = 0; i < MEMORY_ROUND_WAIT_SECONDS; i++)); do
		grep -q "$(printf '\tMemTotal\t')" "$memory_file" && break
		sleep 1
	done
	if ! grep -q "$(printf '\tmemory.current\t')" "$memory_file"; then
		systemctl stop "$unit"
		rm -f "$cpu_file" "$memory_file"
		die "컨테이너 메모리 값을 읽지 못했다. SUT에서 sudo journalctl -u $unit 로 본다"
	fi
	grep -q "$(printf '\tjvm_memory_used_bytes{area="heap"')" "$memory_file" ||
		log "백엔드 JVM 값을 읽지 못했다. 기록은 계속하고, 힙과 GC는 README의 Prometheus 질의로 본다"
	log "기록 중: $cpu_file, $memory_file. ${seconds}초 뒤 스스로 멈춘다"
}

main "$@"
