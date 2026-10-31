#!/usr/bin/env bash
set -euo pipefail
set +x

fail() { printf '%s\n' "$*" >&2; exit 2; }
[ "$#" -ge 4 ] || fail 'usage: migrate.sh ENVIRONMENT BACKEND_IMAGE_DIGEST LOCAL_PORT CA_FILE [--migrate-only | --bootstrap-database | --activate-target --fresh-target]'
operation=--migrate-only
case "$#" in
  4) ;;
  5) case "$5" in --migrate-only | --bootstrap-database) operation=$5 ;; *) fail 'invalid database operation' ;; esac ;;
  6) [ "$5" = --activate-target ] && [ "$6" = --fresh-target ] || fail 'invalid database operation'; operation=--activate-target ;;
  *) fail 'invalid database operation' ;;
esac
case "$1" in staging | production) ;; *) fail 'environment must be staging or production' ;; esac
[ "$1" = staging ] || [ "$operation" = --migrate-only ] || fail 'bootstrap and fresh activation require staging'
[[ "$2" =~ ^928618307647\.dkr\.ecr\.ap-northeast-2\.amazonaws\.com/meterengine-backend@sha256:[a-f0-9]{64}$ ]] || fail 'a verified backend image digest is required'
[[ "$3" =~ ^[0-9]+$ ]] && [ "$3" -ge 1024 ] && [ "$3" -le 65535 ] || fail 'invalid local forwarding port'
[ -f "$4" ] && [ -r "$4" ] || fail 'a verified RDS CA file is required'
case "$(uname -s)" in
  Linux) ;;
  Darwin)
    [ "$1" = staging ] || fail 'production migration requires Linux host networking'
    docker_platform=$(docker info --format '{{json .}}' 2>/dev/null) || fail 'cannot verify OrbStack engine'
    printf '%s' "$docker_platform" | python3 -c '
import json, sys
try:
    info = json.load(sys.stdin)
    valid = (info["OperatingSystem"] == "OrbStack" and info["OSType"] == "linux"
             and info["Architecture"] in ("arm64", "aarch64"))
except (ValueError, KeyError, TypeError):
    valid = False
sys.exit(0 if valid else 2)
' || fail 'staging on Darwin requires OrbStack Linux ARM64'
    ;;
  *) fail 'unsupported migration platform' ;;
esac
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ca_file=$(cd "$(dirname "$4")" && pwd)/$(basename "$4")
umask 077
work=$(mktemp -d)
container_name=meterengine-migration-${work##*/}
docker_pid=
cleanup() {
  rm -rf "$work"
  [ -z "$docker_pid" ] || kill "$docker_pid" 2>/dev/null || true
  docker rm --force "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
export AWS_REGION=ap-northeast-2
python3 - "$script_dir" "$1" "$3" "$operation" >"$work/migration.env" <<'PY'
import json
import os
from pathlib import Path
import subprocess
import sys
sys.path.insert(0, sys.argv[1])
from environment import load_target


def read(command):
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode:
        raise ValueError("AWS read failed")
    return json.loads(result.stdout)


try:
    target = load_target(sys.argv[2], Path(sys.argv[1]) / "environments")
    operation = sys.argv[4]
    identifier = os.environ.get("DB_TARGET_IDENTIFIER", "")
    if operation == "--activate-target" and (not identifier or identifier != target["database_identifier"]
                                             or any(char in identifier for char in "\r\n\0")):
        raise ValueError("fresh activation target mismatch")
    identity = read(["aws", "sts", "get-caller-identity", "--output", "json"])
    if identity["Account"] != target["aws_account_id"] or target["aws_region"] != "ap-northeast-2":
        raise ValueError("account or region mismatch")
    path = target["parameter_path"]
    names = [path + "/" + suffix for suffix in ("db-host", "db-port", "db-name")]
    names += [path + "-migration/" + suffix for suffix in ("db-migration-username", "db-migration-password")]
    batches = [names]
    if operation == "--bootstrap-database":
        batches.append([path + "/db-" + role + "-" + field
                        for role in ("shared", "test", "live") for field in ("username", "password")])
    values = {}
    for batch in batches:
        response = read(["aws", "ssm", "get-parameters", "--names", *batch, "--with-decryption", "--output", "json"])
        if not isinstance(response, dict) or response.get("InvalidParameters", []) != []:
            raise ValueError("owner parameter response mismatch")
        parameters = response["Parameters"]
        if not isinstance(parameters, list) or len(parameters) != len(batch):
            raise ValueError("owner parameter response mismatch")
        received = set()
        for parameter in parameters:
            name, value = parameter["Name"], parameter["Value"]
            if name not in batch or name in received or name in values:
                raise ValueError("owner parameter response mismatch")
            if not isinstance(value, str) or not value.strip() or any(char in value for char in "\r\n\0"):
                raise ValueError("invalid owner parameter value")
            received.add(name)
            values[name] = value
        if received != set(batch):
            raise ValueError("owner parameter response mismatch")
    if values[path + "/db-host"] != target["database_host"] or values[path + "/db-port"] != "5432" or values[path + "/db-name"] != "meterengine_app":
        raise ValueError("database target mismatch")
    if values[path + "-migration/db-migration-username"] != "meterengine_owner":
        raise ValueError("owner role mismatch")
    environment = {
        "DB_HOST": target["database_host"], "DB_PORT": sys.argv[3], "DB_NAME": "meterengine_app",
        "DB_MIGRATION_USERNAME": values[path + "-migration/db-migration-username"],
        "DB_MIGRATION_PASSWORD": values[path + "-migration/db-migration-password"],
        "DB_SSL_ROOT_CERT": "/etc/meterengine/rds-ca.pem"
    }
    if operation == "--bootstrap-database":
        for role in ("shared", "test", "live"):
            username = values[path + "/db-" + role + "-username"]
            if username != "meterengine_" + role:
                raise ValueError("runtime role mismatch")
            environment["DB_" + role.upper() + "_USERNAME"] = username
            environment["DB_" + role.upper() + "_PASSWORD"] = values[path + "/db-" + role + "-password"]
    elif operation == "--activate-target":
        environment["DB_TARGET_IDENTIFIER"] = identifier
    if any(not isinstance(value, str) or not value or any(char in value for char in "\r\n\0") for value in environment.values()):
        raise ValueError("invalid owner parameter value")
    output = "".join(f"{name}={value}\n" for name, value in environment.items())
except (ValueError, KeyError, TypeError, OSError):
    print("migration parameter validation failed", file=sys.stderr)
    sys.exit(2)
sys.stdout.write(output)
PY
chmod 0600 "$work/migration.env"
host=$(sed -n 's/^DB_HOST=//p' "$work/migration.env")
command=("$operation")
[ "$operation" != --activate-target ] || command+=(--fresh-target)
docker run --rm --name "$container_name" --network host --add-host "$host:127.0.0.1" \
  --env-file "$work/migration.env" \
  --volume "$ca_file:/etc/meterengine/rds-ca.pem:ro" "$2" "${command[@]}" &
docker_pid=$!
wait "$docker_pid"
