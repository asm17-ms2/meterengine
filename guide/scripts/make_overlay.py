"""backend/openapi.yaml에서 operation 위치를 읽어 Overlay 1.0 문서를 만든다.

실행: python3 -I guide/scripts/make_overlay.py
- 숨김 목록의 operationId가 스펙에 없으면 실패한다.
- 결과는 guide/openapi/overlays/meterengine.yaml.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SPEC = ROOT / "backend" / "openapi.yaml"
OUT = ROOT / "guide" / "openapi" / "overlays" / "meterengine.yaml"
SERVER = "https://meterengine.com"

# 가이드가 링크하지 않는 경로. 페이지를 만들지 않는다.
EXCLUDED = {
    "운영자 전용": [
        "getOperations", "listOperationsAuditLogs", "listOrganizations",
        "updateOrganizationStatus", "createUserAccount", "listUserAccounts", "updateUserAccountRole",
        "listLiveEnvironmentApplications", "updateLiveEnvironmentApplicationStatus",
    ],
    "콘솔 세션": ["getSession", "deleteSession", "updateSessionOrganization", "acceptSessionConsents"],
    "가입, 로그인, 초대": [
        "createSignup", "createEmailVerification", "confirmEmailVerification", "createSession",
        "previewOrganizationInvitation", "acceptOrganizationInvitation",
    ],
    "MeterEngine 화면용 공개 경로": ["getPaymentMethodRegistration", "createBillingKey", "getPublicInvoice", "createInvoicePaymentRetry"],
    "화면 보조 options": ["suggestCustomers", "suggestBillableMetrics"],
}
# 콘솔에서 쓰지만 API 키로 부를 수 없는 경로. 페이지는 만들고 탐색에서 숨긴다.
HIDDEN = {
    "세션 전용 관리(관리자)": [
        "createApiKey", "deleteApiKey", "updateApiKeyScopes",
        "createManagedOrganizationMember", "inviteOrganizationUser", "listOrganizationInvitations",
        "removeOrganizationMember", "revokeOrganizationInvitation", "updateOrganizationMemberRole",
        "updateOrganizationMemberScopes",
        "updatePaymentSetting", "updateTossPaymentsCredential", "deleteTossPaymentsCredential",
        "createLiveEnvironmentApplication",
    ],
    "세션 전용 관리(조회)": [
        "listApiKeys", "listOrganizationAuditLogs", "listOrganizationMembers",
        "listOrganizationLiveEnvironmentApplications",
    ],
    "토스페이먼츠 웹훅 수신": ["receiveTossPaymentsWebhook"],
}
# 태그 표시 이름. 나열 순서가 최상위 tags 순서다. 모든 operation이 제외된 태그는 넣지 않는다.
TAG_NAMES = [
    ("organization-environment-controller", "업무 환경"),
    ("current-organization-controller", "조직 정보"),
    ("live-environment-application-controller", "실제 환경 이용 신청 (콘솔 전용)"),
    ("api-key-controller", "API 키 (콘솔 전용)"),
    ("organization-people-controller", "구성원과 초대 (콘솔 전용)"),
    ("audit-log-controller", "감사 기록 (콘솔 전용)"),
    ("billable-metric-controller", "미터"),
    ("billable-metric-price-controller", "미터별 단가 조회"),
    ("price-rate-controller", "단가"),
    ("price-plan-controller", "요금제"),
    ("customer-controller", "고객"),
    ("event-controller", "이벤트"),
    ("event-batch-controller", "이벤트 일괄 전송"),
    ("event-export-controller", "이벤트 내보내기"),
    ("billable-metric-usage-controller", "사용량 집계"),
    ("event-issue-controller", "이벤트 매핑 진단"),
    ("event-rejection-controller", "수집 거절"),
    ("unbilled-event-controller", "청구 제외 사용량"),
    ("dashboard-controller", "개요"),
    ("workspace-query-controller", "검색과 내보내기"),
    ("draft-invoice-controller", "청구 예정액"),
    ("invoice-controller", "인보이스"),
    ("invoice-view-link-controller", "인보이스 조회 링크"),
    ("payment-setting-controller", "결제 설정"),
    ("toss-payments-credential-controller", "토스페이먼츠 키"),
    ("payment-method-registration-controller", "카드 등록 링크"),
    ("payment-method-controller", "결제 수단"),
    ("payment-attempt-controller", "결제 요청"),
    ("invoice-payment-controller", "수납 상태"),
    ("payment-cancellation-controller", "결제 취소"),
    ("payment-reconciliation-mismatch-controller", "대사 불일치"),
    ("toss-payments-webhook-controller", "토스페이먼츠 웹훅 수신"),
    ("webhook-endpoint-controller", "웹훅"),
]


def read_operations(text):
    """paths 절을 줄 단위로 읽어 operationId -> (path, method, tags)를 만든다. springdoc 출력의 들여쓰기에 기댄다."""
    ops = {}
    in_paths = False
    path = method = None
    current = None
    in_tags = False
    for line in text.splitlines():
        if line == "paths:":
            in_paths = True
            continue
        if in_paths and re.match(r"^\S", line):
            break
        if not in_paths:
            continue
        m = re.match(r"^  (/\S*):$", line)
        if m:
            path, method, current, in_tags = m.group(1), None, None, False
            continue
        m = re.match(r"^    (get|put|post|delete|patch|head|options):$", line)
        if m:
            method, current, in_tags = m.group(1), {"path": path, "method": m.group(1), "tags": []}, False
            continue
        if current is None:
            continue
        m = re.match(r"^      operationId: (\S+)$", line)
        if m:
            ops[m.group(1)] = current
            continue
        if line == "      tags:":
            in_tags = True
            continue
        m = re.match(r"^      - (\S+)$", line)
        if in_tags and m:
            current["tags"].append(m.group(1))
            continue
        if re.match(r"^      \S", line):
            in_tags = False
    return ops


def target(op):
    return "$.paths['%s'].%s" % (op["path"], op["method"])


def main():
    text = SPEC.read_text(encoding="utf-8")
    ops = read_operations(text)
    errors = []
    excluded = {i for ids in EXCLUDED.values() for i in ids}
    hidden = {i for ids in HIDDEN.values() for i in ids}
    for i in sorted(excluded | hidden):
        if i not in ops:
            errors.append("스펙에 없는 operationId: " + i)
    if excluded & hidden:
        errors.append("제외와 숨김에 같이 있음: %s" % sorted(excluded & hidden))
    used_tags = {t for op in ops.values() for t in op["tags"]}
    visible_tags = {t for i, op in ops.items() if i not in excluded for t in op["tags"]}
    named = [t for t, _ in TAG_NAMES]
    for t in sorted(visible_tags - set(named)):
        errors.append("표시 이름이 없는 태그: " + t)
    for t in named:
        if t not in used_tags:
            errors.append("스펙에 없는 태그: " + t)
    if not re.search(r"^servers:\n- description: .*\n  url: /\n", text, re.M):
        errors.append("servers 절이 예상한 모양(url: /, 하나)이 아니다")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        sys.exit(1)

    out = []
    w = out.append
    w("# 생성 파일: guide/scripts/make_overlay.py 로 다시 만든다. 손으로 고치지 않는다.")
    w("overlay: 1.0.0")
    w("info:")
    w('  title: "MeterEngine 가이드용 조정"')
    w('  version: "1.0.0"')
    w("actions:")
    w("  # 경로가 모두 /v1/ 로 시작하므로 servers 에 /v1 을 붙이지 않는다.")
    w('  - target: "$.servers[0]"')
    w("    update:")
    w('      url: "%s"' % SERVER)
    w('      description: "MeterEngine API"')
    for group, ids in EXCLUDED.items():
        w("  # x-excluded: %s" % group)
        for i in ids:
            w('  - target: "%s"' % target(ops[i]))
            w("    update:")
            w("      x-excluded: true")
    for group, ids in HIDDEN.items():
        w("  # x-hidden: %s" % group)
        for i in ids:
            w('  - target: "%s"' % target(ops[i]))
            w("    update:")
            w("      x-hidden: true")
    w("  # 최상위 tags 가 원본에 없어 새로 만든다. x-group 은 탐색 그룹 표시 이름이다.")
    w('  - target: "$"')
    w("    update:")
    w("      tags:")
    for t, name in TAG_NAMES:
        if t not in visible_tags:
            continue
        w('        - name: "%s"' % t)
        w('          x-group: "%s"' % name)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(out) + "\n", encoding="utf-8")
    print("operation %d, 제외 %d, 숨김 %d, 태그 %d -> %s" % (
        len(ops), len(excluded), len(hidden), sum(1 for t, _ in TAG_NAMES if t in visible_tags), OUT))
    dropped = sorted(used_tags - visible_tags)
    if dropped:
        print("모든 operation이 제외되어 표시 이름을 넣지 않은 태그: " + ", ".join(dropped))


if __name__ == "__main__":
    main()
