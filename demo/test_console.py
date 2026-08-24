"""콘솔 테스트 (MS2-169).

textual이 있어야 돌아간다. demo의 나머지는 표준 라이브러리만 쓰므로, 없으면
건너뛴다. 콘솔만 uv로 실행하는 구조 그대로다 (console.py의 PEP 723 블록).

    uv run --with textual python3 -m unittest test_console
"""

import asyncio
import importlib.util
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

HAS_TEXTUAL = importlib.util.find_spec("textual") is not None


@unittest.skipUnless(HAS_TEXTUAL, "textual이 없다 (uv run --with textual)")
class SaveTest(unittest.TestCase):
    """저장 실패가 앱을 죽이면 안 된다.

    save는 validate를 거치므로 스킴 없는 base_url에 ValueError를 던진다. 그것을
    잡지 않으면 예외가 액션 핸들러를 빠져나가 앱이 트레이스백과 함께 내려간다.
    """

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.config_path = os.path.join(self.directory.name, "bridge.json")
        self.state_path = os.path.join(self.directory.name, "state.json")
        self.addCleanup(self.directory.cleanup)

    def _save_with(self, base_url):
        from textual.widgets import Input

        import console as console_module
        from bridge import admin

        # 기동 직후 health를 물어보러 나가지 않게 막는다. 네트워크를 쓰지 않는다.
        original = admin.health
        admin.health = lambda *args, **kwargs: None
        self.addCleanup(lambda: setattr(admin, "health", original))

        app = console_module.ConsoleApp(self.config_path, self.state_path)

        async def scenario():
            async with app.run_test() as pilot:
                app.query_one("#base_url", Input).value = base_url
                app.action_save()
                await pilot.pause()

        asyncio.run(scenario())
        return app

    def test_스킴_없는_전송_대상을_저장해도_앱이_살아_있다(self):
        app = self._save_with("localhost:8080")
        # 잘못된 값이 메모리에 남으면 화면과 어긋난다. 사본에만 담았어야 한다.
        self.assertEqual(app.config.base_url, "http://localhost:8080")
        self.assertFalse(os.path.exists(self.config_path))

    def test_올바른_값은_저장된다(self):
        app = self._save_with("https://meterengine.com")
        self.assertEqual(app.config.base_url, "https://meterengine.com")
        with open(self.config_path, encoding="utf-8") as f:
            self.assertEqual(json.load(f)["base_url"], "https://meterengine.com")


@unittest.skipUnless(HAS_TEXTUAL, "textual이 없다 (uv run --with textual)")
class AdminActionTest(unittest.TestCase):
    """브리지를 켜고 끄는 일이 앱을 죽이거나 멈추게 하면 안 된다."""

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.config_path = os.path.join(self.directory.name, "bridge.json")
        self.state_path = os.path.join(self.directory.name, "state.json")

    def patch(self, module, name, value):
        original = getattr(module, name)
        self.addCleanup(setattr, module, name, original)
        setattr(module, name, value)

    def app(self):
        import console as console_module
        from bridge import admin

        # 기동 직후 health를 물어보러 나가지 않게 막는다. 네트워크를 쓰지 않는다.
        self.patch(admin, "health", lambda *args, **kwargs: None)
        return console_module.ConsoleApp(self.config_path, self.state_path)

    def run_scenario(self, app, body):
        async def scenario():
            async with app.run_test() as pilot:
                body(pilot)
                await pilot.pause()
                # 여기까지 왔으면 액션이 앱을 내리지 않았다는 뜻이다
                self.assertTrue(app.is_running)

        asyncio.run(scenario())

    def test_Claude_설정을_쓰지_못해도_앱이_살아_있다(self):
        """읽기 전용 홈이나 가득 찬 디스크에서 나는 OSError.

        잡지 않으면 예외가 액션 핸들러를 빠져나가 앱이 트레이스백과 함께 내려간다.
        저장 실패를 막아 둔 것과 같은 이유다.
        """
        from bridge import admin

        def explode(plan):
            raise OSError(13, "Permission denied")

        # 진짜 ~/.claude/settings.json을 읽지 않게 계획도 대신 만든다.
        plan = admin.SettingsPlan(
            path=os.path.join(self.directory.name, "settings.json"),
            settings={},
            changes=[admin.Change("env.X", None, "1")],
        )
        self.patch(admin, "plan_claude_settings", lambda **kwargs: plan)
        self.patch(admin, "apply_claude_settings", explode)
        app = self.app()
        self.run_scenario(app, lambda pilot: app.action_setup())

    def test_launchctl은_이벤트_루프_밖에서_돈다(self):
        """launchctl 한 번이 최대 15초를 잡는다. 그동안 화면이 멈추면 안 된다."""
        import threading

        from bridge import admin

        called = {}

        def slow_start():
            called["thread"] = threading.current_thread().name

        self.patch(admin, "start", slow_start)
        app = self.app()

        async def scenario():
            async with app.run_test() as pilot:
                app.action_start()
                for _ in range(200):
                    if "thread" in called:
                        break
                    await asyncio.sleep(0.01)
                await pilot.pause()

        asyncio.run(scenario())
        self.assertIn("thread", called)
        self.assertNotEqual(called["thread"], threading.current_thread().name)


if __name__ == "__main__":
    unittest.main()
