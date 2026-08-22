"""bridge_state 테스트. 네트워크 대신 가짜 클라이언트를 쓴다."""

import json
import os
import subprocess
import tempfile
import unittest

from bridge.state import (
    MERGED,
    NAMED,
    SKIPPED,
    BridgeConfig,
    BridgeState,
    CustomerResolver,
    project_for_cwd,
    repo_name,
)

DEMO_CUSTOMER = "35bc8d12-9d38-57ab-bc9b-bbd35d779a26"
OTHER_CUSTOMER = "008cd6a7-6ff9-505d-9421-747e7d2d62aa"


class FakeResult:
    def __init__(self, status, body):
        self.status = status
        self.body = body
        self.body_text = json.dumps(body, ensure_ascii=False) if body is not None else ""
        self.elapsed_ms = 1


class FakeClient:
    """ApiClient 대역. 고객 목록과 등록만 흉내 낸다."""

    def __init__(self, customers=None):
        self.customers = list(customers or [])
        self.created = []
        self.list_calls = 0

    def get_customers(self):
        self.list_calls += 1
        return FakeResult(200, {"customers": list(self.customers)})

    def create_customer(self, name):
        self.created.append(name)
        customer_id = OTHER_CUSTOMER if len(self.created) > 1 else DEMO_CUSTOMER
        self.customers.append({"customer_id": customer_id, "name": name})
        return FakeResult(201, {"customer_id": customer_id, "name": name})


class ConfigTest(unittest.TestCase):
    def test_파일이_없으면_기본값이다(self):
        with tempfile.TemporaryDirectory() as directory:
            config = BridgeConfig.load(os.path.join(directory, "없음.json"))
        self.assertEqual(config.base_url, "http://localhost:8080")
        self.assertEqual(config.allow, [])

    def test_기본_전송_대상은_로컬이다(self):
        """usage_event는 지울 수 없다. 배포 주소는 손으로 적게 한다."""
        self.assertNotIn("meterengine.com", BridgeConfig().base_url)

    def test_저장한_것을_그대로_읽는다(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "bridge.json")
            BridgeConfig(owner="박성종", allow=["meterengine"], deny=["비밀"]).save(path)
            config = BridgeConfig.load(path)
        self.assertEqual(config.owner, "박성종")
        self.assertEqual(config.allow, ["meterengine"])
        self.assertEqual(config.deny, ["비밀"])

    def test_org_id가_UUID가_아니면_거부한다(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "bridge.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump({"org_id": "아무거나"}, f)
            with self.assertRaises(ValueError):
                BridgeConfig.load(path)

    def test_고객_이름에_주인이_들어간다(self):
        config = BridgeConfig(owner="박성종")
        self.assertEqual(config.customer_name("meterengine"), "meterengine(박성종)")

    def test_주인이_없으면_프로젝트_이름만_쓴다(self):
        self.assertEqual(BridgeConfig().customer_name("meterengine"), "meterengine")

    def test_고객_이름은_255자를_넘지_않는다(self):
        config = BridgeConfig(owner="박" * 200)
        self.assertLessEqual(len(config.customer_name("p" * 200)), 255)


class ProjectForCwdTest(unittest.TestCase):
    def test_허용_목록_밖은_폴백으로_합친다(self):
        config = BridgeConfig(allow=["meterengine"], fallback_project="기타 프로젝트")
        with tempfile.TemporaryDirectory() as directory:
            secret = os.path.join(directory, "내-비밀-사이드프로젝트")
            os.makedirs(secret)
            self.assertEqual(project_for_cwd(secret, config), "기타 프로젝트")

    def test_허용_목록에_있으면_실명이다(self):
        config = BridgeConfig(allow=["meterengine"])
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "meterengine")
            os.makedirs(path)
            self.assertEqual(project_for_cwd(path, config), "meterengine")

    def test_허용_목록이_비면_전부_실명이다(self):
        config = BridgeConfig()
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "아무프로젝트")
            os.makedirs(path)
            self.assertEqual(project_for_cwd(path, config), "아무프로젝트")

    def test_deny는_None이라_보내지_않는다(self):
        config = BridgeConfig(deny=["비밀레포"])
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "비밀레포")
            os.makedirs(path)
            self.assertIsNone(project_for_cwd(path, config))

    def test_deny가_allow보다_먼저다(self):
        config = BridgeConfig(allow=["비밀레포"], deny=["비밀레포"])
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "비밀레포")
            os.makedirs(path)
            self.assertIsNone(project_for_cwd(path, config))

    def test_폴더가_비면_폴백이다(self):
        self.assertEqual(project_for_cwd("", BridgeConfig()), "기타 프로젝트")


class RepoNameTest(unittest.TestCase):
    def test_git이_아니면_None이다(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertIsNone(repo_name(directory))

    def test_없는_폴더면_None이다(self):
        self.assertIsNone(repo_name("/이런/폴더는/없다"))

    def test_워크트리도_본_레포_이름으로_모인다(self):
        """워크트리마다 다른 고객이 생기면 안 된다. MS2-169, MS2-157이 한 이름으로 모여야 한다."""
        with tempfile.TemporaryDirectory() as directory:
            main = os.path.join(directory, "메인레포")
            os.makedirs(main)
            if not _git_init(main):
                self.skipTest("git을 쓸 수 없습니다")
            worktree = os.path.join(directory, "워크트리들", "MS2-169")
            result = subprocess.run(
                ["git", "-C", main, "worktree", "add", "-b", "feat/x", worktree],
                capture_output=True,
                text=True,
            )
            if result.returncode != 0:
                self.skipTest("워크트리를 만들 수 없습니다: " + result.stderr.strip())
            self.assertEqual(repo_name(main), "메인레포")
            self.assertEqual(repo_name(worktree), "메인레포")
            # 하위 폴더에서도 같은 이름이어야 한다
            nested = os.path.join(worktree, "demo")
            os.makedirs(nested, exist_ok=True)
            self.assertEqual(repo_name(nested), "메인레포")


class BridgeStateTest(unittest.TestCase):
    def state(self, directory):
        return BridgeState(os.path.join(directory, "state.json"))

    def test_세션_매핑이_재시작을_넘어_남는다(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "state.json")
            BridgeState(path).remember_session("sess-1", "meterengine")
            self.assertEqual(BridgeState(path).project_of("sess-1"), "meterengine")

    def test_모르는_세션은_None이다(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertIsNone(self.state(directory).project_of("모름"))
            self.assertIsNone(self.state(directory).project_of(None))

    def test_deny한_세션은_매핑_없음과_구별된다(self):
        """구별하지 않으면 워커가 폴백으로 보내 deny가 무력해진다."""
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "state.json")
            state = BridgeState(path)
            state.deny_session("sess-비밀")
            self.assertTrue(state.is_denied("sess-비밀"))
            self.assertFalse(state.is_denied("sess-다른"))
            self.assertTrue(BridgeState(path).is_denied("sess-비밀"))

    def test_deny했다가_허용하면_풀린다(self):
        with tempfile.TemporaryDirectory() as directory:
            state = self.state(directory)
            state.deny_session("sess-1")
            state.remember_session("sess-1", "meterengine")
            self.assertFalse(state.is_denied("sess-1"))
            self.assertEqual(state.project_of("sess-1"), "meterengine")

    def test_고객_캐시가_남는다(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "state.json")
            BridgeState(path).remember_customer("meterengine(박성종)", DEMO_CUSTOMER)
            self.assertEqual(BridgeState(path).cached_customer("meterengine(박성종)"), DEMO_CUSTOMER)

    def test_UUID가_아닌_캐시는_읽지_않는다(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "state.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump({"customers": {"이상함": "UUID아님"}}, f)
            self.assertIsNone(BridgeState(path).cached_customer("이상함"))

    def test_깨진_상태_파일은_빈_상태로_시작한다(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "state.json")
            with open(path, "w", encoding="utf-8") as f:
                f.write("{잘림")
            self.assertEqual(BridgeState(path).sessions, {})


class CustomerResolverTest(unittest.TestCase):
    def resolver(self, directory, client):
        return CustomerResolver(client, BridgeState(os.path.join(directory, "state.json")))

    def test_이미_있으면_등록하지_않는다(self):
        """등록 API가 이름 중복을 막지 않으므로, 조회를 빠뜨리면 고객이 계속 늘어난다."""
        client = FakeClient([{"customer_id": DEMO_CUSTOMER, "name": "meterengine(박성종)"}])
        with tempfile.TemporaryDirectory() as directory:
            customer_id = self.resolver(directory, client).resolve("meterengine(박성종)")
        self.assertEqual(customer_id, DEMO_CUSTOMER)
        self.assertEqual(client.created, [])

    def test_없으면_등록한다(self):
        client = FakeClient()
        with tempfile.TemporaryDirectory() as directory:
            customer_id = self.resolver(directory, client).resolve("meterengine(박성종)")
        self.assertEqual(customer_id, DEMO_CUSTOMER)
        self.assertEqual(client.created, ["meterengine(박성종)"])

    def test_두_번째부터는_조회하지_않는다(self):
        client = FakeClient()
        with tempfile.TemporaryDirectory() as directory:
            resolver = self.resolver(directory, client)
            resolver.resolve("meterengine(박성종)")
            resolver.resolve("meterengine(박성종)")
        self.assertEqual(client.list_calls, 1)
        self.assertEqual(client.created, ["meterengine(박성종)"])

    def test_다른_이름은_따로_등록한다(self):
        client = FakeClient()
        with tempfile.TemporaryDirectory() as directory:
            resolver = self.resolver(directory, client)
            first = resolver.resolve("meterengine(박성종)")
            second = resolver.resolve("기타 프로젝트(박성종)")
        self.assertNotEqual(first, second)
        self.assertEqual(client.created, ["meterengine(박성종)", "기타 프로젝트(박성종)"])

    def test_조회가_실패하면_알린다(self):
        class Broken(FakeClient):
            def get_customers(self):
                return FakeResult(500, None)

        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(RuntimeError):
                self.resolver(directory, Broken()).resolve("아무개")

    def test_등록_응답에_customer_id가_없으면_알린다(self):
        class Broken(FakeClient):
            def create_customer(self, name):
                return FakeResult(201, {"name": name})

        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(RuntimeError):
                self.resolver(directory, Broken()).resolve("아무개")


def _git_init(path) -> bool:
    result = subprocess.run(
        ["git", "-C", path, "init", "-q"], capture_output=True, text=True
    )
    if result.returncode != 0:
        return False
    for arguments in (
        ["config", "user.email", "t@example.com"],
        ["config", "user.name", "t"],
        ["commit", "-q", "--allow-empty", "-m", "init"],
    ):
        if subprocess.run(["git", "-C", path] + arguments, capture_output=True).returncode != 0:
            return False
    return True


if __name__ == "__main__":
    unittest.main()
