#!/usr/bin/env python3
"""배포 대상이 이 서버가 맞는지 env 파일과 컨테이너를 건드리기 전에 확인한다."""

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys


AWS_REGION = "ap-northeast-2"
SERVICE_ENVIRONMENTS = ("staging", "production")
SUT_ENVIRONMENTS = ("test-storage", "test-sjpark-m7g", "test-sjpark-t4g")
# 오토스케일링 그룹 서버로 뜨고 deploy.yml이 그룹 이름을 환경 파일에서 읽는 환경이다.
GROUP_ENVIRONMENTS = (*SERVICE_ENVIRONMENTS, "test-sjpark-m7g", "test-sjpark-t4g")
PROFILES = {"agents"}
FIELDS = {"autoscaling_group_name", "database_identifier", "parameter_path", "site_domain", "site_aliases", "compose_profiles",
          "image_repository_prefix", "backend_mem_limit", "backend_max_ram_percentage"}
REQUIRED_PARAMETERS = ["DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "CRYPTO_ENCRYPTION_KEY", "OPERATOR_EMAIL", "OPERATOR_PASSWORD"]
IMDS = "http://169.254.169.254/latest"


def load_target(name: str, directory: Path) -> dict:
    if name not in (*SERVICE_ENVIRONMENTS, *SUT_ENVIRONMENTS):
        raise ValueError(f"환경 이름은 {', '.join((*SERVICE_ENVIRONMENTS, *SUT_ENVIRONMENTS))} 중 하나여야 한다: {name}")
    target = json.loads((directory / f"{name}.json").read_text())
    in_group = name in GROUP_ENVIRONMENTS
    if not isinstance(target, dict) or set(target) != (FIELDS if in_group else FIELDS - {"autoscaling_group_name"}):
        raise ValueError(f"{name}.json의 필드가 다르다")
    if in_group and target["autoscaling_group_name"] != f"meterengine-{name}-app":
        raise ValueError(f"{name}.json의 autoscaling_group_name은 meterengine-{name}-app이어야 한다")
    if not re.fullmatch(r"[a-z][a-z0-9-]*", str(target["database_identifier"])):
        raise ValueError(f"{name}.json의 database_identifier는 전체 주소가 아니라 RDS 식별자다")
    for key in ("parameter_path", "site_domain", "image_repository_prefix", "backend_mem_limit"):
        if not isinstance(target[key], str) or not target[key].strip():
            raise ValueError(f"{name}.json의 {key}가 비어 있다")
    if name in SERVICE_ENVIRONMENTS and target["parameter_path"] != f"/meterengine/{name}":
        raise ValueError(f"{name}.json의 parameter_path는 /meterengine/{name}이어야 한다")
    if name in SUT_ENVIRONMENTS and target["parameter_path"] in ("/meterengine/prod", "/meterengine/production", "/meterengine/staging"):
        raise ValueError(f"{name}.json의 parameter_path가 서비스 환경을 가리킨다")
    aliases = target["site_aliases"]
    if not isinstance(aliases, list) or any(not isinstance(alias, str) or not alias or re.search(r"\s", alias) for alias in aliases):
        raise ValueError(f"{name}.json의 site_aliases 형식이 다르다")
    profiles = target["compose_profiles"]
    if not isinstance(profiles, list) or not set(profiles) <= PROFILES:
        raise ValueError(f"{name}.json의 compose_profiles는 {sorted(PROFILES)} 안에서 고른다")
    if type(target["backend_max_ram_percentage"]) is not int:
        raise ValueError(f"{name}.json의 backend_max_ram_percentage는 정수다")
    return target


def validate_target(target: dict, observed: dict) -> None:
    # 환경 파일에 그룹 이름이 없는 test-storage는 DB만 대조한다. 나머지는 그 환경의 오토스케일링 그룹 서버여야 한다.
    for key in ("autoscaling_group_name", "database_identifier") if "autoscaling_group_name" in target else ("database_identifier",):
        if target.get(key) != observed.get(key):
            raise ValueError(f"{key}가 환경 파일과 다릅니다. 이 서버의 값: {observed.get(key) or '없음'}")


def database_identifier(host: str) -> str:
    return host.split(".", 1)[0]


def autoscaling_group_name() -> str:
    """오토스케일링 그룹 밖 서버(옛 단일 서버)는 태그가 없어 빈 값이다."""
    curl = ["curl", "-fsS", "--noproxy", "*", "--connect-timeout", "2", "--max-time", "5"]
    token = subprocess.check_output([*curl, "-X", "PUT", "-H", "X-aws-ec2-metadata-token-ttl-seconds: 60", f"{IMDS}/api/token"], text=True).strip()
    result = subprocess.run([*curl, "-H", f"X-aws-ec2-metadata-token: {token}", f"{IMDS}/meta-data/tags/instance/aws:autoscaling:groupName"],
                            text=True, capture_output=True)
    return result.stdout.strip() if result.returncode == 0 else ""


def observe_target(target: dict) -> dict:
    database_host = subprocess.check_output(["aws", "ssm", "get-parameter", "--region", AWS_REGION, "--name", f"{target['parameter_path']}/db-host",
                                             "--query", "Parameter.Value", "--output", "text"], text=True).strip()
    observed = {"database_identifier": database_identifier(database_host)}
    if "autoscaling_group_name" in target:
        observed["autoscaling_group_name"] = autoscaling_group_name()
    return observed


def shell_values(target: dict) -> dict:
    return {
        "PARAM_PATH": target["parameter_path"],
        "SITE_DOMAIN": target["site_domain"],
        "SITE_ALIASES": " ".join(target["site_aliases"]),
        "COMPOSE_PROFILES": ",".join(target["compose_profiles"]),
        "BACKEND_MEM_LIMIT": target["backend_mem_limit"],
        "BACKEND_MAX_RAM_PERCENTAGE": str(target["backend_max_ram_percentage"]),
        "IMAGE_REPOSITORY_PREFIX": target["image_repository_prefix"],
        "EXPECTED_DB_IDENTIFIER": target["database_identifier"],
        "REQUIRED_PARAMETERS": " ".join(REQUIRED_PARAMETERS),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("preflight",))
    parser.add_argument("--environment", required=True)
    args = parser.parse_args()
    try:
        target = load_target(args.environment, Path(__file__).resolve().parent / "environments")
        # DB 복구 때 사람이 서버에서만 준다. SSM db-host의 식별자와 같아야 통과하고, 연결 주소는 여전히 SSM 값이다.
        recovery_db_identifier = os.environ.get("RECOVERY_DB_IDENTIFIER")
        if recovery_db_identifier:
            print(f"[deploy] 복구 배포: DB 식별자를 환경 파일 대신 {recovery_db_identifier}와 대조한다", file=sys.stderr)
            target["database_identifier"] = recovery_db_identifier
        validate_target(target, observe_target(target))
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(f"[deploy] 환경 확인 실패: {error}", file=sys.stderr)
        return 2
    for key, value in shell_values(target).items():
        print(f"{key}={shlex.quote(value)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
