#!/usr/bin/env python3
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


class RuntimeParameterError(ValueError):
    pass


CONTRACT = Path(__file__).with_name("runtime-parameters.v1.json")


def selected_parameters(services):
    contract = json.loads(CONTRACT.read_text())
    if contract.get("schema_version") != 1:
        raise RuntimeParameterError("unsupported runtime parameter contract")
    selected = []
    for parameter in contract["parameters"]:
        name = parameter["parameter_name"]
        environment = parameter["environment_name"]
        if not re.fullmatch(r"[a-z][a-z0-9-]+", name) or not re.fullmatch(r"[A-Z][A-Z0-9_]+", environment):
            raise RuntimeParameterError("invalid runtime parameter name")
        if "migration" in name or name in ("db-username", "db-password"):
            raise RuntimeParameterError("owner credentials cannot be runtime parameters")
        service = parameter["service"]
        if service in services or service == "backend" and any(name.startswith("backend-") for name in services):
            selected.append(parameter)
    if len({p["parameter_name"] for p in selected}) != len(selected) or len({p["environment_name"] for p in selected}) != len(selected):
        raise RuntimeParameterError("duplicate runtime parameter names")
    return selected


def env_value(value):
    if not isinstance(value, str) or any(char in value for char in "\r\n\0"):
        raise RuntimeParameterError("runtime parameter must be a single-line string")
    if re.fullmatch(r"[a-zA-Z0-9_./:@+-]+", value):
        return value
    return json.dumps(value, ensure_ascii=False).replace("$", "$$")


def snapshot(environment, services, expected_db_host):
    selected = selected_parameters(services)
    path = f"/meterengine/{environment}/"
    expected = {path + parameter["parameter_name"]: parameter for parameter in selected}
    values = {}
    missing = set()
    names = list(expected)
    for offset in range(0, len(names), 10):
        result = subprocess.run(["aws", "ssm", "get-parameters", "--names", *names[offset:offset + 10],
                                 "--with-decryption", "--output", "json"], capture_output=True, text=True)
        if result.returncode:
            raise RuntimeParameterError("runtime parameter read failed")
        response = json.loads(result.stdout)
        batch = names[offset:offset + 10]
        for name in response.get("InvalidParameters", []):
            if name not in batch or name in missing or name in values:
                raise RuntimeParameterError("unexpected runtime parameter response")
            if expected[name]["required"]:
                raise RuntimeParameterError("required runtime parameter is missing")
            missing.add(name)
        for parameter in response["Parameters"]:
            name = parameter["Name"]
            if name not in batch or name in values or name in missing:
                raise RuntimeParameterError("unexpected runtime parameter response")
            values[name] = parameter["Value"]
    if any(parameter["required"] and name not in values for name, parameter in expected.items()):
        raise RuntimeParameterError("required runtime parameter is missing")
    runtime = {parameter["environment_name"]: values[name] for name, parameter in expected.items() if name in values}
    for name, value in values.items():
        if (not expected[name]["required"] and (not isinstance(value, str) or not value.strip())
                and not (expected[name]["environment_name"] == "METERENGINE_STAGING_WEBHOOK_ALLOWED_URLS" and value == "")):
            raise RuntimeParameterError("optional runtime parameter is empty")
    if runtime.get("DB_HOST") != expected_db_host or runtime.get("DB_NAME") != "meterengine_app" or runtime.get("DB_PORT") != "5432":
        raise RuntimeParameterError("runtime db-host/db-port/db-name differs from the verified target")
    if runtime.get("METERENGINE_CUTOVER_MODE") not in ("read_only", "active"):
        raise RuntimeParameterError("invalid cutover mode")
    for parameter in selected:
        if parameter["required"] and (not isinstance(runtime[parameter["environment_name"]], str) or not runtime[parameter["environment_name"]].strip()):
            raise RuntimeParameterError("required runtime parameter is empty")
    for name, username in (("SHARED", "meterengine_shared"), ("TEST", "meterengine_test"), ("LIVE", "meterengine_live")):
        if runtime.get(f"DB_{name}_USERNAME") != username:
            raise RuntimeParameterError("runtime database role differs from the approved role")
    return "".join(f"{name}={env_value(value)}\n" for name, value in runtime.items())


def read_env_value(path, name):
    matches = [line.partition("=")[2] for line in Path(path).read_text().splitlines() if line.partition("=")[0] == name]
    if len(matches) != 1:
        raise RuntimeParameterError("runtime environment value is missing or duplicated")
    value = matches[0]
    return json.loads(value).replace("$$", "$") if value.startswith('"') else value


def main():
    if len(sys.argv) == 4 and sys.argv[1] == "read-env":
        try:
            value = read_env_value(sys.argv[2], sys.argv[3])
        except (ValueError, OSError):
            print("runtime environment read failed", file=sys.stderr)
            return 2
        sys.stdout.write(value)
        return 0
    parser = argparse.ArgumentParser()
    parser.add_argument("--environment", required=True, choices=("staging", "production"))
    parser.add_argument("--expected-db-host", required=True)
    parser.add_argument("--services", nargs="+", required=True)
    args = parser.parse_args()
    try:
        output = snapshot(args.environment, args.services, args.expected_db_host)
    except RuntimeParameterError as error:
        print(str(error), file=sys.stderr)
        return 2
    except (ValueError, KeyError, TypeError, OSError):
        print("runtime parameter validation failed", file=sys.stderr)
        return 2
    sys.stdout.write(output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
