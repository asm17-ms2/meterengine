const SUCCESS_MESSAGES: Partial<Record<string, string>> = {
  createMember: "사용자 계정을 만들었어요.",
  updateMemberRole: "사용자 역할을 바꿨어요.",
  updateMemberScopes: "사용자 권한을 저장했어요.",
  revokeOrganizationInvitation: "초대를 취소했어요.",
  deleteApiKey: "API 키를 폐기했어요.",
  updateApiKeyScopes: "API 키 권한을 저장했어요.",
  deleteCustomer: "고객을 보관했어요. 보관 목록에서 확인하세요.",
  archiveMember: "사용자 소속을 보관했어요. 보관 목록에서 확인하세요.",
  deleteWebhookEndpoint: "웹훅 URL을 보관했어요. 보관 목록에서 확인하세요.",
  updateSupplier: "공급자 정보를 저장했어요.",
  updatePaymentSetting: "결제 설정을 바꿨어요.",
  updateTossPaymentsCredential: "토스페이먼츠 키를 저장했어요.",
  archiveTossPaymentsCredential: "토스페이먼츠 키를 보관했어요.",
  createWebhookEndpoint: "웹훅 URL을 등록했어요.",
  updateWebhookEndpoint: "웹훅 설정을 저장했어요.",
  restoreWebhookEndpoint: "웹훅 URL을 복원했어요.",
  retryWebhookDelivery: "웹훅을 다시 보내도록 예약했어요.",
  restoreBillableMetric: "과금 미터를 복원했어요.",
  createLiveEnvironmentApplication: "라이브 환경 이용을 신청했어요.",
};

export function successMessage(operation: string): string {
  return SUCCESS_MESSAGES[operation] ?? "처리했어요.";
}

const UNCONFIRMED_MESSAGES: Partial<Record<string, string>> = {
  updateTossPaymentsCredential: "토스페이먼츠 키 저장 결과를 확인하지 못했어요. 화면을 새로고침한 뒤 토스페이먼츠 연동 목록의 저장 시각을 확인하고, 바뀌지 않았으면 다시 저장하세요.",
  createLiveEnvironmentApplication: "신청 결과를 확인하지 못했어요. 화면을 새로고침한 뒤 신청 내역에 있는지 확인하고, 없으면 다시 신청하세요.",
};

export function unconfirmedMessage(operation: string): string {
  return UNCONFIRMED_MESSAGES[operation] ?? "처리 결과를 확인하지 못했어요. 중복 요청을 피하려면 화면을 새로고침한 뒤 결제와 취소 내역을 먼저 확인하세요.";
}

const ERROR_MESSAGES: Partial<Record<string, string>> = {
  toss_payments_credential_missing: "이 환경의 토스페이먼츠 키가 없어요. 조직 설정의 토스페이먼츠 연동에서 키를 저장한 뒤 다시 시도하세요.",
  live_environment_already_open: "이미 라이브 환경을 쓰고 있어요. 화면을 새로고침한 뒤 확인하세요.",
  live_environment_application_in_progress: "확인 중인 신청이 있어요. 화면을 새로고침한 뒤 신청 상태를 확인하세요.",
  // 서버 문구 대신 콘솔 작업에서 자주 나는 오류를 해요체로 보인다. 오류 코드 값은 서버와 같다.
  internal_server_error: "일시적인 오류로 요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.",
  last_owner_required: "마지막 대표관리자는 다른 역할로 바꾸거나 소속을 보관할 수 없어요. 다른 사용자를 먼저 대표관리자로 바꾸세요.",
  owner_scopes_required: "대표관리자는 전체 관리 권한을 빼고 저장할 수 없어요. 권한을 줄이려면 역할을 먼저 관리자로 바꾸세요.",
  email_already_exists: "이미 가입한 이메일이에요. 초대 링크를 만들어 초대하세요.",
  organization_member_already_exists: "이미 이 조직에 소속된 사용자예요.",
  api_key_unavailable: "폐기되거나 만료된 API 키는 권한을 바꿀 수 없어요.",
  customer_archived: "보관한 고객이에요. 고객을 복원한 뒤 다시 시도하세요.",
  resource_archived: "보관한 항목이에요. 복원한 뒤 다시 시도하세요.",
  price_rate_history_immutable: "이번 달보다 앞선 달에 시작하는 단가는 등록할 수 없어요. 적용 시작 월을 이번 달 이후로 고르세요.",
  price_plan_history_immutable: "이번 달보다 앞선 달에 시작하는 요금제는 등록할 수 없어요. 적용 시작 월을 이번 달 이후로 고르세요.",
  payment_method_missing: "이 환경에 등록된 카드가 없어요. 카드 등록 링크를 만들어 고객에게 전달하세요.",
  invoice_already_paid: "이미 결제했거나 결제 중인 청구서예요. 결제와 취소 내역을 확인하세요.",
  invoice_amount_zero: "청구 금액이 0원인 청구서는 결제할 수 없어요.",
  payment_attempt_not_cancelable: "완료된 결제만 취소할 수 있어요. 결제와 취소 내역에서 결제 상태를 확인하세요.",
  webhook_endpoint_limit_exceeded: "웹훅 URL은 이 환경에 10개까지 둘 수 있어요. 쓰지 않는 웹훅 URL을 보관한 뒤 다시 시도하세요.",
  webhook_delivery_not_retryable: "다시 보낼 수 없는 웹훅이에요. 전송이 실패했는지, 웹훅 URL이 전송 중인지 확인하세요.",
};

// 같은 오류 코드라도 작업에 따라 다음에 할 일이 달라 작업별 문구를 먼저 찾는다.
const OPERATION_ERROR_MESSAGES: Partial<Record<string, Partial<Record<string, string>>>> = {
  createLiveEnvironmentApplication: { toss_payments_unavailable: "토스페이먼츠에서 키를 확인하지 못해 신청이 접수되지 않았어요. 잠시 뒤 다시 신청해 주세요." },
};

export function errorMessageForCode(code: string, operation?: string): string | undefined {
  return (operation ? OPERATION_ERROR_MESSAGES[operation]?.[code] : undefined) ?? ERROR_MESSAGES[code];
}

export function staleCancellationMessage(operation: string, code?: string): string | undefined {
  if (operation !== "createPaymentCancellation") return undefined;
  if (code === "payment_cancellation_idempotency_conflict") return unconfirmedMessage(operation);
  if (code === "payment_attempt_fully_canceled" || code === "payment_cancellation_amount_exceeded") return "다른 취소가 먼저 끝나 화면의 취소할 수 있는 금액이 최신이 아니에요. 이번 취소 요청은 반영하지 않았어요. 화면을 새로고침해 결제와 취소 내역을 확인한 뒤 다시 요청하세요.";
  return undefined;
}

export function staleVersionMessage(operation: string, code?: string): string | undefined {
  if (operation !== "updateCustomer" || code !== "resource_version_conflict") return undefined;
  return "다른 사람이 먼저 고객 정보를 바꿨어요. 최신 정보를 불러와 확인한 뒤 다시 수정하세요.";
}

export class InputFieldError extends Error {
  readonly field: string;

  constructor(field: string, message: string) {
    super(message);
    this.field = field;
  }
}

export function readWholeNumber(value: string, field: string, { min, max, message }: { min: number; max: number; message: string }): number {
  const number = Number(value);
  if (!/^\d+$/.test(value) || !Number.isSafeInteger(number) || number < min || number > max) throw new InputFieldError(field, message);
  return number;
}

export function readBusinessRegistrationNumber(value: string, field: string): string {
  const digits = value.replace(/[\s-]/g, "");
  if (!/^\d{10}$/.test(digits)) throw new InputFieldError(field, "사업자등록번호는 숫자 10자리로 입력하세요.");
  return digits;
}

// 같은 외부 고객 ID를 다시 보내면 서버는 기존 고객을 200으로 돌려주고 이름과 이메일을 바꾸지 않는다. 입력과 다르면 새로 만들지 않았다고 알린다.
export function existingCustomerMessage(input: { externalId: string; name: string; billingEmail: string }, response: Record<string, unknown> | undefined): string | undefined {
  if (!input.externalId || !response) return undefined;
  const isSameCustomer = response.name === input.name && String(response.billing_email ?? "") === input.billingEmail;
  return isSameCustomer ? undefined : `외부 고객 ID가 같은 기존 고객(이름: ${String(response.name ?? "")})이 있어 새로 등록하지 않았어요.`;
}
