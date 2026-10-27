#!/usr/bin/env bash
# SUT EC2 안에서 root로 돈다. 컨테이너별 cgroup CPU와 /proc/stat 누적값을 10초마다 쌓는 기록을 systemd 단위로 띄운다.
# 표준 입력으로 받아 실행되므로 본문은 main()에 두고 맨 끝에서 한 번 부른다.

set -euo pipefail

readonly COMPOSE_PROJECT=meterengine-prod
readonly SAMPLE_SECONDS=10
readonly RESULT_DIR=/var/tmp/results
readonly UNIT_PREFIX=meterengine-cpu-record
readonly SYSTEM_UNITS="docker.service containerd.service amazon-ssm-agent.service"
readonly MICROSECONDS_PER_SECOND=1000000
readonly FIRST_ROUND_WAIT_SECONDS=3
# 부하 발생기가 같은 RUN_ID를 다시 쓰므로 k6 lib/config.js의 RUN_ID_PATTERN과 같게 둔다.
readonly RUN_ID_PATTERN='^[A-Za-z0-9_-]{1,64}$'

log() { printf '[start-cpu-recording] %s\n' "$*"; }
die() { printf '[start-cpu-recording] 실패: %s\n' "$*" >&2; exit 1; }

# cpu.stat 첫 줄이 usage_usec라 프로세스를 띄우지 않고 읽는다.
cpu_usage() {
	local key value
	{ read -r key value <"/sys/fs/cgroup$1/cpu.stat"; } 2>/dev/null && [ "$key" = usage_usec ] && printf '%s' "$value"
}

# cgroup 드라이버(systemd, cgroupfs)마다 경로가 달라 주 프로세스의 /proc/<pid>/cgroup에서 읽는다.
record_round() {
	local usec_per_tick="$1" ids inspected="" now label user nice system idle iowait irq softirq steal rest field id name pid cgroup usage unit
	ids=$(docker ps -q --no-trunc --filter "label=com.docker.compose.project=$COMPOSE_PROJECT") || return 1
	if [ -n "$ids" ]; then
		inspected=$(docker inspect -f '{{.Id}}{{"\t"}}{{index .Config.Labels "com.docker.compose.service"}}{{"\t"}}{{.State.Pid}}' $ids 2>/dev/null) || true
	fi
	now=$(date +%s%3N)
	read -r label user nice system idle iowait irq softirq steal rest </proc/stat
	for field in user nice system idle iowait irq softirq steal; do
		printf '%s\thost\t%s\t-\t-\t%s\n' "$now" "$field" "$((${!field} * usec_per_tick))"
	done
	while IFS=$'\t' read -r id name pid; do
		[ "${pid:-0}" -gt 0 ] || continue
		cgroup=$(sed -n 's/^0:://p' "/proc/$pid/cgroup" 2>/dev/null) && [ -n "$cgroup" ] || continue
		usage=$(cpu_usage "$cgroup") || continue
		printf '%s\tcontainer\t%s\t%s\t%s\t%s\n' "$now" "$name" "${id:0:12}" "$pid" "$usage"
	done <<<"$inspected"
	for unit in $SYSTEM_UNITS; do
		usage=$(cpu_usage "/system.slice/$unit") || continue
		printf '%s\tunit\t%s\t-\t-\t%s\n' "$now" "$unit" "$usage"
	done
	if usage=$(cpu_usage "$(sed -n 's/^0:://p' /proc/$$/cgroup)"); then
		printf '%s\tunit\tsampler\t-\t-\t%s\n' "$now" "$usage"
	fi
}

# 한 회차를 다 읽은 뒤 한 번에 덧붙여, 반쯤 읽은 회차가 파일에 남지 않게 한다.
record_rounds() {
	local file="$1" usec_per_tick="$2" rows
	while :; do
		rows=$(record_round "$usec_per_tick") && printf '%s\n' "$rows" >>"$file"
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
}

main() {
	[ $# -eq 2 ] || die "사용법: start-cpu-recording.sh <RUN_ID> <기록 초>"
	local run_id="$1" seconds="$2" unit file clock_ticks body
	[[ "$run_id" =~ $RUN_ID_PATTERN ]] || die "RUN_ID는 영문, 숫자, _, - 로 64자 이하여야 한다: $run_id"
	[[ "$seconds" =~ ^[1-9][0-9]*$ ]] || die "기록 초가 양의 정수가 아니다: $seconds"
	[ -f /sys/fs/cgroup/cgroup.controllers ] || die "cgroup v2가 아니다. 이 기록은 cgroup v2만 읽는다"
	unit="$UNIT_PREFIX-$run_id"
	file="$RESULT_DIR/$run_id-sut-cpu.tsv"
	! systemctl list-units --plain --no-legend --state=active "$UNIT_PREFIX-*" | grep -q . ||
		die "다른 CPU 기록이 돌고 있다. SUT에서 sudo systemctl stop $UNIT_PREFIX-<RUN_ID> 로 먼저 멈춘다"
	[ ! -e "$file" ] || die "$file 이 이미 있다. RUN_ID를 바꾼다"
	clock_ticks=$(getconf CLK_TCK)
	[ $((MICROSECONDS_PER_SECOND % clock_ticks)) -eq 0 ] || die "CLK_TCK $clock_ticks 로 마이크로초를 나눠떨어지게 바꿀 수 없다"

	mkdir -p "$RESULT_DIR"
	write_info "$unit" "$seconds" | tee "$RESULT_DIR/$run_id-sut-cpu-info.txt"
	printf 'epoch_ms\tkind\tname\tcontainer_id\tpid\tusage_usec\n' >"$file"
	body="$(declare -p COMPOSE_PROJECT SAMPLE_SECONDS SYSTEM_UNITS); $(declare -f cpu_usage record_round record_rounds); record_rounds \"\$@\""
	# systemd가 서비스 명령 줄의 ${...}와 $$를 먼저 풀어 버려, $를 모두 $$로 바꿔 넘긴다.
	# 설명을 주지 않으면 여러 줄 명령이 설명이 되어 daemon-reload 뒤 단위가 깨지고 RuntimeMaxSec도 사라진다.
	systemd-run --quiet --collect --unit="$unit" --description="컨테이너별 CPU 기록 $run_id" \
		--property=RuntimeMaxSec="$seconds" bash -c "${body//\$/\$\$}" \
		start-cpu-recording "$file" "$((MICROSECONDS_PER_SECOND / clock_ticks))"
	sleep "$FIRST_ROUND_WAIT_SECONDS"
	systemctl is-active --quiet "$unit" || { rm -f "$file"; die "기록이 바로 멈췄다. SUT에서 sudo journalctl -u $unit 로 본다"; }
	if ! grep -q "$(printf '\tcontainer\t')" "$file"; then
		systemctl stop "$unit"
		rm -f "$file"
		die "$COMPOSE_PROJECT 컨테이너를 하나도 읽지 못했다. 배포했는지 본다"
	fi
	log "기록 중: $file. ${seconds}초 뒤 스스로 멈춘다"
}

main "$@"
