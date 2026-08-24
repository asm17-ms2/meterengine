#!/usr/bin/env python3
"""MeterEngine 데모/검증 CLI (MS2-128).

이벤트를 실제 HTTP로 백엔드에 흘려보내고(send), 요청과 응답을 1:1로 대조하며,
소스 기반 기대값과 서버 집계/예정액이 일치하는지 사람이 확인한다(verify).
자세한 사용법은 demo/README.md 참조.
"""

import sys

MINIMUM_PYTHON = (3, 9)

if sys.version_info < MINIMUM_PYTHON:
    sys.stderr.write("Python %d.%d 이상이 필요합니다 (현재 %d.%d).\n" % (MINIMUM_PYTHON + sys.version_info[:2]))
    sys.exit(2)

import argparse

from core.api_client import TransportError
from csvdemo.render import Console, color_enabled
from csvdemo import send_cmd
from csvdemo import verify_cmd


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="meterdemo",
        description="MeterEngine 수집-조회 데모/검증 툴. 표준 라이브러리만 사용한다.",
    )
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--base-url", help="백엔드 주소 (기본 http://localhost:8080, verify는 로그 헤더 값 우선)")
    common.add_argument("--org-id", help="X-Organization-Id (기본: 시드 도입사)")
    common.add_argument("--no-color", action="store_true", help="ANSI 색 비활성화")
    common.add_argument("--timeout", type=float, default=10.0, help="HTTP 타임아웃 초 (기본 10)")

    subparsers = parser.add_subparsers(dest="command", required=True)

    send = subparsers.add_parser("send", parents=[common], help="CSV의 이벤트를 순차 전송하고 요청:응답을 대조 출력")
    send.add_argument("--csv", required=True, help="이벤트 CSV 경로 (스키마는 MS2-142 확정 대기)")
    send.add_argument("--interval", type=float, default=0.0, help="전송 간격 초 (기본 0)")
    send.add_argument("--jitter", type=float, default=0.0, help="간격에 더할 랜덤 슬립 상한 초 (기본 0)")
    send.add_argument("--yes", action="store_true", help="전송 전 확인 게이트 생략")
    send.add_argument("--dry-run", action="store_true", help="전송 없이 게이트와 미리보기만")
    send.add_argument("--verbose", action="store_true", help="요청/응답 JSON 전문도 출력")

    verify = subparsers.add_parser("verify", parents=[common], help="소스 기반 기대값과 서버 응답을 표로 비교")
    verify.add_argument("--log", help="send가 남긴 JSONL 로그 (실제 전송분이므로 CSV보다 우선)")
    verify.add_argument("--csv", help="원본 데이터 CSV (스키마는 MS2-142 확정 대기)")
    verify.add_argument("--month", help="검증할 월 yyyy-MM (기본: 소스에 있는 모든 월)")

    convert = subparsers.add_parser("convert", parents=[common], help="LLM 사용 로그를 팀 이벤트 CSV로 변환 (준비 중)")
    convert.set_defaults(command="convert")
    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    console = Console(color_enabled(args.no_color))
    try:
        if args.command == "send":
            return send_cmd.run_send(args, console)
        if args.command == "verify":
            return verify_cmd.run_verify(args, console)
        print(
            "convert는 준비 중입니다. LLM 제공사별 사용 로그 형식 조사와 매핑 승인(MS2-128) 후 구현됩니다.",
            file=sys.stderr,
        )
        return 2
    except TransportError as error:
        print(str(error), file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print()
        return 130
    except BrokenPipeError:
        return 0
    except Exception:
        # 미처리 예외의 기본 종료 코드 1은 "불일치"와 충돌한다. 오류는 항상 2로 끝낸다.
        import traceback

        traceback.print_exc()
        print("예상하지 못한 오류입니다. 위 내용과 함께 알려주세요.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
