#!/bin/bash
set -euo pipefail

# 공인 IP가 붙고 경로가 잡힐 때까지 외부 연결을 기다린다.
for attempt in {1..60}; do
  if curl --fail --silent --show-error --connect-timeout 5 --max-time 10 https://github.com/ >/dev/null 2>&1; then
    break
  fi
  [ "$attempt" -lt 60 ] || exit 1
  sleep 5
done

dnf install -y docker git python3 amazon-ssm-agent
systemctl enable --now docker amazon-ssm-agent
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
  https://github.com/docker/compose/releases/download/v5.5.0/docker-compose-linux-aarch64 \
  --output "$work/docker-compose"
printf '%s  %s\n' ff42489f5a9b879d5d117c5ffea6defc27390b3286da8ad52cbc9c6ab5df590e "$work/docker-compose" | sha256sum --check
install -D -m 0755 "$work/docker-compose" /usr/libexec/docker/cli-plugins/docker-compose
install -d -m 0755 /etc/meterengine
if [ ! -d /opt/meterengine/.git ]; then
  git clone https://github.com/asm17-ms2/meterengine.git /opt/meterengine
fi
# 비밀 파라미터가 아직 자리 값이라 start.sh는 사람이 값을 넣은 뒤 SSM으로 부른다.
