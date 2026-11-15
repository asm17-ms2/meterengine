#!/usr/bin/env bash
# 버전 파라미터에 커밋 SHA를 쓰고 오토스케일링 그룹의 서버를 새 서버로 바꾼다. CD와 사람이 서버 밖에서 부른다.
# 사용법: rollout.sh <오토스케일링 그룹 이름> <버전 파라미터 이름> <커밋 SHA 40자>
# 결과는 표준 출력에 recorded_only=<true|false>, alb_dns_name=<값> 두 줄로 남기고 진행 기록은 표준 오류로 찍는다.

set -euo pipefail

SHA_PATTERN='^[0-9a-f]{40}$'
POLL_SECONDS=15
# 측정 환경에서 서버 한 대를 바꾸는 데 4분 29초 걸렸고, 기본 이미지에 설치하는 시간을 더해 15분을 본다.
REFRESH_WAIT_POLLS=60
# 유예 시간 안에는 ASG가 새 서버를 건강하다고 봐 새로 고침이 대상 그룹 healthy보다 먼저 끝날 수 있다.
TARGET_HEALTH_WAIT_POLLS=20
RECOVERY_WAIT_POLLS=80
ACTIVE_STATUSES="Pending InProgress Cancelling RollbackInProgress Baking"
# 요청을 받는 그룹은 새 서버가 healthy가 된 뒤 옛 서버를 뺀다.
APP_REFRESH_PREFERENCES='{"MinHealthyPercentage":100,"MaxHealthyPercentage":200,"SkipMatching":false}'
# 컨슈머 그룹은 먼저 내리고 띄운다. 새 서버를 먼저 띄우면 파티션을 두 번 다시 나눈다.
CONSUMER_REFRESH_PREFERENCES='{"MinHealthyPercentage":50,"SkipMatching":false}'

group=
parameter=
sha=
previous=
target_groups=
alb_dns_name=
refresh_preferences=$APP_REFRESH_PREFERENCES

log() { printf '[rollout] %s\n' "$*" >&2; }
die() {
	printf '[rollout] 실패: %s\n' "$*" >&2
	exit 1
}

describe_group() {
	aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$group" --query "AutoScalingGroups[0].$1" --output text
}

active_refreshes() {
	aws autoscaling describe-instance-refreshes --auto-scaling-group-name "$group" \
		--query "InstanceRefreshes[?contains(['Pending','InProgress','Cancelling','RollbackInProgress','Baking'], Status)].InstanceRefreshId" \
		--output text
}

refresh_field() {
	aws autoscaling describe-instance-refreshes --auto-scaling-group-name "$group" --instance-refresh-ids "$1" \
		--query "InstanceRefreshes[0].$2" --output text
}

find_alb_dns_name() {
	local load_balancer
	load_balancer=$(aws elbv2 describe-target-groups --target-group-arns "${target_groups%%[[:space:]]*}" \
		--query 'TargetGroups[0].LoadBalancerArns[0]' --output text) || return 1
	aws elbv2 describe-load-balancers --load-balancer-arns "$load_balancer" --query 'LoadBalancers[0].DNSName' --output text
}

write_version() {
	aws ssm put-parameter --name "$parameter" --type String --overwrite --value "$1" >/dev/null
}

# 성공하면 새로 고침 ID를 찍는다. 다른 새로 고침이 먼저 시작돼 있으면 2로 끝난다.
start_refresh() {
	local errors id
	errors=$(mktemp)
	# Terraform의 instance_refresh 블록과 같은 값이다. 바꾸면 둘을 같이 바꾼다.
	if id=$(aws autoscaling start-instance-refresh --auto-scaling-group-name "$group" --strategy Rolling \
		--preferences "$refresh_preferences" \
		--query InstanceRefreshId --output text 2>"$errors"); then
		rm -f "$errors"
		printf '%s' "$id"
		return 0
	fi
	if grep -q InstanceRefreshInProgress "$errors"; then
		rm -f "$errors"
		return 2
	fi
	cat "$errors" >&2
	rm -f "$errors"
	return 1
}

# 새로 고침이 끝난 상태를 찍는다. 정한 횟수 안에 끝나지 않으면 TimedOut을 찍는다.
wait_refresh() {
	local id="$1" polls="$2" i status
	for ((i = 0; i < polls; i++)); do
		status=$(refresh_field "$id" Status) || status=
		case " $ACTIVE_STATUSES " in
		*" $status "*) ;;
		*) if [ -n "$status" ] && [ "$status" != None ]; then
			printf '%s' "$status"
			return 0
		fi ;;
		esac
		sleep "$POLL_SECONDS"
	done
	printf 'TimedOut'
}

# 일하는 서버마다 모든 대상 그룹에서 healthy인지 본다.
targets_healthy() {
	local instances target_group healthy instance
	instances=$(describe_group "Instances[?LifecycleState=='InService'].InstanceId") || return 1
	[ -n "$instances" ] || return 1
	for target_group in $target_groups; do
		healthy=$(aws elbv2 describe-target-health --target-group-arn "$target_group" \
			--query "TargetHealthDescriptions[?TargetHealth.State=='healthy'].Target.Id" --output text) || return 1
		for instance in $instances; do
			tr -s '[:space:]' '\n' <<<"$healthy" | grep -Fxq "$instance" || return 1
		done
	done
}

wait_targets_healthy() {
	local i
	for ((i = 0; i < TARGET_HEALTH_WAIT_POLLS; i++)); do
		if targets_healthy; then
			return 0
		fi
		sleep "$POLL_SECONDS"
	done
	return 1
}

print_recent_activities() {
	log "최근 활동:"
	aws autoscaling describe-scaling-activities --auto-scaling-group-name "$group" --max-items 5 \
		--query 'Activities[].[StartTime,StatusCode,Description,StatusMessage]' --output text >&2 || true
}

print_manual_steps() {
	log "사람이 할 일: 오토스케일링 그룹 $group 의 서버와 대상 그룹 상태를 본 뒤, 고친 커밋이나 되돌릴 커밋으로 다시 실행한다"
	log "  deploy/rollout.sh $group $parameter <SHA>"
}

# 이 배포가 실패했을 때만 부른다. 이전 값이 커밋 SHA면 되돌리고 그 값으로 서버를 다시 바꾼다.
fail_rollout() {
	local reason="$1" refresh_id="$2" recovery_id status
	log "실패: $reason"
	if [ -n "$refresh_id" ]; then
		log "사유: $(refresh_field "$refresh_id" StatusReason || true)"
		print_recent_activities
	fi
	if ! [[ "$previous" =~ $SHA_PATTERN ]]; then
		log "이전 값($previous)이 커밋 SHA가 아니라 버전 파라미터를 되돌리지 않는다"
		print_manual_steps
		exit 1
	fi
	write_version "$previous" || {
		log "버전 파라미터를 이전 값으로 되돌리지 못했다"
		print_manual_steps
		exit 1
	}
	log "버전 파라미터를 이전 값 $previous 로 되돌렸다"
	if [ -z "$refresh_id" ]; then
		exit 1
	fi
	if ! recovery_id=$(start_refresh); then
		log "복구 새로 고침을 시작하지 못했다"
		print_manual_steps
		exit 1
	fi
	log "복구 새로 고침 $recovery_id 을 시작했다"
	status=$(wait_refresh "$recovery_id" "$RECOVERY_WAIT_POLLS")
	if [ "$status" = Successful ]; then
		log "서버를 이전 버전으로 되돌렸다"
	else
		log "복구 새로 고침이 $status 로 끝났다"
		print_manual_steps
	fi
	exit 1
}

interfered() {
	log "실패: 다른 새로 고침이 끼어들었다. 그 새로 고침도 버전 파라미터의 $sha 를 읽으므로 파라미터는 그대로 둔다"
	log "그 새로 고침이 끝나면 결과를 보고, 필요하면 같은 SHA로 다시 배포한다"
	exit 1
}

main() {
	if [ $# -ne 3 ] || ! [[ "$3" =~ $SHA_PATTERN ]]; then
		die "사용법: $0 <오토스케일링 그룹 이름> <버전 파라미터 이름> <커밋 SHA 40자>"
	fi
	group="$1" parameter="$2" sha="$3"

	local found min desired active
	read -r found min desired <<<"$(describe_group '[AutoScalingGroupName,MinSize,DesiredCapacity]')" ||
		die "오토스케일링 그룹 $group 을 읽지 못했다"
	[ "$found" = "$group" ] || die "오토스케일링 그룹 $group 이 없다"
	target_groups=$(describe_group 'TargetGroupARNs') || die "오토스케일링 그룹 $group 의 대상 그룹을 읽지 못했다"
	# 컨슈머 서버 그룹처럼 요청을 받지 않는 그룹은 대상 그룹과 ALB가 없다. 서버가 InService인지만 본다.
	if [ -z "$target_groups" ] || [ "$target_groups" = None ]; then
		target_groups=
		refresh_preferences=$CONSUMER_REFRESH_PREFERENCES
	else
		alb_dns_name=$(find_alb_dns_name) || die "오토스케일링 그룹 $group 앞의 ALB를 찾지 못했다"
	fi

	active=$(active_refreshes) || die "새로 고침 상태를 읽지 못했다"
	[ -z "$active" ] || die "진행 중인 새로 고침($active)이 있어 버전을 쓰지 않았다. 끝난 뒤 다시 배포한다"

	# 서버 그룹 여러 개를 차례로 바꿀 때는 처음에 읽은 값을 넘겨받는다. 앞 그룹이 이미 새 SHA를 써서 다시 읽으면 되돌릴 값을 잃는다.
	previous=${ROLLOUT_PREVIOUS:-}
	if [ -z "$previous" ]; then
		previous=$(aws ssm get-parameter --name "$parameter" --query Parameter.Value --output text) ||
			die "버전 파라미터 $parameter 를 읽지 못했다"
	fi
	write_version "$sha" || die "버전 파라미터 $parameter 에 쓰지 못했다"
	log "버전 파라미터 $parameter: $previous -> $sha"

	if [ "$min" = 0 ]; then
		if [ "$desired" != 0 ]; then
			log "경고: 최소 대수가 0인데 서버가 ${desired}대 있다. 새로 고침하지 않아 이 서버들은 새 버전으로 바뀌지 않는다"
		fi
		log "최소 대수가 0이라 버전만 기록했다"
		printf 'recorded_only=true\nalb_dns_name=%s\n' "$alb_dns_name"
		return 0
	fi

	local refresh_id code=0 status
	refresh_id=$(start_refresh) || code=$?
	case "$code" in
	0) ;;
	2) interfered ;;
	*) fail_rollout "새로 고침을 시작하지 못했다" "" ;;
	esac
	log "새로 고침 $refresh_id 을 시작했다"

	status=$(wait_refresh "$refresh_id" "$REFRESH_WAIT_POLLS")
	case "$status" in
	Successful) ;;
	Cancelled) interfered ;;
	TimedOut)
		aws autoscaling cancel-instance-refresh --auto-scaling-group-name "$group" >/dev/null || true
		status=$(wait_refresh "$refresh_id" "$REFRESH_WAIT_POLLS")
		fail_rollout "새로 고침이 시간 안에 끝나지 않아 취소했다(취소 뒤 상태 $status)" "$refresh_id"
		;;
	*) fail_rollout "새로 고침이 $status 로 끝났다" "$refresh_id" ;;
	esac

	wait_targets_healthy || fail_rollout "새로 고침은 끝났지만 일하는 서버가 대상 그룹에서 healthy가 되지 않았다" "$refresh_id"
	log "모든 서버가 $sha 로 바뀌었고 대상 그룹에서 healthy다"
	printf 'recorded_only=false\nalb_dns_name=%s\n' "$alb_dns_name"
}

main "$@"
