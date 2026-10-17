#!/usr/bin/env bash
set -euxo pipefail

K6_VERSION=v2.3.0
K6_LINUX_ARM64_SHA256=5ca3433e8201da72a284aaa241a1bb5fb47f4abb4e384d39410ddd8062f49b90
K6_PACKAGE="k6-${K6_VERSION}-linux-arm64"

dnf install -y git jq tar gzip postgresql18
git clone https://github.com/asm17-ms2/meterengine.git /opt/meterengine

cd "$(mktemp -d)"
curl -fsSL --retry 5 -O "https://github.com/grafana/k6/releases/download/${K6_VERSION}/${K6_PACKAGE}.tar.gz"
echo "${K6_LINUX_ARM64_SHA256}  ${K6_PACKAGE}.tar.gz" | sha256sum -c -
tar -xzf "${K6_PACKAGE}.tar.gz"
install -m 0755 "${K6_PACKAGE}/k6" /usr/local/bin/k6
