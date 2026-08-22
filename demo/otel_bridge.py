#!/usr/bin/env python3
"""Claude Code 사용량을 팀 수집 API로 흘려보내는 로컬 브리지 (MS2-169).

Claude Code가 OTLP로 보낸 사용량 이벤트를 받아 POST /v1/events 형태로 바꿔 보낸다.
각자 기계에서 도는 상주 프로세스이고, 백엔드나 배포 구성은 건드리지 않는다.

    Claude Code --UserPromptSubmit hook--> 브리지: 이 세션은 이 폴더
                --OTLP/JSON--------------> 브리지: 토큰과 메타
                                              |
                                              v
                                      POST <base_url>/v1/events

이 파일은 명령줄 진입점이다. 화면을 보며 다루려면 console.py를 쓴다.
자세한 설명은 demo/README.md 참조.
"""

from __future__ import annotations

import sys

MINIMUM_PYTHON = (3, 9)

if sys.version_info < MINIMUM_PYTHON:
    sys.stderr.write("Python %d.%d 이상이 필요합니다 (현재 %d.%d).\n" % (MINIMUM_PYTHON + sys.version_info[:2]))
    sys.exit(2)

import argparse
import json
import os

from bridge import admin, server
from bridge.const import CLAUDE_SETTINGS_PATH, DEFAULT_HOST, DEFAULT_PORT, health_endpoint
from bridge.state import CONFIG_PATH, STATE_PATH, BridgeConfig, BridgeState


def run_serve(args) -> int:
    config = BridgeConfig.load(args.config)
    if args.base_url:
        config.base_url = args.base_url
    # 고객 캐시가 이 서버 것인지 판정할 수 있게 scope를 넘긴다.
    state = BridgeState(args.state, config.scope())
    return server.serve(config, state, args.host, args.port)


def run_config(args) -> int:
    """설정을 보거나 바꾼다."""
    path = args.config
    config = BridgeConfig.load(path)
    changed = False
    for field_name in ("owner", "base_url", "org_id", "fallback_project"):
        value = getattr(args, field_name, None)
        if value:
            setattr(config, field_name, value)
            changed = True
    if args.allow is not None:
        config.allow = _split(args.allow)
        changed = True
    if args.deny is not None:
        config.deny = _split(args.deny)
        changed = True
    if changed or not os.path.exists(path):
        config.save(path)
        print("설정을 저장했습니다: " + path)
    with open(path, encoding="utf-8") as f:
        print(json.dumps(json.load(f), ensure_ascii=False, indent=2))
    if _is_production(config.base_url):
        print()
        print("주의: 전송 대상이 배포 서버입니다. usage_event는 지울 수 없습니다.")
    if changed:
        print()
        print("이미 도는 브리지에는 반영되지 않습니다. 껐다 켜세요 (stop 후 start).")
    return 0


def run_setup(args) -> int:
    """~/.claude/settings.json에 OTel env와 hook을 병합한다."""
    plan = admin.plan_claude_settings(args.settings, args.host, args.port)
    if not plan.needed:
        print("이미 설정돼 있습니다: " + plan.path)
        return 0

    print("다음을 %s에 반영합니다." % plan.path)
    for change in plan.changes:
        print("  " + str(change))
    if not args.yes:
        print()
        try:
            answer = input("진행할까요? [y/N] ").strip().lower()
        except EOFError:
            answer = ""
        if answer not in ("y", "yes"):
            print("취소했습니다.")
            return 1

    backup = admin.apply_claude_settings(plan)
    if backup:
        print("기존 파일을 %s로 복사했습니다." % backup)
    print("반영했습니다. 새 Claude 세션부터 적용됩니다.")
    return 0


def run_install(args) -> int:
    path = admin.install(args.host, args.port, force=args.force)
    print("등록 파일을 썼습니다: " + path)
    print("브리지를 등록하고 시작했습니다. 로그인할 때마다 자동으로 뜹니다.")
    return 0


def run_uninstall(args) -> int:
    admin.uninstall()
    print("자동 시작을 해제했습니다.")
    return 0


def run_start(args) -> int:
    admin.start()
    print("시작했습니다.")
    return 0


def run_stop(args) -> int:
    admin.stop()
    print("멈췄습니다. 다시 켜려면 start를 실행하세요.")
    return 0


def run_status(args) -> int:
    body = admin.health(args.host, args.port)
    if body is None:
        print("브리지가 응답하지 않습니다 (%s)" % health_endpoint(args.host, args.port))
        print("이 상태에서 Claude는 정상 동작하고 사용량만 수집되지 않습니다.")
        return 1
    print(json.dumps(body, ensure_ascii=False, indent=2))
    return 0


def _split(text: str):
    return [x.strip() for x in text.split(",") if x.strip()]


def _is_production(base_url: str) -> bool:
    return base_url.rstrip("/").endswith("meterengine.com")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="otel_bridge",
        description="Claude Code의 OTel 사용량을 MeterEngine 수집 API로 보낸다.",
    )
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--host", default=DEFAULT_HOST, help="listen 주소 (기본 127.0.0.1)")
    common.add_argument("--port", type=int, default=DEFAULT_PORT, help="listen 포트 (기본 4318)")

    subparsers = parser.add_subparsers(dest="command", required=True)

    serve = subparsers.add_parser("serve", parents=[common], help="브리지를 포그라운드로 실행")
    serve.add_argument("--config", default=CONFIG_PATH, help="설정 파일 (기본 ~/.meterengine/bridge.json)")
    serve.add_argument("--state", default=STATE_PATH, help="상태 파일 (기본 ~/.meterengine/state.json)")
    serve.add_argument("--base-url", help="설정 파일의 base_url을 이번 실행에만 덮어쓴다")

    config = subparsers.add_parser("config", help="설정을 보거나 바꾼다")
    config.add_argument("--config", default=CONFIG_PATH)
    config.add_argument("--owner", help="이 기계의 주인. 고객 이름에 들어간다")
    config.add_argument("--base-url", help="전송 대상 (기본 http://localhost:8080)")
    config.add_argument("--org-id", help="X-Organization-Id")
    config.add_argument("--allow", help="실명으로 보낼 레포 이름들, 쉼표 구분. 비우면 전부 실명")
    config.add_argument("--deny", help="아예 보내지 않을 레포 이름들, 쉼표 구분")
    config.add_argument("--fallback-project", help="허용 목록 밖 프로젝트를 합칠 이름")

    setup = subparsers.add_parser(
        "setup", parents=[common], help="~/.claude/settings.json에 OTel과 hook을 병합"
    )
    setup.add_argument("--settings", default=CLAUDE_SETTINGS_PATH)
    setup.add_argument("--yes", action="store_true", help="확인 없이 진행")

    install = subparsers.add_parser(
        "install", parents=[common], help="launchd에 등록해 로그인할 때 자동 시작"
    )
    install.add_argument("--force", action="store_true", help="워크트리에서도 등록한다")

    subparsers.add_parser("uninstall", parents=[common], help="자동 시작 해제")
    subparsers.add_parser("start", parents=[common], help="브리지 켜기")
    subparsers.add_parser("stop", parents=[common], help="브리지 끄기")
    subparsers.add_parser("status", parents=[common], help="브리지 상태와 누적 건수")
    return parser


HANDLERS = {
    "serve": run_serve,
    "config": run_config,
    "setup": run_setup,
    "install": run_install,
    "uninstall": run_uninstall,
    "start": run_start,
    "stop": run_stop,
    "status": run_status,
}


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    try:
        return HANDLERS[args.command](args)
    except admin.AdminError as error:
        print(str(error), file=sys.stderr)
        return 2
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print()
        return 130


if __name__ == "__main__":
    sys.exit(main())
