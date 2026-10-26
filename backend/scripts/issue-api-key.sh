#!/usr/bin/env bash
# 사용법: issue-api-key.sh [live|test]  (DB에 쓰지 않고 키 원문, SHA-256 해시, 끝 네 글자만 출력한다)

set -euo pipefail

SECRET_LENGTH=32

usage() {
  echo "usage: $0 [live|test]" >&2
  exit 2
}

random_secret() {
  local secret=""
  while [ "${#secret}" -lt "$SECRET_LENGTH" ]; do
    secret+=$(head -c 256 /dev/urandom | LC_ALL=C tr -dc 'A-Za-z0-9')
  done
  printf '%s' "${secret:0:$SECRET_LENGTH}"
}

sha256_hex() {
  if command -v shasum >/dev/null 2>&1; then
    printf '%s' "$1" | shasum -a 256 | cut -d ' ' -f 1
  elif command -v sha256sum >/dev/null 2>&1; then
    printf '%s' "$1" | sha256sum | cut -d ' ' -f 1
  else
    echo "shasum or sha256sum is required" >&2
    exit 1
  fi
}

main() {
  [ "$#" -le 1 ] || usage
  local mode="${1:-live}"
  case "$mode" in
    live | test) ;;
    *) usage ;;
  esac

  local key
  key="me_${mode}_$(random_secret)"

  echo "secret: $key"
  echo "sha256: $(sha256_hex "$key")"
  echo "last_four: ${key: -4}"
}

main "$@"
