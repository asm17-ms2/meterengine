#!/usr/bin/env bash
# 조직을 여러 곳 섞어 재는 실험에 쓸 조직과 키를 SUT DB에 직접 넣는다. 가입과 승인을 조직 수만큼 손으로 하지 않으려는 것이다.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/../../lib/common.sh"
. "$SCRIPT_DIR/lib.sh"

readonly ORGANIZATION_NAME_PREFIX='k6-organization-'
readonly KEY_LABEL='k6 측정'
readonly KEY_MODE=live
readonly SECRET_RANDOM_BYTES=16
readonly LAST_FOUR_LENGTH=4
readonly MAX_ORGANIZATIONS=99

# backend가 만드는 키와 같은 모양(me_<모드>_ 뒤에 영숫자 32자)이다.
new_secret() {
	printf 'me_%s_%s' "$KEY_MODE" "$(od -An -N"$SECRET_RANDOM_BYTES" -tx1 /dev/urandom | tr -d ' \n')"
}

# DB에는 backend가 대조하는 값인 원문의 SHA-256만 들어간다.
key_hash_hex() {
	printf '%s' "$1" | sha256sum | cut -d' ' -f1
}

# 원문은 키 파일에만, 해시와 끝 네 글자는 DB에 넣을 파일에만 쓴다.
issue_keys() {
	local count="$1" key_file="$2" issued_file="$3" ordinal secret
	: >"$key_file"
	chmod 600 "$key_file"
	for ordinal in $(seq 1 "$count"); do
		secret=$(new_secret)
		printf '%s\n' "$secret" >>"$key_file"
		printf '%s\t%s\t%s\n' "$ordinal" "$(key_hash_hex "$secret")" "${secret: -$LAST_FOUR_LENGTH}" >>"$issued_file"
	done
}

# 다시 돌리면 있는 조직은 그대로 두고, 앞서 넣은 키를 폐기한 뒤 새 키를 넣는다.
store_issued_keys() {
	psql -X -q -A -t -v ON_ERROR_STOP=1 -v issued_file="$1" -v name_prefix="$ORGANIZATION_NAME_PREFIX" \
		-v label="$KEY_LABEL" -v mode="$KEY_MODE" <<'SQL'
BEGIN;
CREATE TEMP TABLE issued (ordinal int PRIMARY KEY, key_hash_hex text NOT NULL, last_four text NOT NULL);
\set copy '\\copy issued FROM ' :'issued_file'
:copy
CREATE TEMP TABLE named AS
SELECT :'name_prefix' || lpad(ordinal::text, 2, '0') AS name, key_hash_hex, last_four FROM issued;
INSERT INTO organization (name)
SELECT name FROM named WHERE NOT EXISTS (SELECT 1 FROM organization o WHERE o.name = named.name);
UPDATE api_key SET revoked_at = now()
WHERE revoked_at IS NULL AND label = :'label'
  AND organization_id IN (SELECT id FROM organization WHERE starts_with(name, :'name_prefix'));
INSERT INTO api_key (id, organization_id, mode, label, key_hash, last_four, scopes, created_at, environment_id)
SELECT gen_random_uuid(), o.id, :'mode', :'label', decode(named.key_hash_hex, 'hex'), named.last_four,
  ARRAY['ingest', 'read']::varchar[], now(), e.id
FROM named
JOIN organization o ON o.name = named.name
JOIN organization_environment e ON e.organization_id = o.id AND e.mode = :'mode';
COMMIT;
SELECT count(*) FROM api_key
WHERE revoked_at IS NULL AND label = :'label'
  AND organization_id IN (SELECT id FROM organization WHERE starts_with(name, :'name_prefix'));
SQL
}

main() {
	[ $# -eq 2 ] || die "사용법: $0 <조직 수> <키를 쓸 파일>"
	local count="$1" key_file="$2"
	[[ "$count" =~ ^[1-9][0-9]?$ ]] || die "조직 수는 1에서 ${MAX_ORGANIZATIONS}까지다: $count"
	need_cmd psql sha256sum od
	[ -n "${PGHOST:-}" ] || use_sut_db

	local issued_file usable
	issued_file=$(mktemp)
	trap "rm -f '$issued_file'" EXIT
	umask 077
	issue_keys "$count" "$key_file" "$issued_file"
	usable=$(store_issued_keys "$issued_file") || die "조직과 키를 넣지 못했다"
	[ "$usable" = "$count" ] || die "쓸 수 있는 키가 ${usable}개다. ${count}개여야 한다"
	log "조직 ${count}곳의 키를 $key_file 에 썼다. 서버가 키를 다시 읽는 30초 뒤부터 쓸 수 있다"
}

main "$@"
