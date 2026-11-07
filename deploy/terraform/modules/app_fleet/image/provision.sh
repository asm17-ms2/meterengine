#!/bin/bash
# 앱 서버 이미지를 굽는 빌더의 user_data다. build-app-image.sh가 넘기고, 끝나면 스스로 꺼진다.
# 중간에 실패하면 꺼지지 않아 굽는 스크립트가 시간 초과로 보고 이미지를 만들지 않는다.
set -euo pipefail

dnf install -y docker git python3 openssl
# 빌더에서 띄우면 데몬 상태가 이미지에 남는다. 등록만 해 두면 서버가 부팅할 때 systemd가 띄운다.
systemctl enable docker

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
  https://github.com/docker/compose/releases/download/v5.5.0/docker-compose-linux-aarch64 \
  --output "$work/docker-compose"
printf '%s  %s\n' ff42489f5a9b879d5d117c5ffea6defc27390b3286da8ad52cbc9c6ab5df590e "$work/docker-compose" | sha256sum --check
install -D -m 0755 "$work/docker-compose" /usr/libexec/docker/cli-plugins/docker-compose

docker --version
docker compose version
git --version
python3 --version

dnf clean all
# 비워 두면 이 이미지로 뜨는 서버마다 첫 부팅에서 새 값을 만든다.
truncate -s 0 /etc/machine-id
echo '[app-image] 굽기 끝. 이 줄 아래는 이 이미지로 뜬 서버의 부팅 기록이다'
systemctl poweroff
