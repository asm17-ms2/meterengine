#!/usr/bin/env python3
# /// script
# requires-python = ">=3.9"
# dependencies = ["textual>=1.0"]
# ///
"""브리지를 화면으로 다루는 콘솔 (MS2-169).

    uv run demo/console.py

상태를 2초마다 갱신하고, 설정을 고치고, 브리지를 켜고 끈다. 같은 일을 명령줄로
하려면 demo/otel_bridge.py를 쓴다. 두 진입점이 bridge/admin.py를 함께 쓰므로
동작이 갈라지지 않는다.

uv가 필요한 이유는 이 파일 하나뿐이다. 위의 script 블록(PEP 723)에 의존성이
적혀 있어 uv가 알아서 격리 환경에 받아 실행한다. demo의 나머지는 그대로
python3로 돈다.
"""

from __future__ import annotations

import sys
from dataclasses import replace

from textual import on, work
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Horizontal, Vertical
from textual.widgets import Button, DataTable, Footer, Header, Input, Label, Static

from bridge import admin
from bridge.const import DEFAULT_HOST, DEFAULT_PORT
from bridge.state import (
    CONFIG_PATH,
    MERGED,
    NAMED,
    PROJECT_STATES,
    SKIPPED,
    STATE_PATH,
    BridgeConfig,
    BridgeState,
)


class ConsoleApp(App):
    """브리지 상태판 겸 설정판."""

    TITLE = "MeterEngine 사용량 브리지"

    CSS = """
    Screen { layout: vertical; }
    #status { height: auto; padding: 1 2; background: $panel; }
    #status.running { border-left: thick $success; }
    #status.stopped { border-left: thick $error; }
    .section { padding: 1 2; height: auto; }
    .section-title { text-style: bold; margin-bottom: 1; }
    .row { height: 3; }
    .row Label { width: 12; content-align: left middle; height: 3; }
    .row Input { width: 1fr; }
    #projects { height: 10; margin-top: 1; }
    #rule { color: $text-muted; margin-top: 1; }
    #add-row Button { width: 10; }
    """

    BINDINGS = [
        Binding("s", "start", "시작"),
        Binding("x", "stop", "중지"),
        Binding("w", "save", "설정 저장"),
        Binding("space", "cycle", "상태 전환"),
        Binding("k", "setup", "Claude 설정"),
        Binding("i", "install", "자동시작 등록"),
        Binding("u", "uninstall", "등록 해제"),
        Binding("q", "quit", "종료"),
    ]

    def __init__(self, config_path: str = CONFIG_PATH, state_path: str = STATE_PATH):
        super().__init__()
        self.config_path = config_path
        self.state_path = state_path
        self.config = BridgeConfig.load(config_path)
        self.health: dict = {}
        self.reachable = False

    # ------------------------------------------------------------ 구성

    def compose(self) -> ComposeResult:
        yield Header()
        yield Static("상태를 읽는 중...", id="status")
        with Vertical(classes="section"):
            yield Label("설정", classes="section-title")
            with Horizontal(classes="row"):
                yield Label("주인")
                yield Input(self.config.owner, placeholder="고객 이름에 들어간다", id="owner")
            with Horizontal(classes="row"):
                yield Label("전송 대상")
                yield Input(self.config.base_url, id="base_url")
            with Horizontal(classes="row"):
                yield Label("폴백 이름")
                yield Input(self.config.fallback_project, id="fallback")
            yield Static("", id="rule")
            yield DataTable(id="projects", cursor_type="row", zebra_stripes=True)
            with Horizontal(classes="row", id="add-row"):
                yield Label("추가")
                yield Input(placeholder="레포 이름을 적고 Enter", id="new-project")
                yield Button("추가", id="add", variant="primary")
        yield Footer()

    def on_mount(self) -> None:
        table = self.query_one("#projects", DataTable)
        table.add_column("프로젝트", key="project", width=32)
        table.add_column("상태", key="state", width=24)
        self._fill_projects()
        self._refresh_rule()
        self.refresh_status()
        self.set_interval(2.0, self.refresh_status)

    # ------------------------------------------------------------ 상태

    @work(thread=True, exclusive=True)
    def refresh_status(self) -> None:
        """브리지에 물어본다. 응답이 늦어도 화면이 멈추지 않게 스레드에서 돈다."""
        body = admin.health(DEFAULT_HOST, DEFAULT_PORT, timeout=1.0)
        self.call_from_thread(self._apply_status, body)

    def _apply_status(self, body) -> None:
        self.reachable = body is not None
        self.health = body or {}
        self._merge_projects()
        panel = self.query_one("#status", Static)
        panel.set_class(self.reachable, "running")
        panel.set_class(not self.reachable, "stopped")
        panel.update(self._status_text())

    def _status_text(self) -> str:
        if not self.reachable:
            registered = "등록됨" if admin.installed() else "등록 안 됨"
            return (
                "● 꺼짐   자동 시작: %s\n"
                "이 상태에서 Claude는 정상 동작하고 사용량만 수집되지 않습니다. s를 눌러 시작합니다."
                % registered
            )
        counts = self.health.get("counts", {})
        projects = self.health.get("projects", {})
        lines = [
            "● 실행 중   %s   주인 %s"
            % (self.health.get("base_url", "?"), self.health.get("owner") or "(없음)"),
            "전송  new %s / 중복 %s / 거절 %s / 실패 %s / 건너뜀 %s"
            % tuple(counts.get(k, 0) for k in ("new", "duplicate", "rejected", "error", "skipped")),
        ]
        if projects:
            lines.append(
                "세션  " + " / ".join("%s %d" % (name, n) for name, n in sorted(projects.items()))
            )
        customers = self.health.get("customers") or []
        if customers:
            lines.append("고객  " + ", ".join(customers))
        return "\n".join(lines)

    # ------------------------------------------------------------ 프로젝트 목록

    def _known_projects(self) -> list:
        """설정에 적힌 것과 브리지가 실제로 본 것을 합친다.

        관측된 것을 함께 보여 주는 이유는, 무엇을 실명으로 할지 고르려면 내가 어떤
        레포에서 일했는지가 먼저 보여야 하기 때문이다.
        """
        names = set(self.config.allow) | set(self.config.deny)
        try:
            names |= set(BridgeState(self.state_path, self.config.scope()).sessions.values())
        except OSError:
            pass
        names.discard(self.config.fallback_project)
        return sorted(names)

    def _fill_projects(self) -> None:
        table = self.query_one("#projects", DataTable)
        table.clear()
        for name in self._known_projects():
            table.add_row(name, self.config.project_state(name), key=name)

    def _merge_projects(self) -> None:
        """브리지가 새로 본 프로젝트를 목록에 덧붙인다.

        기동 때 한 번만 채우면 콘솔을 켜 둔 사이에 처음 본 레포가 목록에 없다.
        무엇을 실명으로 할지 고르려면 그것이 보여야 한다는 것이 이 화면의 요지다.

        통째로 다시 채우지 않는 이유는 table.clear()가 저장하지 않은 상태 변경
        (스페이스로 돌려 둔 값)을 지우기 때문이다. 새 이름만 붙인다. state.json을
        다시 읽지 않고 health의 projects를 쓰는 것은 어차피 2초마다 받는 값이고,
        브리지가 도는 동안에는 그쪽이 더 최신이기 때문이다.
        """
        projects = self.health.get("projects") or {}
        if not projects:
            return
        table = self.query_one("#projects", DataTable)
        existing = set(self._rows())
        added = False
        for name in sorted(projects):
            if name in existing or name == self.config.fallback_project:
                continue
            table.add_row(name, self.config.project_state(name), key=name)
            added = True
        if added:
            self._refresh_rule()

    def _rows(self) -> dict:
        table = self.query_one("#projects", DataTable)
        result = {}
        for key in table.rows:
            row = table.get_row(key)
            result[str(row[0])] = str(row[1])
        return result

    def _refresh_rule(self) -> None:
        named = [n for n, s in self._rows().items() if s == NAMED]
        if named:
            text = "목록의 %d개만 실명으로 가고, 나머지는 '%s'로 합쳐집니다." % (
                len(named), self.query_one("#fallback", Input).value or self.config.fallback_project
            )
        else:
            text = "실명이 하나도 없어 [b]모든 프로젝트가 실명으로[/b] 갑니다 (허용 목록이 비면 그렇게 동작합니다)."
        self.query_one("#rule", Static).update(text)

    def action_cycle(self) -> None:
        """커서가 놓인 프로젝트의 상태를 바꾼다."""
        table = self.query_one("#projects", DataTable)
        if table.cursor_row is None or not table.rows:
            return
        row_key, _ = table.coordinate_to_cell_key(table.cursor_coordinate)
        current = str(table.get_row(row_key)[1])
        following = (PROJECT_STATES[(PROJECT_STATES.index(current) + 1) % len(PROJECT_STATES)]
                     if current in PROJECT_STATES else NAMED)
        table.update_cell(row_key, "state", following)
        self._refresh_rule()

    @on(Button.Pressed, "#add")
    @on(Input.Submitted, "#new-project")
    def add_project(self) -> None:
        field = self.query_one("#new-project", Input)
        name = field.value.strip()
        if not name:
            return
        if name in self._rows():
            self.notify("이미 목록에 있습니다: " + name, severity="warning")
            return
        self.query_one("#projects", DataTable).add_row(name, NAMED, key=name)
        field.clear()
        self._refresh_rule()

    @on(Input.Changed, "#fallback")
    def fallback_changed(self) -> None:
        self._refresh_rule()

    # ------------------------------------------------------------ 동작

    def action_save(self) -> None:
        """화면의 값을 설정 파일에 쓴다.

        먼저 사본에 담아 저장이 끝난 뒤에 self.config로 올린다. save는 validate를
        거치므로 스킴 없는 base_url 같은 값에 ValueError를 던지는데, 원본에 바로
        쓰면 실패한 값이 메모리에 남아 화면과 어긋난다.
        """
        candidate = replace(
            self.config,
            owner=self.query_one("#owner", Input).value.strip(),
            base_url=self.query_one("#base_url", Input).value.strip(),
            fallback_project=(
                self.query_one("#fallback", Input).value.strip() or self.config.fallback_project
            ),
            allow=list(self.config.allow),
            deny=list(self.config.deny),
        )
        candidate.set_project_states(self._rows())
        try:
            candidate.save(self.config_path)
        except (OSError, ValueError) as error:
            self.notify("저장하지 못했습니다: %s" % error, severity="error", timeout=8)
            return
        self.config = candidate

        if self.config.base_url.rstrip("/").endswith("meterengine.com"):
            self.notify(
                "전송 대상이 배포 서버입니다. 보낸 이벤트는 지울 수 없습니다.", severity="warning", timeout=8
            )
        # base_url 같은 값은 기동 때 한 번만 읽는다. 저장만 하면 도는 브리지는 옛 값을 쓴다.
        if self.reachable:
            try:
                admin.restart()
                self.notify("저장하고 브리지를 재시작했습니다.")
            except admin.AdminError:
                self.notify("저장했습니다. 도는 브리지에 반영하려면 껐다 켜세요.", severity="warning")
        else:
            self.notify("저장했습니다.")

    def action_start(self) -> None:
        self._run(admin.start, "시작했습니다.")

    def action_stop(self) -> None:
        self._run(admin.stop, "멈췄습니다.")

    def action_install(self) -> None:
        def do():
            path = admin.install(DEFAULT_HOST, DEFAULT_PORT)
            return "등록했습니다: " + path

        self._run(do, None)

    def action_uninstall(self) -> None:
        self._run(admin.uninstall, "자동 시작을 해제했습니다.")

    def action_setup(self) -> None:
        """~/.claude/settings.json에 OTel과 hook을 병합한다."""
        try:
            plan = admin.plan_claude_settings(host=DEFAULT_HOST, port=DEFAULT_PORT)
        except admin.AdminError as error:
            self.notify(str(error), severity="error", timeout=10)
            return
        if not plan.needed:
            self.notify("Claude 설정은 이미 돼 있습니다.")
            return
        admin.apply_claude_settings(plan)
        self.notify(
            "Claude 설정에 %d개를 반영했습니다. 새 세션부터 적용됩니다." % len(plan.changes), timeout=8
        )

    def _run(self, action, message) -> None:
        try:
            result = action()
        except admin.AdminError as error:
            self.notify(str(error), severity="error", timeout=10)
            return
        self.notify(message or result or "완료했습니다.")
        self.refresh_status()


def main(argv=None) -> int:
    import argparse

    parser = argparse.ArgumentParser(prog="console", description="브리지를 화면으로 다룬다.")
    parser.add_argument("--config", default=CONFIG_PATH)
    parser.add_argument("--state", default=STATE_PATH)
    args = parser.parse_args(argv)
    try:
        ConsoleApp(args.config, args.state).run()
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
