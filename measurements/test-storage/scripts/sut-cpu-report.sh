#!/usr/bin/env bash
set -euo pipefail

log() { printf '[sut-cpu-report] %s\n' "$*"; }
die() { printf '[sut-cpu-report] 실패: %s\n' "$*" >&2; exit 1; }

readonly STEP_SKIP_SECONDS=60
readonly SAMPLE_GAP_LIMIT_SECONDS=25
readonly OUTSIDE_NEGATIVE_LIMIT_PERCENT=1

# 단계마다 rate, 보낸 속도, 시작과 끝 epoch, 평균을 내기 시작하는 시각(서울)을 찍는다.
steps() {
	local f
	for f in "$1/${1##*/}"-ingest-r*.json; do
		TZ=Asia/Seoul jq -r --argjson skip "$STEP_SKIP_SECONDS" '
			def epoch: sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601;
			(.metadata.started_at | epoch) as $start
			| [.metadata.rate, (.k6.metrics.iterations.values.rate // 0 | floor), $start,
				(.metadata.ended_at | epoch), ($start + $skip | localtime | strftime("%Y-%m-%d %H:%M:%S"))]
			| @tsv' "$f"
	done | sort -n
}

# 열 순서: backend, 그 밖의 컨테이너, 시스템 단위. 뒤 둘은 기록에 처음 나온 순서다.
columns() {
	awk -F'\t' 'NR > 1 && NF == 6 && $2 != "host" {
		name = ($2 == "container" && $3 ~ /^backend(-blue|-green)?$/) ? "backend" : $3
		key = $2 ":" name
		if (key in seen) next
		seen[key] = 1
		if (key == "container:backend") backend = key " "
		else if ($2 == "container") containers = containers key " "
		else units = units key " "
	} END { print backend containers units }' "$1"
}

header() {
	local column
	printf 'rate\tsent_per_s\tec2_cpu'
	for column in $1; do
		case "$column" in container:*) printf '\t%s' "${column#*:}" ;; esac
	done
	printf '\tcontainers\toutside\tsoftirq\tirq\tiowait\tsteal'
	for column in $1; do
		case "$column" in unit:*) printf '\t%s' "${column#*:}" ;; esac
	done
	printf '\tbackend_colors\twindow_start_kst\twindow_seconds\tnote\n'
}

step_row() {
	awk -F'\t' -v from_ms="$1" -v to_ms="$2" -v rate="$3" -v sent="$4" -v window_kst="$5" -v columns="$6" \
		-v gap_limit_ms="$((SAMPLE_GAP_LIMIT_SECONDS * 1000))" -v negative_limit="$OUTSIDE_NEGATIVE_LIMIT_PERCENT" '
	function flag(label, name) {
		if ((label SUBSEP name) in flagged) return
		flagged[label, name] = 1
		note = note ", " label (name == "" ? "" : ": " name)
	}
	function percent(value) { return sprintf("%.1f", 100 * value / total) }
	BEGIN { from = from_ms + 0; to = to_ms + 0; if (to <= from) exit }
	NR == 1 || NF != 6 { next }
	{
		t = $1 + 0
		if (t > to) exit
		key = $2 SUBSEP $3 SUBSEP $4 SUBSEP $5
		v = $6 + 0
		if (t < from) { before[key] = v; next }
		if (base == "") base = t
		if (t != last) {
			if (last != "" && t - last > max_gap) max_gap = t - last
			last = t
			rounds++
		}
		group = $2 ":" (($2 == "container" && $3 ~ /^backend(-blue|-green)?$/) ? "backend" : $3)
		present[group] = 1
		if ($2 == "container" && $3 ~ /^backend/) color[$3] = 1
		if (!(key in seen_at)) { order[++keys] = key; service[key] = $3 }
		seen_at[key] = t
		if (t == base) { previous[key] = v; next }
		if (key in previous) baseline = previous[key]
		else if (key in before) { baseline = before[key]; flag("구간 앞 값을 썼다", $3) }
		else { baseline = 0; if ($2 == "container") flag("새로 뜸", $3) }
		delta = v - baseline
		# 줄어든 값은 unit만 재시작이고, host와 container는 커널 보정이라 그 회차를 0으로 본다.
		if (delta < 0) { delta = ($2 == "unit") ? v : 0; flag("카운터 줄어듦", $3) }
		previous[key] = v
		used[group] += delta
	}
	END {
		count = split(columns, column, " ")
		if (to <= from) unusable = "구간 짧음"
		else if (rounds == 0) unusable = "SUT 기록 범위 밖"
		else if (rounds == 1) unusable = "표본 하나뿐"
		row = rate "\t" sent
		if (unusable != "") {
			for (i = 1; i <= count + 8; i++) row = row "\t없음"
			print row "\t" window_kst "\t0\t" unusable
			exit
		}
		for (i = 1; i <= keys; i++) if (seen_at[order[i]] < last) flag("멈춤", service[order[i]])
		total = used["host:user"] + used["host:nice"] + used["host:system"] + used["host:idle"] + used["host:iowait"] + used["host:irq"] + used["host:softirq"] + used["host:steal"]
		busy = total - used["host:idle"] - used["host:iowait"] - used["host:steal"]
		row = row "\t" percent(total - used["host:idle"])
		for (i = 1; i <= count; i++) {
			if (column[i] !~ /^container:/) continue
			row = row "\t" ((column[i] in present) ? percent(used[column[i]]) : "없음")
			containers += used[column[i]]
		}
		if (100 * (busy - containers) / total < -negative_limit) flag("컨테이너 밖 음수", "")
		row = row "\t" percent(containers) "\t" percent(busy - containers)
		row = row "\t" percent(used["host:softirq"]) "\t" percent(used["host:irq"]) "\t" percent(used["host:iowait"]) "\t" percent(used["host:steal"])
		for (i = 1; i <= count; i++)
			if (column[i] ~ /^unit:/) row = row "\t" ((column[i] in present) ? percent(used[column[i]]) : "없음")
		split("backend backend-blue backend-green", backend_name, " ")
		for (i = 1; i <= 3; i++) if (backend_name[i] in color) { colors = colors "+" (i == 1 ? "legacy" : substr(backend_name[i], 9)); backends++ }
		if (backends > 1) flag("백엔드 둘 이상이 함께 돌았다", "")
		if (last - base < to - from - 2 * gap_limit_ms) flag("기록이 구간 일부만 덮음", "")
		if (max_gap > gap_limit_ms) flag(sprintf("표본 간격 %d초", max_gap / 1000), "")
		print row "\t" (colors == "" ? "없음" : substr(colors, 2)) "\t" window_kst "\t" sprintf("%d", (last - base) / 1000) "\t" (note == "" ? "없음" : substr(note, 3))
	}' "$7"
}

main() {
	[ $# -eq 2 ] && [ -d "$1" ] && [ -f "$2" ] || die "사용법: $0 <k6 결과 폴더> <CPU 기록 TSV>"
	command -v jq >/dev/null 2>&1 || die "jq 명령이 없다. 설치한 뒤 다시 실행한다"
	local dir="${1%/}" record="$2" output="${2%.tsv}-report.tsv" columns rate sent start end window_kst
	case "$(basename "$record")" in
	"$(basename "$dir")"-*) ;;
	*) die "$record 의 RUN_ID가 $dir 와 다르다" ;;
	esac
	[ "$(head -n 1 "$record")" = "$(printf 'epoch_ms\tkind\tname\tcontainer_id\tpid\tusage_usec')" ] || die "$record 는 CPU 기록 형식이 아니다"
	ls "$dir/${dir##*/}"-ingest-r*.json >/dev/null 2>&1 || die "$dir 에 단계 결과(<RUN_ID>-ingest-r*.json)가 없다"
	columns=$(columns "$record")
	{
		header "$columns"
		steps "$dir" | while IFS=$'\t' read -r rate sent start end window_kst; do
			step_row "$(((start + STEP_SKIP_SECONDS) * 1000))" "$((end * 1000))" "$rate" "$sent" "$window_kst" "$columns" "$record"
		done
	} >"$output"
	column -t -s "$(printf '\t')" "$output" 2>/dev/null || cat "$output"
	log "저장: $output"
}

main "$@"
