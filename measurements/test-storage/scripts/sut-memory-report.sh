#!/usr/bin/env bash
set -euo pipefail

log() { printf '[sut-memory-report] %s\n' "$*"; }
die() { printf '[sut-memory-report] 실패: %s\n' "$*" >&2; exit 1; }

readonly STEP_SKIP_SECONDS=60
# Micrometer의 jvm_gc_pause_seconds_max는 최근 2분 안 최댓값이라 앞 단계 멈춤이 섞이지 않게 단계 시작 2분 뒤 표본만 쓴다.
readonly GC_LONGEST_SKIP_SECONDS=120
readonly SAMPLE_GAP_LIMIT_SECONDS=25
readonly BYTES_PER_MIB=1048576
# 쓸 수 없는 구간에서 없음으로 채울 고정 값 열 수다.
readonly MEMORY_METRIC_COLUMNS=19

# 단계마다 rate, 보낸 속도, 시작과 끝 epoch, 시작 시각(서울)을 찍는다. 앞 단계 끝부터 횟수를 세므로 시작 순으로 늘어놓는다.
steps() {
	local f
	for f in "$1/${1##*/}"-ingest-r*.json; do
		TZ=Asia/Seoul jq -r '
			def epoch: sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601;
			(.metadata.started_at | epoch) as $start
			| [.metadata.rate, (.k6.metrics.iterations.values.rate // 0 | floor), $start,
				(.metadata.ended_at | epoch), ($start | localtime | strftime("%Y-%m-%d %H:%M:%S"))]
			| @tsv' "$f"
	done | sort -t "$(printf '\t')" -k3,3n
}

kst() {
	TZ=Asia/Seoul jq -rn --argjson t "$1" '$t | localtime | strftime("%Y-%m-%d %H:%M:%S")'
}

# 백엔드 밖 컨테이너를 기록에 처음 나온 순서로 찍는다.
columns() {
	awk -F'\t' 'NR > 1 && NF == 7 && $2 == "container" && $6 == "memory.current" && $3 !~ /^backend(-blue|-green)?$/ && !($3 in seen) {
		seen[$3] = 1
		list = list $3 " "
	} END { print list }' "$1"
}

header() {
	local column
	printf 'rate\tsent_per_s\toom_kill\tbackend_restarts\tbackend_limit_hits\tbackend_peak_mib\tbackend_limit_mib\tbackend_peak_pct\tbackend_anon_max_mib'
	printf '\theap_used_max_mib\theap_max_mib\theap_used_max_pct\tgc_pause_ms_per_s\tgc_pause_max_ms\tgc_concurrent_ms_per_s\tfull_gc\tjvm_read_failed'
	printf '\thost_used_max_mib\thost_available_min_mib\thost_total_mib\tswap_used_max_mib'
	for column in $1; do printf '\t%s_peak_mib' "$column"; done
	printf '\tbackend_colors\twindow_start_kst\twindow_seconds\tnote\n'
}

step_row() {
	awk -F'\t' -v count_from_ms="$1" -v from_ms="$2" -v gc_from_ms="$3" -v longest_from_ms="$4" -v to_ms="$5" -v rate="$6" -v sent="$7" -v window_kst="$8" -v columns="$9" \
		-v gap_limit_ms="$((SAMPLE_GAP_LIMIT_SECONDS * 1000))" -v bytes_per_mib="$BYTES_PER_MIB" -v metric_columns="$MEMORY_METRIC_COLUMNS" '
	function flag(label, name) {
		if ((label SUBSEP name) in flagged) return
		flagged[label, name] = 1
		note = note ", " label (name == "" ? "" : ": " name)
	}
	function mib(b) { return sprintf("%.0f", b / bytes_per_mib) }
	function numeric(s) { return s ~ /^-?[0-9]+([.][0-9]+)?([eE][-+]?[0-9]+)?$/ }
	function is_backend(name) { return name ~ /^backend(-blue|-green)?$/ }
	# 구간 앞 값이 있으면 그 뒤부터 세고, 없으면 새로 뜬 것은 JVM 첫 읽기가 늦어도 0부터, 처음부터 있던 것은 첫 값부터 센다.
	# Micrometer는 GC 시계열을 그 일이 처음 생길 때 만들어, 앞 회차 응답에 없던 JVM 값은 0부터 센다.
	function event(group, key, v,    b) {
		if (!((group SUBSEP key) in ev_base)) {
			ev_key[group, ++ev_n[group]] = key
			ev_name[group, key] = $3
			if (key in before) b = before[key]
			else if ($2 == "jvm" && (run in ok_first) && ok_first[run] < t) b = 0
			else if (t == count_base && !counted_before) b = v
			else if ($2 == "jvm" && counted_new(run)) b = 0
			else if ($2 == "jvm") { b = v; flag("JVM 앞 값 없음", $3) }
			else b = 0
			ev_base[group, key] = b
		}
		ev_last[group, key] = v
	}
	# 앞 기록이 있는 단계에서는 첫 회차에 처음 보인 컨테이너도 새로 뜬 것이다.
	function counted_new(r) { return (r in count_first) && !(r in count_before_run) && (counted_before || count_first[r] > count_base) }
	# 횟수는 앞 단계 끝 바로 뒤부터 세어 단계 사이 몇 초 동안 생긴 일도 이 단계에 넣는다.
	function count_row() {
		if (count_base == "") count_base = t
		if ($2 == "container" && $6 == "memory.current" && !(run in count_first)) count_first[run] = t
		if ($2 == "host" && $6 == "vmstat_oom_kill") { has_oom = 1; event("oom", key, v) }
		else if ($2 == "docker" && $6 == "restart_count" && is_backend($3)) { has_backend_docker = 1; event("restart", key, v) }
		else if ($2 == "container" && $6 == "memory.events.max" && is_backend($3)) { has_limit_events = 1; event("limit", key, v) }
		else if ($2 == "container" && $6 == "memory.events.oom_kill") event("cg_oom", key, v)
		else if ($2 == "jvm" && index($6, "jvm_gc_pause_seconds_count{") == 1) {
			pause_count_seen = 1
			if (index($6, "gc=\"G1 Old Generation\"") > 0 || index($6, "action=\"end of major GC\"") > 0) event("full", key, v)
		}
	}
	function event_sum(group,    i, key, d, sum) {
		for (i = 1; i <= ev_n[group]; i++) {
			key = ev_key[group, i]
			d = ev_last[group, key] - ev_base[group, key]
			if (d < 0) { d = ev_last[group, key]; flag("카운터 줄어듦", ev_name[group, key]) }
			sum += d
		}
		return sum
	}
	# GC 초당 값은 CPU 표처럼 평균 구간 첫 회차를 기준으로 삼는다.
	function rate_counter(group, key, v,    b) {
		if (!((group SUBSEP key) in rc_base)) {
			rc_key[group, ++rc_n[group]] = key
			rc_name[group, key] = $3
			if (t == gc_base) b = v
			else if (key in gc_before) { b = gc_before[key]; flag("구간 앞 값을 썼다", $3) }
			else if ((run in ok_first) && ok_first[run] < t) b = 0
			else if ((run in run_first) && run_first[run] > gc_base && !(run in before_run)) b = 0
			else { b = v; flag("JVM 앞 값 없음", $3) }
			rc_base[group, key] = b
		}
		rc_last[group, key] = v
		gc_read_last = t
	}
	# JVM이 답하지 않은 끝 구간은 GC를 모르므로 마지막으로 읽은 때까지로 나눈다.
	function ms_per_s(group,    i, key, d, sum) {
		if (gc_base == "" || gc_last <= gc_base) return "없음"
		if (rc_n[group] == 0) return gc_jvm_seen ? "0.0" : "없음"
		if (gc_read_last <= gc_base) return "없음"
		for (i = 1; i <= rc_n[group]; i++) {
			key = rc_key[group, i]
			d = rc_last[group, key] - rc_base[group, key]
			if (d < 0) { d = rc_last[group, key]; flag("카운터 줄어듦", rc_name[group, key]) }
			sum += d
		}
		return sprintf("%.1f", 1000000 * sum / (gc_read_last - gc_base))
	}
	function count_or_none(group, seen_any) { return seen_any ? sprintf("%.0f", event_sum(group)) : "없음" }
	BEGIN { count_from = count_from_ms + 0; from = from_ms + 0; gc_from = gc_from_ms + 0; longest_from = longest_from_ms + 0; to = to_ms + 0; if (to <= from) exit }
	NR == 1 || NF != 7 { next }
	{
		t = $1 + 0
		if (t > to) exit
		if ($6 != "memory.max") {
			if (!numeric($7)) {
				if (t >= from) flag("숫자 아닌 값", $3 == "-" ? "" : $3)
				next
			}
			v = $7 + 0
		}
		key = $2 SUBSEP $3 SUBSEP $4 SUBSEP $5 SUBSEP $6
		run = $3 SUBSEP $4 SUBSEP $5
		if ($2 == "jvm" && $6 == "scrape_usec" && !(run in ok_first)) ok_first[run] = t
		gc_sum = ($2 == "jvm" && (index($6, "jvm_gc_pause_seconds_sum{") == 1 || index($6, "jvm_gc_concurrent_phase_time_seconds_sum{") == 1))
		if (t < gc_from && gc_sum) gc_before[key] = v
		if (t < count_from) {
			before[key] = v
			counted_before = 1
			if ($2 == "container" && $6 == "memory.current") count_before_run[run] = 1
		} else count_row()
		if (t < from) {
			if ($2 == "container" && $6 == "memory.current") before_run[run] = 1
			if ($6 == "memory.peak") before_peak[run] = v
			next
		}
		if (base == "") base = t
		if (t != last) {
			if (last != "" && t - last > max_gap) max_gap = t - last
			last = t
			rounds++
		}
		if (t >= gc_from) {
			if (gc_base == "") gc_base = t
			gc_last = t
		}
		if ($2 == "host") {
			has_host = 1
			if ($6 == "MemTotal") mem_total = v
			else if ($6 == "MemAvailable") {
				if (mem_total - v > used_max) used_max = mem_total - v
				if (avail_min == "" || v < avail_min) avail_min = v
			}
			else if ($6 == "SwapTotal") swap_total = v
			else if ($6 == "SwapFree") { if (swap_total - v > swap_used_max) swap_used_max = swap_total - v }
		} else if ($2 == "docker") {
			if ($6 == "oom_killed" && v == 1) flag("OOMKilled 표시", $3)
			else if ($6 == "restarting" && v == 1) flag("재시작 중 보임", $3)
		} else if ($2 == "container") {
			if ($6 == "memory.current") {
				if (!(run in seen_at)) {
					run_order[++runs] = run
					run_name[run] = $3
					run_first[run] = t
					if (t > base && !(run in before_run)) flag("새로 뜸", $3)
				}
				seen_at[run] = t
				if (!(run in peak) || v > peak[run]) peak[run] = v
				if (is_backend($3)) color[$3] = 1
			}
			else if ($6 == "memory.peak") {
				if (!(run in peak_first)) peak_first[run] = v
				peak_last[run] = v
			}
			else if ($6 == "memory.max") limit[run] = $7
			else if ($6 == "memory.stat.anon" && is_backend($3)) { if (anon_max == "" || v > anon_max) anon_max = v }
		} else if ($2 == "jvm") {
			jvm_seen = 1
			if ($6 == "scrape_error") { read_failed++; next }
			if ($6 == "scrape_usec") next
			if (t >= gc_from) gc_jvm_seen = 1
			# mawk 1.3.4 20200120은 13자리 숫자 첨자를 %.6g로 바꿔 여러 회차가 한 칸에 섞이므로 원문 문자열을 첨자로 쓴다.
			if (index($6, "jvm_memory_used_bytes{area=\"heap\"") == 1) heap_used[run, $1] += v
			else if (index($6, "jvm_memory_max_bytes{area=\"heap\"") == 1) { if (v > 0) heap_max[run, $1] += v }
			else if (index($6, "jvm_gc_pause_seconds_max{") == 1) {
				if (t >= longest_from && (pause_max == "" || v > pause_max)) pause_max = v
			}
			else if (t >= gc_from && index($6, "jvm_gc_pause_seconds_sum{") == 1) rate_counter("pause", key, v)
			else if (t >= gc_from && index($6, "jvm_gc_concurrent_phase_time_seconds_sum{") == 1) rate_counter("concurrent", key, v)
		}
	}
	END {
		count = split(columns, column, " ")
		if (to <= from) unusable = "구간 짧음"
		else if (rounds == 0) unusable = "SUT 기록 범위 밖"
		else if (rounds == 1) unusable = "표본 하나뿐"
		row = rate "\t" sent
		if (unusable != "") {
			for (i = 1; i <= metric_columns + count + 1; i++) row = row "\t없음"
			print row "\t" window_kst "\t0\t" unusable
			exit
		}
		bp = ""
		for (i = 1; i <= runs; i++) {
			r = run_order[i]
			name = run_name[r]
			if (seen_at[r] < last) flag("멈춤", name)
			p = peak[r]
			# 평생 최고값(memory.peak)이 구간 안에서 올랐거나 구간 안에서 뜬 것이면 그 값이 구간 최고값이다.
			if (r in before_peak) exact = peak_last[r] > before_peak[r]
			else exact = (r in peak_last) && ((run_first[r] > base && !(r in before_run)) || peak_last[r] > peak_first[r])
			if (exact) { if (peak_last[r] > p) p = peak_last[r] }
			else if (is_backend(name)) flag("최고값 하한", name)
			if (is_backend(name)) {
				if (bp == "" || p > bp) { bp = p; bp_limit = (r in limit) ? limit[r] : "" }
			} else if (!(name in svc_peak) || p > svc_peak[name]) svc_peak[name] = p
		}
		row = row "\t" count_or_none("oom", has_oom) "\t" count_or_none("restart", has_backend_docker) "\t" count_or_none("limit", has_limit_events)
		if (bp == "") row = row "\t없음\t없음\t없음"
		else if (bp_limit ~ /^[0-9]+$/) row = row "\t" mib(bp) "\t" mib(bp_limit) "\t" sprintf("%.1f", 100 * bp / bp_limit)
		else row = row "\t" mib(bp) "\t없음\t없음"
		row = row "\t" (anon_max == "" ? "없음" : mib(anon_max))
		heap_used_max = ""; heap_max_max = ""; heap_pct_max = ""
		for (k in heap_used) {
			if (heap_used_max == "" || heap_used[k] > heap_used_max) heap_used_max = heap_used[k]
			if ((k in heap_max) && heap_max[k] > 0 && (heap_pct_max == "" || 100 * heap_used[k] / heap_max[k] > heap_pct_max)) heap_pct_max = 100 * heap_used[k] / heap_max[k]
		}
		for (k in heap_max) if (heap_max_max == "" || heap_max[k] > heap_max_max) heap_max_max = heap_max[k]
		row = row "\t" (heap_used_max == "" ? "없음" : mib(heap_used_max)) "\t" (heap_max_max == "" ? "없음" : mib(heap_max_max)) "\t" (heap_pct_max == "" ? "없음" : sprintf("%.1f", heap_pct_max))
		row = row "\t" ms_per_s("pause") "\t" (pause_max == "" ? "없음" : sprintf("%.1f", 1000 * pause_max)) "\t" ms_per_s("concurrent")
		row = row "\t" count_or_none("full", pause_count_seen) "\t" (jvm_seen ? sprintf("%.0f", read_failed + 0) : "없음")
		if (has_host) row = row "\t" mib(used_max) "\t" (avail_min == "" ? "없음" : mib(avail_min)) "\t" mib(mem_total) "\t" mib(swap_used_max)
		else row = row "\t없음\t없음\t없음\t없음"
		for (i = 1; i <= count; i++) row = row "\t" ((column[i] in svc_peak) ? mib(svc_peak[column[i]]) : "없음")
		for (i = 1; i <= ev_n["cg_oom"]; i++) {
			k = ev_key["cg_oom", i]
			if (ev_last["cg_oom", k] > ev_base["cg_oom", k]) flag("컨테이너 OOM", ev_name["cg_oom", k])
		}
		# 다시 뜨면 cgroup이 새로 생겨 죽기 직전의 최고값과 한도 닿음이 기록에 남지 않는다.
		for (i = 1; i <= ev_n["restart"]; i++) {
			k = ev_key["restart", i]
			if (ev_last["restart", k] > ev_base["restart", k]) flag("재시작 전 최고값 못 잼", ev_name["restart", k])
		}
		split("backend backend-blue backend-green", backend_name, " ")
		for (i = 1; i <= 3; i++) if (backend_name[i] in color) { colors = colors "+" (i == 1 ? "legacy" : substr(backend_name[i], 9)); backends++ }
		if (backends > 1) flag("백엔드 둘 이상이 함께 돌았다", "")
		if (last - base < to - from - 2 * gap_limit_ms) flag("기록이 구간 일부만 덮음", "")
		if (max_gap > gap_limit_ms) flag(sprintf("표본 간격 %d초", max_gap / 1000), "")
		if (gc_jvm_seen && gc_read_last < gc_last - gap_limit_ms) flag("JVM 값 구간 끝 전에 끊김", "")
		print row "\t" (colors == "" ? "없음" : substr(colors, 2)) "\t" window_kst "\t" sprintf("%d", (last - base) / 1000) "\t" (note == "" ? "없음" : substr(note, 3))
	}' "${10}"
}

main() {
	[ $# -eq 2 ] && [ -d "$1" ] && [ -f "$2" ] || die "사용법: $0 <k6 결과 폴더> <SUT 메모리 기록 TSV>"
	command -v jq >/dev/null 2>&1 || die "jq 명령이 없다. 설치한 뒤 다시 실행한다"
	local dir="${1%/}" record="$2" output="${2%.tsv}-report.tsv" columns rate sent start end start_kst first_ms last_ms count_from previous_end=""
	case "$(basename "$record")" in
	"$(basename "$dir")"-*) ;;
	*) die "$record 의 RUN_ID가 $dir 와 다르다" ;;
	esac
	[ "$(head -n 1 "$record")" = "$(printf 'epoch_ms\tkind\tname\tcontainer_id\tpid\tmetric\tvalue')" ] || die "$record 는 SUT 메모리 기록 형식이 아니다"
	ls "$dir/${dir##*/}"-ingest-r*.json >/dev/null 2>&1 || die "$dir 에 단계 결과(<RUN_ID>-ingest-r*.json)가 없다"
	columns=$(columns "$record")
	first_ms=$(awk -F'\t' 'NR == 2 { print $1; exit }' "$record")
	last_ms=$(tail -n 1 "$record" | cut -f 1)
	{
		header "$columns"
		steps "$dir" | while IFS=$'\t' read -r rate sent start end start_kst; do
			count_from=$((start * 1000))
			[ -z "$previous_end" ] || [ "$previous_end" -ge "$start" ] || count_from=$((previous_end * 1000 + 1))
			step_row "$count_from" "$((start * 1000))" "$(((start + STEP_SKIP_SECONDS) * 1000))" "$(((start + GC_LONGEST_SKIP_SECONDS) * 1000))" "$((end * 1000))" \
				"$rate" "$sent" "$start_kst" "$columns" "$record"
			previous_end=$end
		done
		[ -z "$first_ms" ] || step_row "$first_ms" "$first_ms" "$first_ms" "$first_ms" "$last_ms" 전체 없음 "$(kst "$((first_ms / 1000))")" "$columns" "$record"
	} >"$output"
	column -t -s "$(printf '\t')" "$output" 2>/dev/null || cat "$output"
	log "저장: $output"
}

main "$@"
