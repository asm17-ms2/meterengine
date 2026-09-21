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
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.config_path = os.path.join(self.directory.name, "bridge.json")
        self.state_path = os.path.join(self.directory.name, "state.json")
        self.addCleanup(self.directory.cleanup)

    def _save_with(self, base_url):
        from textual.widgets import Input

        import console as console_module
        from bridge import admin

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
        self.assertEqual(app.config.base_url, "http://localhost:8080")
        self.assertFalse(os.path.exists(self.config_path))

    def test_올바른_값은_저장된다(self):
        app = self._save_with("https://meterengine.com")
        self.assertEqual(app.config.base_url, "https://meterengine.com")
        with open(self.config_path, encoding="utf-8") as f:
            self.assertEqual(json.load(f)["base_url"], "https://meterengine.com")


@unittest.skipUnless(HAS_TEXTUAL, "textual이 없다 (uv run --with textual)")
class AdminActionTest(unittest.TestCase):
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

        self.patch(admin, "health", lambda *args, **kwargs: None)
        return console_module.ConsoleApp(self.config_path, self.state_path)

    def run_scenario(self, app, body):
        async def scenario():
            async with app.run_test() as pilot:
                body(pilot)
                await pilot.pause()
                self.assertTrue(app.is_running)

        asyncio.run(scenario())

    def test_Claude_설정을_쓰지_못해도_앱이_살아_있다(self):
        from bridge import admin

        def explode(plan):
            raise OSError(13, "Permission denied")

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
