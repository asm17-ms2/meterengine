from __future__ import annotations

import http.client
import json
import socket
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import List, Optional

from core.model import loads_decimal


class TransportError(Exception):
    pass


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
    message: Optional[str]
    errors: List[dict] = field(default_factory=list)

    def summary(self) -> str:
        parts = []
        if self.code:
            parts.append(self.code)
        if self.message:
            parts.append(self.message)
        for error in self.errors:
            parts.append("%s: %s" % (error.get("field"), error.get("message")))
        return " / ".join(parts) if parts else "HTTP %d" % self.status


def parse_problem(status: int, body: Optional[dict]) -> Problem:
    body = body or {}
    return Problem(
        status=status,
        code=body.get("code"),
        message=body.get("message"),
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
        return self._request("GET", "/v1/invoices/draft", query=_month_query(month))

    def get_events_page(self, month: Optional[str]) -> ApiResult:
        query = _month_query(month)
        query.update({"page": "0", "size": "1"})
        return self._request("GET", "/v1/events", query=query)

    def get_customers(self) -> ApiResult:
        return self._request("GET", "/v1/customers")

    def create_customer(self, name: str) -> ApiResult:
        body = json.dumps({"name": name}, ensure_ascii=False).encode("utf-8")
        return self._request("POST", "/v1/customers", body=body, content_type="application/json")

    def _request(self, method, path, query=None, body=None, content_type=None) -> ApiResult:
        url = self.base_url + path
        if query:
            url += "?" + urllib.parse.urlencode(query)
        request = urllib.request.Request(url, data=body, method=method)
        request.add_header("X-Organization-Id", self.org_id)
        request.add_header("Accept", "application/json")
        if content_type:
            request.add_header("Content-Type", content_type)
        started = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                return self._to_result(response.status, response.read(), started)
        except urllib.error.HTTPError as error:
            return self._to_result(error.code, error.read(), started)
        except urllib.error.URLError as error:
            raise TransportError(self._transport_message(error.reason)) from error
        except socket.timeout as error:
            raise TransportError(self._timeout_message()) from error
        except (http.client.HTTPException, OSError) as error:
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
    roster = {}
    for billable_metric in (usage_body or {}).get("billable_metric_usages") or []:
        for customer in billable_metric.get("customers") or []:
            roster[customer["customer_id"]] = customer["customer_name"]
    return roster
