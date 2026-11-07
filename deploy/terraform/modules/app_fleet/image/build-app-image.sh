#!/usr/bin/env bash
# develop에 머지된 provision.sh로 앱 서버 이미지를 굽는다. 사람이 관리자 자격으로 서버 밖에서 부른다.
# 사용법: build-app-image.sh <기본 AMI ID>
# 결과는 표준 출력에 image_id=<값> 한 줄로 남기고 진행 기록은 표준 오류로 찍는다.

set -euo pipefail

ACCOUNT_ID=928618307647
REGION=ap-northeast-2
VPC_ID=vpc-07498f0672b7f3c9e
# staging 공용 서브넷이다. 공인 IP로 dnf 저장소와 GitHub에 닿는다.
SUBNET_ID=subnet-0348a18c1d399c193
INSTANCE_TYPE=t4g.medium
BUILDER_NAME=meterengine-app-image-builder
# 시작 템플릿의 루트 볼륨 크기다. 이미지 스냅숏이 이보다 크면 서버가 뜨지 못한다.
LAUNCH_TEMPLATE_ROOT_GIB=30
POLL_SECONDS=15
STOP_WAIT_POLLS=80
IMAGE_WAIT_POLLS=80
SECURITY_GROUP_DELETE_ATTEMPTS=6
AMI_PATTERN='^ami-[0-9a-f]{8,17}$'

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
provision="$here/provision.sh"
base_image=
builder=
security_group=
image=

log() { printf '[app-image] %s\n' "$*" >&2; }
die() {
	printf '[app-image] 실패: %s\n' "$*" >&2
	exit 1
}

cleanup() {
	# 진행 기록을 받을 곳이 사라지거나 Ctrl-C를 또 눌러도 빌더와 보안 그룹은 끝까지 지운다.
	trap '' INT TERM HUP PIPE
	set +e
	if [ -n "$builder" ]; then
		log "빌더 $builder 를 지운다"
		if ! { aws ec2 terminate-instances --instance-ids "$builder" >/dev/null &&
			aws ec2 wait instance-terminated --instance-ids "$builder"; }; then
			log "빌더 $builder 를 지우지 못했다. 손으로 지운다: aws ec2 terminate-instances --instance-ids $builder"
		fi
	fi
	if [ -n "$security_group" ] && ! delete_security_group; then
		log "임시 보안 그룹 $security_group 을 지우지 못했다. 빌더가 지워진 뒤 손으로 지운다: aws ec2 delete-security-group --group-id $security_group"
	fi
}

# 빌더가 terminated가 된 뒤에도 네트워크 인터페이스가 잠시 그룹을 잡고 있어 첫 삭제가 실패할 수 있다.
delete_security_group() {
	local attempt
	for ((attempt = 1; attempt <= SECURITY_GROUP_DELETE_ATTEMPTS; attempt++)); do
		aws ec2 delete-security-group --group-id "$security_group" && return 0
		sleep "$POLL_SECONDS"
	done
	return 1
}

check_account() {
	local account
	account=$(aws sts get-caller-identity --query Account --output text) || die "AWS 자격을 확인하지 못했다"
	[ "$account" = "$ACCOUNT_ID" ] || die "지금 자격의 계정이 $account 다. $ACCOUNT_ID 에서만 굽는다"
}

# 태그에 남길 커밋이 develop에 있어야 이미지에 무엇이 들었는지 나중에 레포에서 찾을 수 있다.
check_source() {
	local changes
	changes=$(git -C "$here" status --porcelain -- provision.sh) || die "provision.sh의 git 상태를 읽지 못했다"
	[ -z "$changes" ] || die "provision.sh에 커밋하지 않은 변경이 있다"
	git -C "$here" fetch --quiet origin develop || die "origin develop을 받지 못했다"
	git -C "$here" merge-base --is-ancestor HEAD origin/develop ||
		die "지금 커밋이 origin/develop에 없다. develop에 머지된 커밋에서 굽는다"
}

create_security_group() {
	security_group=$(aws ec2 create-security-group --vpc-id "$VPC_ID" --group-name "$BUILDER_NAME-$1" \
		--description 'meterengine app image builder, https egress only' \
		--tag-specifications "ResourceType=security-group,Tags=[{Key=Name,Value=$BUILDER_NAME}]" \
		--query GroupId --output text) || die "임시 보안 그룹을 만들지 못했다"
	aws ec2 revoke-security-group-egress --group-id "$security_group" \
		--ip-permissions '[{"IpProtocol":"-1","IpRanges":[{"CidrIp":"0.0.0.0/0"}]}]' >/dev/null ||
		die "임시 보안 그룹의 기본 송신 규칙을 지우지 못했다"
	aws ec2 authorize-security-group-egress --group-id "$security_group" \
		--ip-permissions '[{"IpProtocol":"tcp","FromPort":443,"ToPort":443,"IpRanges":[{"CidrIp":"0.0.0.0/0"}]}]' >/dev/null ||
		die "임시 보안 그룹에 443 송신을 열지 못했다"
}

launch_builder() {
	builder=$(aws ec2 run-instances --image-id "$base_image" --instance-type "$INSTANCE_TYPE" --count 1 \
		--network-interfaces "[{\"DeviceIndex\":0,\"SubnetId\":\"$SUBNET_ID\",\"Groups\":[\"$security_group\"],\"AssociatePublicIpAddress\":true}]" \
		--metadata-options HttpEndpoint=enabled,HttpTokens=required \
		--instance-initiated-shutdown-behavior stop \
		--user-data "file://$provision" \
		--tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$BUILDER_NAME}]" \
		--query 'Instances[0].InstanceId' --output text) || die "빌더를 띄우지 못했다"
}

builder_state() {
	aws ec2 describe-instances --instance-ids "$builder" --query 'Reservations[0].Instances[0].State.Name' --output text
}

image_state() {
	aws ec2 describe-images --image-ids "$image" --query 'Images[0].State' --output text
}

# failed 같은 끝 상태는 다시 available이 되지 않으니 시간 한도까지 기다리지 않는다.
wait_for_image() {
	local i state
	for ((i = 0; i < IMAGE_WAIT_POLLS; i++)); do
		state=$(image_state || true)
		case "$state" in
		available) return 0 ;;
		failed | error | invalid | deregistered)
			die "이미지 $image 가 $state 가 되었다. deregister-image로 지우고 남은 스냅숏이 있으면 delete-snapshot으로 지운다"
			;;
		esac
		sleep "$POLL_SECONDS"
	done
	die "이미지 $image 가 시간 안에 available이 되지 않아 삭제 보호 없이 남았다. describe-images로 상태를 보고, available이 되면 쓸지 지울지 정한다"
}

# 묻는 함수가 원하는 값을 찍을 때까지 기다린다. 정한 횟수 안에 나오지 않으면 1로 끝난다.
wait_for() {
	local wanted="$1" polls="$2" query="$3" i
	for ((i = 0; i < polls; i++)); do
		if [ "$("$query" || true)" = "$wanted" ]; then
			return 0
		fi
		sleep "$POLL_SECONDS"
	done
	return 1
}

check_image_fits() {
	local shape architecture root size
	shape=$(aws ec2 describe-images --image-ids "$image" --output text \
		--query "Images[0].[Architecture,RootDeviceName,BlockDeviceMappings[?DeviceName=='/dev/xvda'].Ebs.VolumeSize | [0]]") ||
		die "이미지 $image 를 읽지 못했다"
	read -r architecture root size <<<"$shape"
	if [ "$architecture" != arm64 ] || [ "$root" != /dev/xvda ] || ! [[ "$size" =~ ^[0-9]+$ ]] || [ "$size" -gt "$LAUNCH_TEMPLATE_ROOT_GIB" ]; then
		die "이미지 $image 가 시작 템플릿에 맞지 않는다(아키텍처 $architecture, 루트 $root, 크기 $size GiB). 쓰지 말고 deregister-image와 delete-snapshot으로 이미지와 스냅숏을 지운다"
	fi
}

main() {
	if [ $# -ne 1 ] || ! [[ "$1" =~ $AMI_PATTERN ]]; then
		die "사용법: $0 <기본 AMI ID>"
	fi
	base_image="$1"
	# 결과 한 줄만 3번으로 내고, aws가 찍는 것은 모두 진행 기록으로 보낸다.
	exec 3>&1 1>&2
	export AWS_REGION="$REGION" AWS_DEFAULT_REGION="$REGION" AWS_PAGER=""

	check_account
	check_source
	local source_commit compose_version stamp image_name tags
	source_commit=$(git -C "$here" rev-parse HEAD)
	compose_version=$(sed -n 's#.*/docker/compose/releases/download/\(v[0-9.]*\)/.*#\1#p' "$provision")
	[ -n "$compose_version" ] || die "provision.sh에서 compose 버전을 찾지 못했다"
	stamp=$(date -u +%Y%m%dT%H%M%SZ)
	image_name="meterengine-app-al2023-arm64-$stamp"

	trap cleanup EXIT
	trap 'exit 1' INT TERM HUP
	create_security_group "$stamp"
	launch_builder
	log "빌더 $builder 가 $base_image 에서 커밋 $source_commit 의 provision.sh를 돌린다"

	if ! wait_for stopped "$STOP_WAIT_POLLS" builder_state; then
		log "빌더 콘솔 출력:"
		aws ec2 get-console-output --instance-id "$builder" --latest --query Output --output text || true
		die "빌더가 시간 안에 꺼지지 않았다. 위 콘솔 출력에서 설치가 어디서 멈췄는지 본다"
	fi
	log "빌더가 꺼졌다. 이미지 $image_name 을 만든다"

	tags="{Key=Name,Value=$image_name},{Key=Role,Value=app-image},{Key=BaseImage,Value=$base_image}"
	tags+=",{Key=ComposeVersion,Value=$compose_version},{Key=SourceCommit,Value=$source_commit}"
	image=$(aws ec2 create-image --instance-id "$builder" --name "$image_name" \
		--description "meterengine app server from $base_image at $source_commit" \
		--tag-specifications "ResourceType=image,Tags=[$tags]" "ResourceType=snapshot,Tags=[$tags]" \
		--query ImageId --output text) || die "이미지를 만들지 못했다"
	wait_for_image
	# 맞지 않는 이미지는 삭제 보호 없이 남겨 바로 지울 수 있게 한다.
	check_image_fits
	aws ec2 enable-image-deregistration-protection --image-id "$image" >/dev/null ||
		die "이미지 $image 에 삭제 보호를 켜지 못했다. 손으로 켠다"

	log "이미지 $image 를 구웠다"
	printf 'image_id=%s\n' "$image" >&3
}

main "$@"
