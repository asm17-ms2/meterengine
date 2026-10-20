#!/usr/bin/env bash
set -euo pipefail

log() { printf '[deploy-report] %s\n' "$*"; }
die() { printf '[deploy-report] 실패: %s\n' "$*" >&2; exit 1; }

summary_value() {
	awk -v key="$1" '$1 == key { print $2 }' "$2"
}

invalid_reasons() {
	local before="$1" after="$2" dropped="$3" reasons=""
	[ "$dropped" = 0 ] || reasons="$reasons 못 보낸 요청이 ${dropped}건이다."
	[ "$after" != 없음 ] || reasons="$reasons 배포 뒤 백엔드 컨테이너가 없다."
	[ "$before" != "$after" ] || reasons="$reasons 배포 전후 백엔드 컨테이너가 같아 배포가 백엔드를 바꾸지 않았다."
	printf '%s' "$reasons"
}

report() {
	awk -v start="$1" -v end="$2" -v rate="$3" '
	function close_gap(until_ms,    from, length_ms) {
		from = (last_ok == "") ? gap_first_fail : last_ok
		length_ms = until_ms - from
		if (from < end && until_ms > start) {
			deploy_gaps++
			if (length_ms > longest) { longest = length_ms; longest_from = from }
		} else {
			outside_gaps++
			outside_gap_ms += length_ms
		}
		in_gap = 0
	}
	{
		sent = $3 + 0
		ok = ($5 == "200")
		if (first == "") first = sent
		last = sent
		if (sent >= start && sent <= end) {
			inside_sent++
			if (!ok) inside_failed++
		} else {
			outside_sent++
			if (ok) outside_ok++
		}
		if (ok) {
			if (in_gap) close_gap(sent)
			last_ok = sent
		} else if (!in_gap) {
			in_gap = 1
			gap_first_fail = sent
		}
	}
	END {
		if (NR == 0) { print "REQ 줄이 없다"; exit 1 }
		if (in_gap) { unrecovered = 1; close_gap(last) }
		overlap_from = (start > first) ? start : first
		overlap_to = (end < last) ? end : last
		overlap = (overlap_to > overlap_from) ? overlap_to - overlap_from : 0
		outside_ms = (last - first) - overlap
		printf "보낸 속도: 초당 %d건, 끊김은 실제보다 최대 %dms 길게 잡힐 수 있다\n", rate, 2000 / rate
		printf "배포 구간: %dms (실행 시작 %.1f초 뒤부터)\n", end - start, (start - first) / 1000
		printf "배포 구간 요청: %d건 보냄, %d건 실패\n", inside_sent, inside_failed
		if (deploy_gaps > 0) {
			printf "가장 긴 끊김: %dms (실행 시작 %.1f초 뒤부터), 배포 구간에 걸친 끊김 %d개\n", longest, (longest_from - first) / 1000, deploy_gaps
		} else {
			print "가장 긴 끊김: 없음"
		}
		if (outside_ms > 0 && outside_sent > 0) {
			printf "배포 구간 밖 가용성: 시간 기준 %.3f%%, 요청 기준 %.3f%% (끊김 %d개, %d건 중 %d건 성공)\n", \
				100 * (outside_ms - outside_gap_ms) / outside_ms, 100 * outside_ok / outside_sent, outside_gaps, outside_sent, outside_ok
		} else {
			print "배포 구간 밖 가용성: 구간 밖에 보낸 요청이 없다"
		}
		if (unrecovered) print "주의: 마지막 요청까지 성공으로 돌아오지 않았다"
	}'
}

main() {
	[ $# -eq 5 ] || die "사용법: $0 <결과 폴더> <배포 시작 ms> <배포 끝 ms> <배포 전 backend> <배포 뒤 backend>"
	local dir="$1" start="$2" end="$3" before="$4" after="$5"
	local console="$dir/console.log" summary="$dir/summary.txt" output="$dir/report.txt"
	[ -f "$console" ] || die "$console 이 없다"
	[ -f "$summary" ] || die "$summary 가 없다. k6가 끝까지 돌았는지 본다"
	[ "$start" -lt "$end" ] || die "배포 시작 ms가 끝 ms보다 작아야 한다"

	local rate dropped reasons
	rate=$(summary_value rate "$summary")
	dropped=$(summary_value dropped_iterations "$summary")
	reasons=$(invalid_reasons "$before" "$after" "$dropped")
	{
		[ -z "$reasons" ] || printf '이 측정은 쓰지 않는다.%s\n' "$reasons"
		printf '실행: %s\n' "$(basename "$dir")"
		printf '못 보낸 요청: %s건, VU %s, 요청 제한 %s초\n' "$dropped" "$(summary_value vus "$summary")" "$(summary_value timeout_seconds "$summary")"
		printf '백엔드 컨테이너: %s -> %s\n' "$before" "$after"
		grep -o 'REQ [^"]*' "$console" | sort -n -k3 | report "$start" "$end" "$rate"
	} | tee "$output"
	log "저장: $output"
}

main "$@"
