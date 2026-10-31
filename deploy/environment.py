#!/usr/bin/env python3
"""Validate deployment targets before credentials or running services are touched."""

import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys


TARGETS = {"staging": "staging", "production": "production", "test-slo": "sut", "test-storage": "sut"}
IDENTITY_FIELDS = ("aws_account_id", "aws_region", "instance_id", "database_identifier", "database_host")
CORE_SERVICES = {"caddy", "frontend", "backend-blue", "backend-green"}
SERVICES = CORE_SERVICES | {"prometheus", "grafana", "node-exporter", "postgres-exporter", "cloudwatch-exporter", "cadvisor", "oauth2-proxy"}


def load_target(name: str, directory: Path) -> dict:
    if name not in TARGETS:
        raise ValueError("environment must be staging, production, test-slo or test-storage")
    target = json.loads((directory / f"{name}.json").read_text())
    fields = {"schema_version", "deployment_environment", *IDENTITY_FIELDS, "parameter_path", "site_domain", "site_aliases", "compose_project", "enabled_services"}
    if not isinstance(target, dict) or set(target) != fields:
        raise ValueError("invalid manifest fields")
    if type(target.get("schema_version")) is not int or target.get("schema_version") != 1 or target.get("deployment_environment") != TARGETS[name]:
        raise ValueError("invalid manifest schema_version or deployment_environment")
    for key in (*IDENTITY_FIELDS, "parameter_path", "site_domain", "compose_project"):
        if not isinstance(target.get(key), str) or not target[key].strip():
            raise ValueError(f"unconfigured target: {key}")
    if not re.fullmatch(r"\d{12}", target["aws_account_id"]):
        raise ValueError("invalid aws_account_id")
    if not re.fullmatch(r"i-[0-9a-f]{8,17}", target["instance_id"]):
        raise ValueError("invalid instance_id")
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", target["compose_project"]):
        raise ValueError("invalid compose_project")
    if not isinstance(target.get("site_aliases"), list) or any(
            not isinstance(alias, str) or not alias or any(c.isspace() for c in alias)
            for alias in target["site_aliases"]):
        raise ValueError("invalid site_aliases")
    enabled = target.get("enabled_services")
    if not isinstance(enabled, list) or not all(isinstance(service, str) for service in enabled):
        raise ValueError("invalid enabled_services")
    if not CORE_SERVICES <= set(enabled) or not set(enabled) <= SERVICES or len(enabled) != len(set(enabled)):
        raise ValueError("invalid enabled_services")
    if "grafana" in enabled and "prometheus" not in enabled:
        raise ValueError("grafana requires prometheus")
    if name in ("staging", "production") and target["parameter_path"] != f"/meterengine/{name}":
        raise ValueError("parameter_path must match environment")
    if name.startswith("test-") and target["parameter_path"] in ("/meterengine/prod", "/meterengine/production", "/meterengine/staging"):
        raise ValueError("SUT parameter_path must not use a service environment")
    return target


def validate_target(target: dict, observed: dict) -> None:
    if not isinstance(observed, dict):
        raise ValueError("observed must be an object")
    for key in IDENTITY_FIELDS:
        if not observed.get(key) or observed[key] != target[key]:
            raise ValueError(f"target mismatch: {key}")


def command_json(*args):
    return json.loads(subprocess.check_output(args, text=True))


def observe_target(target):
    token = subprocess.check_output([
        "curl", "-fsS", "--noproxy", "*", "--connect-timeout", "2", "--max-time", "5", "-X", "PUT",
        "-H", "X-aws-ec2-metadata-token-ttl-seconds: 60", "http://169.254.169.254/latest/api/token"], text=True).strip()
    identity = command_json("curl", "-fsS", "--noproxy", "*", "--connect-timeout", "2", "--max-time", "5",
                            "-H", f"X-aws-ec2-metadata-token: {token}",
                            "http://169.254.169.254/latest/dynamic/instance-identity/document")
    observed = {"aws_account_id": identity["accountId"], "aws_region": identity["region"],
                "instance_id": identity["instanceId"]}
    for key in ("aws_account_id", "aws_region", "instance_id"):
        if observed[key] != target[key]:
            raise ValueError(f"target mismatch: {key}")
    region = target["aws_region"]
    caller = command_json("aws", "sts", "get-caller-identity", "--region", region, "--output", "json")
    if caller["Account"] != target["aws_account_id"]:
        raise ValueError("target mismatch: aws_account_id (caller)")
    databases = command_json("aws", "rds", "describe-db-instances", "--region", region,
                             "--db-instance-identifier", target["database_identifier"], "--output", "json")["DBInstances"]
    if len(databases) != 1:
        raise ValueError("expected exactly one database")
    observed.update(database_identifier=databases[0]["DBInstanceIdentifier"], database_host=databases[0]["Endpoint"]["Address"])
    validate_target(target, observed)
    parameter = command_json("aws", "ssm", "get-parameter", "--region", region,
                             "--name", target["parameter_path"] + "/db-host", "--output", "json")["Parameter"]
    if parameter.get("Type", "String") != "String" or parameter["Value"] != target["database_host"]:
        raise ValueError("target mismatch: db-host parameter")
    return observed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("check", "preflight"))
    parser.add_argument("--environment", required=True)
    parser.add_argument("--observed", type=Path)
    args = parser.parse_args()
    try:
        target = load_target(args.environment, Path(__file__).resolve().parent / "environments")
        if args.command == "check":
            if args.observed is None:
                raise ValueError("--observed is required")
            validate_target(target, json.loads(args.observed.read_text()))
            print(json.dumps(target, sort_keys=True))
        else:
            observe_target(target)
            fields = {"METERENGINE_DEPLOYMENT_ENVIRONMENT": target["deployment_environment"], "EXPECTED_DB_HOST": target["database_host"], "PARAM_PATH": target["parameter_path"], "SITE_DOMAIN": target["site_domain"],
                      "SITE_ALIASES": " ".join(target["site_aliases"]), "COMPOSE_PROJECT": target["compose_project"],
                      "AWS_REGION": target["aws_region"], "ENABLED_SERVICES": " ".join(target["enabled_services"]),
                      "ECR_REGISTRY": f"{target['aws_account_id']}.dkr.ecr.{target['aws_region']}.amazonaws.com"}
            for key, value in fields.items():
                print(f"{key}={shlex.quote(value)}")
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        detail = f"{error.cmd[0]} exited {error.returncode}" if isinstance(error, subprocess.CalledProcessError) else str(error)
        print(f"[deploy] environment validation failed: {detail}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
