#!/usr/bin/env bash
set -euo pipefail

log() { printf '[deploy-report] %s\n' "$*"; }
die() { printf '[deploy-report] 실패: %s\n' "$*" >&2; exit 1; }

summary_value() {
	awk -v key="$1" '$1 == key { print $2 }' "$2"
}

valid_backend() {
	[[ "$1" =~ ^legacy:[^,]+,blue:[^,]+,green:[^,]+$ ]]
}

backend_ids() {
	printf '%s\n' "$1" | tr ',' '\n' | sed 's/^[a-z]*://' | tr '+' '\n' | sed '/^없음$/d'
}

has_new_backend() {
	local before id
	before=" $(backend_ids "$1" | tr '\n' ' ') "
	for id in $(backend_ids "$2"); do
		case "$before" in
		*" $id "*) ;;
		*) return 0 ;;
		esac
	done
	return 1
}

invalid_reasons() {
	local before="$1" after="$2" dropped="$3" start="$4" end="$5" first="$6" last="$7" reasons=""
	[ "$dropped" = 0 ] || reasons="$reasons 못 보낸 요청이 ${dropped}건이다."
	case "$after" in
	*,blue:없음,green:없음) reasons="$reasons 배포 뒤 블루와 그린이 모두 돌지 않는다." ;;
	esac
	has_new_backend "$before" "$after" || reasons="$reasons 배포 뒤 백엔드 컨테이너가 모두 배포 전에도 돌던 것이라 배포가 새 백엔드를 띄우지 않았다."
	if [ -n "$first" ]; then
		[ "$first" -le "$start" ] || reasons="$reasons 첫 요청을 배포 시작 뒤에 보내 배포 구간 앞부분을 재지 못했다."
		[ "$last" -ge "$end" ] || reasons="$reasons 마지막 요청을 배포 끝 전에 보내 배포 구간 뒷부분을 재지 못했다."
	fi
	printf '%s' "$reasons"
}

# 요청을 보낸 시각 중 가장 이른 것과 늦은 것(ms)을 찍는다. 요청이 없으면 아무것도 찍지 않는다.
sent_range() {
	awk '{ sent = $3 + 0; if (NR == 1 || sent < first) first = sent; if (NR == 1 || sent > last) last = sent }
		END { if (NR > 0) printf "%.0f %.0f\n", first, last }'
}

# 배포 구간 안(inside)이나 밖(outside)에 보낸 요청의 걸린 ms에서 nearest-rank p99를 찍는다. 요청이 없으면 없음을 찍는다.
elapsed_p99() {
	awk -v start="$1" -v end="$2" -v want="$3" '
	{
		sent = $3 + 0
		inside = (sent >= start && sent <= end) ? "inside" : "outside"
		if (inside == want) print $4 + 0
	}' |
		sort -n |
		awk '{ v[NR] = $1 } END { if (NR == 0) print "없음"; else print v[int((99 * NR + 99) / 100)] }'
}

requests() {
	grep -o 'REQ [^"]*' "$1" || true
}

report() {
	awk -v start="$1" -v end="$2" -v rate="$3" -v inside_p99="$4" -v outside_p99="$5" -v unusable="$6" -v p99_margin=1000 '
	function ms(value) {
		return (value == "없음") ? value : value "ms"
	}
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
		printf "p99: 배포 구간 %s, 구간 밖 %s\n", ms(inside_p99), ms(outside_p99)
		if (unrecovered) print "주의: 마지막 요청까지 성공으로 돌아오지 않았다"
		fails = ""
		if (inside_failed > 0) fails = fails sprintf(" 배포 구간 요청 %d건이 실패했다.", inside_failed)
		if (deploy_gaps > 0) fails = fails sprintf(" 배포 구간에 걸친 끊김이 %d개다.", deploy_gaps)
		if (inside_p99 == "없음" || outside_p99 == "없음") {
			fails = fails " 배포 구간이나 구간 밖에 보낸 요청이 없어 p99를 견줄 수 없다."
		} else if (inside_p99 + 0 > outside_p99 + p99_margin) {
			fails = fails sprintf(" 배포 구간 p99 %dms가 구간 밖 p99 %dms + %dms를 넘는다.", inside_p99, outside_p99, p99_margin)
		}
		if (unusable != "") {
			print "판정: 쓰지 않는 측정이라 판정하지 않는다"
		} else if (fails == "") {
			printf "판정: 통과 (배포 구간 실패 0건, 배포 구간에 걸친 끊김 0개, 배포 구간 p99 %dms <= 구간 밖 p99 %dms + %dms)\n", inside_p99, outside_p99, p99_margin
		} else {
			print "판정: 실패." fails
		}
	}'
}

main() {
	[ $# -eq 5 ] || die "사용법: $0 <결과 폴더> <배포 시작 ms> <배포 끝 ms> <배포 전 backend> <배포 뒤 backend>"
	local dir="$1" start="$2" end="$3" before="$4" after="$5"
	local console="$dir/console.log" summary="$dir/summary.txt" output="$dir/report.txt"
	[ -f "$console" ] || die "$console 이 없다"
	[ -f "$summary" ] || die "$summary 가 없다. k6가 끝까지 돌았는지 본다"
	[ "$start" -lt "$end" ] || die "배포 시작 ms가 끝 ms보다 작아야 한다"
	valid_backend "$before" || die "배포 전 backend 값이 legacy:<ID>,blue:<ID>,green:<ID> 꼴이 아니다: $before"
	valid_backend "$after" || die "배포 뒤 backend 값이 legacy:<ID>,blue:<ID>,green:<ID> 꼴이 아니다: $after"

	local rate dropped first last reasons inside_p99 outside_p99
	rate=$(summary_value rate "$summary")
	dropped=$(summary_value dropped_iterations "$summary")
	read -r first last <<<"$(requests "$console" | sent_range)"
	reasons=$(invalid_reasons "$before" "$after" "$dropped" "$start" "$end" "$first" "$last")
	inside_p99=$(requests "$console" | elapsed_p99 "$start" "$end" inside)
	outside_p99=$(requests "$console" | elapsed_p99 "$start" "$end" outside)
	{
		[ -z "$reasons" ] || printf '이 측정은 쓰지 않는다.%s\n' "$reasons"
		printf '실행: %s\n' "$(basename "$dir")"
		printf '못 보낸 요청: %s건, VU %s, 요청 제한 %s초\n' "$dropped" "$(summary_value vus "$summary")" "$(summary_value timeout_seconds "$summary")"
		printf '백엔드 컨테이너: %s -> %s\n' "$before" "$after"
		requests "$console" | sort -n -k3 | report "$start" "$end" "$rate" "$inside_p99" "$outside_p99" "$reasons"
	} | tee "$output"
	log "저장: $output"
}

main "$@"
