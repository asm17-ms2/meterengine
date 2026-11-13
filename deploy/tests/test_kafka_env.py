import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
KAFKA_ENV = ("METERENGINE_ROLE", "EVENT_WRITE_PATH", "KAFKA_BOOTSTRAP_SERVERS", "KAFKA_SECURITY_PROTOCOL", "KAFKA_CREATE_TOPICS")
# 측정에서 바꿔 가며 재는 값과, 넘기지 않았을 때 backend가 쓰는 값.
KAFKA_TUNING_DEFAULTS = {
    "KAFKA_CONSUMER_THREADS": "1",
    "KAFKA_CONSUMER_MAX_POLL_RECORDS": "500",
    "KAFKA_CONSUMER_SESSION_TIMEOUT_MS": "15000",
    "KAFKA_PRODUCER_LINGER_MS": "25",
    "KAFKA_PRODUCER_BATCH_SIZE": "16384",
    "KAFKA_PRODUCER_COMPRESSION_TYPE": "lz4",
    "KAFKA_PRODUCER_DELIVERY_TIMEOUT_MS": "15000",
    "KAFKA_PUBLISH_TIMEOUT": "20s",
}


def required_names():
    return set(re.findall(r"\$\{([A-Z0-9_]+):?\?", COMPOSE_FILE.read_text()))


def backend_environment(kafka_values):
    environment = {name: value for name, value in os.environ.items()
                   if name not in KAFKA_ENV and name not in KAFKA_TUNING_DEFAULTS}
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

    # staging, production과 같이 쓰는 파일이라, 값을 넘기지 않으면 지금 동작과 같아야 한다.
    def test_compose_keeps_the_current_tuning_when_unset(self):
        backend = backend_environment({})
        for name, value in KAFKA_TUNING_DEFAULTS.items():
            self.assertEqual(backend.get(name), value, name)

    def test_compose_passes_the_tuning_values_when_set(self):
        values = {"KAFKA_CONSUMER_THREADS": "3", "KAFKA_CONSUMER_MAX_POLL_RECORDS": "1000",
                  "KAFKA_CONSUMER_SESSION_TIMEOUT_MS": "30000", "KAFKA_PRODUCER_LINGER_MS": "50",
                  "KAFKA_PRODUCER_BATCH_SIZE": "65536", "KAFKA_PRODUCER_COMPRESSION_TYPE": "zstd",
                  "KAFKA_PRODUCER_DELIVERY_TIMEOUT_MS": "30000", "KAFKA_PUBLISH_TIMEOUT": "35s"}
        backend = backend_environment(values)
        for name, value in values.items():
            self.assertEqual(backend.get(name), value, name)


if __name__ == "__main__":
    unittest.main()
