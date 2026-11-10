import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
KAFKA_ENV = ("METERENGINE_ROLE", "EVENT_WRITE_PATH", "KAFKA_BOOTSTRAP_SERVERS", "KAFKA_SECURITY_PROTOCOL", "KAFKA_CREATE_TOPICS")


def required_names():
    return set(re.findall(r"\$\{([A-Z0-9_]+):?\?", COMPOSE_FILE.read_text()))


def backend_environment(kafka_values):
    environment = {name: value for name, value in os.environ.items() if name not in KAFKA_ENV}
    environment.update({name: "dummy" for name in required_names()})
    environment["BACKEND_MEM_LIMIT"] = "768m"
    environment["SITE_DOMAIN"] = "billing.example.com"
    environment.update(kafka_values)
    result = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
        capture_output=True, text=True, env=environment, timeout=30)
    if result.returncode != 0:
        raise AssertionError(f"compose config failed: {result.stderr}")
    return json.loads(result.stdout)["services"]["backend"]["environment"]


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class KafkaEnvironmentTest(unittest.TestCase):
    # 값이 없는 환경(staging, production)은 지금처럼 한 서버가 전부 맡고 요청 안에서 DB에 저장한다.
    def test_compose_keeps_the_direct_path_when_unset(self):
        backend = backend_environment({})
        self.assertEqual(backend.get("METERENGINE_ROLE"), "all")
        self.assertEqual(backend.get("EVENT_WRITE_PATH"), "direct")
        self.assertEqual(backend.get("KAFKA_SECURITY_PROTOCOL"), "PLAINTEXT")
        self.assertEqual(backend.get("KAFKA_CREATE_TOPICS"), "false")

    def test_compose_passes_the_kafka_values_when_set(self):
        values = {"METERENGINE_ROLE": "consumer", "EVENT_WRITE_PATH": "kafka",
                  "KAFKA_BOOTSTRAP_SERVERS": "b-1.example:9094,b-2.example:9094", "KAFKA_SECURITY_PROTOCOL": "SSL",
                  "KAFKA_CREATE_TOPICS": "true"}
        backend = backend_environment(values)
        for name, value in values.items():
            self.assertEqual(backend.get(name), value, name)


if __name__ == "__main__":
    unittest.main()
