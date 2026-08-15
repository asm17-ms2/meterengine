"""백엔드 HTTP API의 얇은 래퍼. 외부 API 호출은 이 모듈만 거친다.

응답 JSON의 소수는 Decimal로 읽는다 (quantity, unit_price 정밀도 보존).
"""

from __future__ import annotations

import http.client
import socket
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import List, Optional

from model import loads_decimal


class TransportError(Exception):
    """서버까지 도달하지 못한 실패. 연결 거부와 타임아웃을 메시지로 구분한다."""


@dataclass
class ApiResult:
    status: int
    body: Optional[dict]
    body_text: str
    elapsed_ms: int


@dataclass
class Problem:
    status: int
    code: Optional[str]
    title: Optional[str]
    detail: Optional[str]
    errors: List[dict] = field(default_factory=list)

    def summary(self) -> str:
        parts = []
        if self.code:
            parts.append(self.code)
        if self.detail:
            parts.append(self.detail)
        elif self.title:
            parts.append(self.title)
        for error in self.errors:
            parts.append("%s: %s" % (error.get("field"), error.get("message")))
        return " / ".join(parts) if parts else "HTTP %d" % self.status


def parse_problem(status: int, body: Optional[dict]) -> Problem:
    """problem+json을 파싱한다. code는 /v1/events에만 있으므로 없을 수 있다."""
    body = body or {}
    return Problem(
        status=status,
        code=body.get("code"),
        title=body.get("title"),
        detail=body.get("detail"),
        errors=body.get("errors") or [],
    )


class ApiClient:
    def __init__(self, base_url: str, org_id: str, timeout_seconds: float = 10.0):
        self.base_url = base_url.rstrip("/")
        self.org_id = org_id
        self.timeout_seconds = timeout_seconds

    def post_event(self, body_text: str) -> ApiResult:
        return self._request(
            "POST",
            "/v1/events",
            body=body_text.encode("utf-8"),
            content_type="application/json",
        )

    def get_usage(self, month: Optional[str]) -> ApiResult:
        return self._request("GET", "/v1/usage", query=_month_query(month))

    def get_invoice(self, month: Optional[str]) -> ApiResult:
        return self._request("GET", "/v1/invoice", query=_month_query(month))

    def get_events_page(self, month: Optional[str]) -> ApiResult:
        """total(월 필터 적용 건수)만 필요하므로 가장 작은 페이지를 받는다."""
        query = _month_query(month)
        query.update({"page": "0", "size": "1"})
        return self._request("GET", "/v1/events", query=query)

    def _request(self, method, path, query=None, body=None, content_type=None) -> ApiResult:
        url = self.base_url + path
        if query:
            url += "?" + urllib.parse.urlencode(query)
        request = urllib.request.Request(url, data=body, method=method)
        request.add_header("X-Organization-Id", self.org_id)
        request.add_header("Accept", "application/json, application/problem+json")
        if content_type:
            request.add_header("Content-Type", content_type)
        started = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                return self._to_result(response.status, response.read(), started)
        except urllib.error.HTTPError as error:
            # 4xx/5xx도 서버가 준 응답이다. 바디를 읽어 그대로 돌려준다.
            return self._to_result(error.code, error.read(), started)
        except urllib.error.URLError as error:
            raise TransportError(self._transport_message(error.reason)) from error
        except socket.timeout as error:
            raise TransportError(self._timeout_message()) from error
        except (http.client.HTTPException, OSError) as error:
            # 응답 수신 중 연결이 끊기면(RemoteDisconnected 등) URLError로 감싸지지 않는다
            raise TransportError(
                "%s 응답을 받는 중 연결이 끊겼습니다: %s" % (self.base_url, error)
            ) from error

    def _to_result(self, status: int, raw: bytes, started: float) -> ApiResult:
        elapsed_ms = int((time.monotonic() - started) * 1000)
        body_text = raw.decode("utf-8", errors="replace")
        try:
            body = loads_decimal(body_text) if body_text.strip() else None
        except ValueError:
            body = None
        if body is not None and not isinstance(body, dict):
            body = None
        return ApiResult(status=status, body=body, body_text=body_text, elapsed_ms=elapsed_ms)

    def _transport_message(self, reason) -> str:
        if isinstance(reason, ConnectionRefusedError):
            return (
                "%s에 연결할 수 없습니다 (연결 거부). 백엔드가 떠 있는지 확인하세요: "
                "IntelliJ에서 MeterEngineApplication 실행 또는 backend/에서 ./gradlew bootRun"
                % self.base_url
            )
        if isinstance(reason, socket.timeout):
            return self._timeout_message()
        return "%s 요청에 실패했습니다: %s" % (self.base_url, reason)

    def _timeout_message(self) -> str:
        return (
            "%s 요청이 %.0f초 안에 응답하지 않았습니다 (타임아웃). "
            "전송 요청이었다면 서버에 저장됐을 수도 있습니다" % (self.base_url, self.timeout_seconds)
        )


def _month_query(month: Optional[str]) -> dict:
    return {"month": month} if month else {}


def roster_from_usage(usage_body: Optional[dict]) -> dict:
    """/v1/usage 응답에서 등록 고객 명단(customer_id -> customer_name)을 뽑는다.

    응답은 등록 고객 전원을 0 채움으로 포함하므로 이것이 전체 명단이다.
    """
    roster = {}
    for metric in (usage_body or {}).get("metrics") or []:
        for customer in metric.get("customers") or []:
            roster[customer["customer_id"]] = customer["customer_name"]
    return roster
