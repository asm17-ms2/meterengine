#!/usr/bin/env bash
set -euo pipefail

fail() { printf '%s\n' "$*" >&2; exit 2; }
work=
trap '[ -z "$work" ] || rm -rf "$work"' EXIT

case "${1:-}" in
install)
  [ "$#" -eq 2 ] || fail 'usage: rds-ca.sh install DESTINATION'
  destination=$2
  [ -d "$(dirname "$destination")" ] || fail 'CA destination directory is missing'
  work=$(mktemp -d "$(dirname "$destination")/.rds-ca.XXXXXX")
  curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
    --output "$work/bundle.pem" https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem
  awk -v directory="$work" '
    /^-----BEGIN CERTIFICATE-----$/ { if (inside) exit 1; inside=1; count++; file=directory "/cert-" count ".pem" }
    inside { print > file }
    /^-----END CERTIFICATE-----$/ { if (!inside) exit 1; inside=0; close(file); next }
    !inside && NF && $0 !~ /^-----BEGIN CERTIFICATE-----$/ { exit 1 }
    END { if (inside || !count) exit 1 }
  ' "$work/bundle.pem" || fail 'CA bundle is malformed'
  for certificate in "$work"/cert-*.pem; do
    openssl x509 -in "$certificate" -noout -checkend 0 >/dev/null 2>&1 || fail 'CA certificate is invalid or expired'
    openssl verify -CAfile "$work/bundle.pem" "$certificate" >/dev/null 2>&1 || fail 'CA certificate validity verification failed'
  done
  chmod 0644 "$work/bundle.pem"
  mv -f "$work/bundle.pem" "$destination"
  ;;
verify)
  [ "$#" -eq 4 ] || fail 'usage: rds-ca.sh verify RDS_HOST PORT CA_FILE'
  [[ "$2" =~ ^[a-zA-Z0-9.-]+$ ]] && [[ "$3" =~ ^[0-9]+$ ]] || fail 'invalid TLS target'
  [ -r "$4" ] || fail 'CA file is missing'
  work=$(mktemp -d)
  if ! openssl s_client -starttls postgres -connect "$2:$3" -servername "$2" \
    -verify_hostname "$2" -verify_return_error -CAfile "$4" </dev/null >"$work/verify.log" 2>&1; then
    fail 'RDS TLS verification failed'
  fi
  ;;
*) fail 'usage: rds-ca.sh install DESTINATION | verify RDS_HOST PORT CA_FILE' ;;
esac
