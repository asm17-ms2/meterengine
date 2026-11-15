import { Alert, Button } from "antd";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { isUuid } from "@/lib/uuid";
import { workspaceFetch, type InvoicePaymentResponse } from "@/lib/api/workspace";
import type { InvoiceResponse } from "@/lib/api/invoices";
import { formatKoreanMonth, formatKrw, formatKstDateTime } from "@/lib/format";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { InvoiceViewLinks } from "@/components/workspace/InvoiceViewLinks";
import type { InvoiceViewLinkResponse } from "@/lib/invoice-view-links";
import { paymentReadiness } from "./card-state";
import { INVOICE_PAYMENT_STATUS_LABELS } from "@/lib/invoice-payment-status";

type PaymentAttemptResponse = {
  id: string; invoice_id: string; status: string; amount: number;
  mode: string; failure_message: string | null; requested_at: string;
};
type PaymentCancellationResponse = {
  id: string; status: string; amount: number; reason: string; requested_at: string;
};

// 결제 시도 결과는 토스페이먼츠 승인 결과라 "승인", "승인 실패"로 보인다.
const ATTEMPT_STATUS_LABELS: Record<string, string> = { done: "승인", failed: "승인 실패" };

const PAYMENT_STATES: Record<string, { label: string; description: string }> = {
  unpaid: { label: INVOICE_PAYMENT_STATUS_LABELS.unpaid, description: "등록한 카드로 이 청구서의 결제를 요청할 수 있어요." },
  failed: { label: INVOICE_PAYMENT_STATUS_LABELS.failed, description: "실패 사유와 등록 카드를 확인한 뒤 다시 결제를 요청하세요." },
  pending: { label: INVOICE_PAYMENT_STATUS_LABELS.pending, description: "진행 중인 결제의 결과가 확인될 때까지 추가로 결제를 요청할 수 없어요. 새로고침한 뒤 확인하세요." },
  paid: { label: INVOICE_PAYMENT_STATUS_LABELS.paid, description: "승인된 결제가 있어 이 청구서로 다시 결제를 요청할 수 없어요." },
  partially_canceled: { label: INVOICE_PAYMENT_STATUS_LABELS.partially_canceled, description: "승인된 결제의 일부를 취소했어요. 취소한 금액은 청구 금액과 따로 기록해요. 이 청구서로는 다시 결제를 요청할 수 없어요." },
  canceled: { label: INVOICE_PAYMENT_STATUS_LABELS.canceled, description: "결제 금액 전부를 취소했어요. 청구 금액은 그대로 남아요. 이 청구서로는 다시 결제를 요청할 수 없어요." },
  not_required: { label: INVOICE_PAYMENT_STATUS_LABELS.not_required, description: "청구 금액이 0원이라 결제할 금액이 없어요." },
};

async function CancellationHistory({ attempt }: { attempt: PaymentAttemptResponse }) {
  const cancellations = await workspaceFetch<{ payment_cancellations: PaymentCancellationResponse[] }>(
    "/v1/payment-attempts/" + encodeURIComponent(attempt.id) + "/cancellations",
  );
  if (!cancellations.ok) return <section className="workspace-panel">
    <h2>결제 취소</h2>
    <p className="field-hint">{formatKstDateTime(attempt.requested_at)} 승인 / {attempt.mode === "test" ? "테스트 환경" : "라이브 환경"}</p>
    <WorkspaceError message={cancellations.error.message} status={cancellations.error.status} />
    <p className="muted">취소 내역을 확인한 뒤 새로 요청할 수 있어요.</p>
  </section>;

  const rows = cancellations.data.payment_cancellations;
  const isValidHistory = Number.isSafeInteger(attempt.amount) && attempt.amount > 0 && rows.every(row =>
    ["pending", "done", "failed"].includes(row.status) && Number.isSafeInteger(row.amount) && row.amount > 0,
  );
  const canceledAmount = rows.filter(row => row.status === "done").reduce((sum, row) => sum + row.amount, 0);
  const remainingAmount = attempt.amount - canceledAmount;
  const hasPending = rows.some(row => row.status === "pending");
  const canCancel = isValidHistory && remainingAmount > 0 && !hasPending;
  const isFullyCanceled = isValidHistory && remainingAmount === 0;
  return <section className="workspace-panel">
    <div className="workspace-heading">
      <div><h2>결제 취소</h2><p className="field-hint">{formatKstDateTime(attempt.requested_at)} 승인 / {attempt.mode === "test" ? "테스트 환경" : "라이브 환경"}</p></div>
      {canCancel && <div className="workspace-actions">
        {remainingAmount > 1 && <ActionPanel title="부분 취소" label="입력한 금액 취소 요청" operation="createPaymentCancellation" isDanger
          description={(attempt.mode === "test" ? "테스트 환경 결제예요. " : "라이브 환경 결제예요. ") + "취소할 금액을 입력하세요. 취소할 수 있는 금액은 " + formatKrw(remainingAmount) + "이에요. 취소 요청은 되돌릴 수 없어요."}
          fields={[
            { name: "id", label: "", type: "hidden", value: attempt.id },
            { name: "amount", label: "취소 금액 (원)", type: "number", min: 1, max: remainingAmount - 1, hint: "전액을 취소하려면 '남은 금액 전액 취소'를 쓰세요." },
            { name: "reason", label: "취소 사유" },
          ]} />}
        <ActionPanel title="남은 금액 전액 취소" label={formatKrw(remainingAmount) + " 전액 취소 요청"} operation="createPaymentCancellation" isDanger
          description={(attempt.mode === "test" ? "테스트 환경 결제예요. " : "라이브 환경 결제예요. ") + "취소할 수 있는 금액 " + formatKrw(remainingAmount) + " 전부를 취소해요. 청구 금액은 그대로 남고, 취소한 뒤에는 이 청구서로 다시 결제를 요청할 수 없어요. 취소 요청은 되돌릴 수 없어요."}
          fields={[
            { name: "id", label: "", type: "hidden", value: attempt.id },
            { name: "amount", label: "", type: "hidden", value: String(remainingAmount) },
            { name: "reason", label: "취소 사유" },
          ]} />
      </div>}

    </div>
    {!isValidHistory || remainingAmount < 0 ? <WorkspaceError message="취소 금액을 확인하지 못했어요. 새로고침한 뒤 확인하세요." />
      : <>
        <p>승인액 {formatKrw(attempt.amount)} / 취소 완료 {formatKrw(canceledAmount)} / 취소할 수 있는 금액 {formatKrw(remainingAmount)}</p>
        {hasPending ? <Alert type="info" showIcon title="취소 결과 확인 중" description="진행 중인 취소의 결과가 나올 때까지 추가로 취소할 수 없어요. 새로고침한 뒤 확인하세요." />
          : isFullyCanceled ? <Alert type="success" showIcon title="전액 취소 완료" description="이 결제에서 추가로 취소할 금액이 없어요." /> : null}
      </>}
    <WorkspaceTable label={formatKstDateTime(attempt.requested_at) + "에 승인된 결제의 취소 내역"} rows={rows.map(row => ({ ...row, requested_at: formatKstDateTime(row.requested_at) }))}
      columns={[{ key: "requested_at", label: "요청 시각 (KST)" }, { key: "status", label: "결과", isStatus: true }, { key: "amount", label: "취소액", isMoney: true }, { key: "reason", label: "사유" }]}
      empty="이 결제에 요청한 취소가 없어요." />
  </section>;
}

function PaymentLoadFailure({ message, status }: { message: string; status?: number }) {
  return <>
    <WorkspaceHeader title={status === 404 ? "수납 내역을 찾을 수 없어요" : "수납 상세"}
      description={status === 404 ? "주소가 잘못됐거나 다른 조직의 청구서일 수 있어요. 지금 선택한 조직을 확인하세요." : "수납 내역을 불러오지 못했어요."}>
      <Link href="/payments">수납 목록으로</Link>
    </WorkspaceHeader>
    <WorkspaceError message={message} status={status} />
  </>;
}

export default async function PaymentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  // 청구서 id 모양이 아니면 서버가 404가 아닌 400을 주므로, 묻지 않고 찾을 수 없다고 알린다.
  if (!isUuid(id)) return <PaymentLoadFailure message="청구서를 찾을 수 없어요" status={404} />;
  const [invoice, attempts, viewLinks] = await Promise.all([
    workspaceFetch<InvoiceResponse>("/v1/invoices/" + encodeURIComponent(id)),
    workspaceFetch<{ payment_attempts: PaymentAttemptResponse[] }>("/v1/invoices/" + encodeURIComponent(id) + "/payment-attempts"),
    workspaceFetch<{ invoice_view_links: InvoiceViewLinkResponse[] }>("/v1/invoices/" + encodeURIComponent(id) + "/view-links"),
  ]);
  if (!invoice.ok || !attempts.ok) {
    const failure = !invoice.ok ? invoice.error : !attempts.ok ? attempts.error : undefined;
    return <PaymentLoadFailure message={failure?.message ?? "조회하지 못했어요."} status={failure?.status} />;
  }
  const payments = await workspaceFetch<{ invoice_payments: InvoicePaymentResponse[] }>("/v1/invoice-payments", {
    month: invoice.data.month, customer_id: invoice.data.customer_id,
  });
  const currentPayment = payments.ok ? payments.data.invoice_payments.find(payment => payment.invoice_id === id) : undefined;
  const [card, keys] = await Promise.all([
    workspaceFetch<{ card_number: string; archived_at: string | null }>("/v1/customers/" + encodeURIComponent(invoice.data.customer_id) + "/payment-method"),
    workspaceFetch<{ toss_payments_credentials: { mode: string }[] }>("/v1/toss-payments-credentials"),
  ]);
  const readiness = paymentReadiness(currentPayment?.mode, keys, card);
  const state = currentPayment ? PAYMENT_STATES[currentPayment.status] : undefined;
  const canCollect = currentPayment && ["unpaid", "failed"].includes(currentPayment.status);
  return <>
    <WorkspaceHeader title={invoice.data.customer_name + "의 수납"}
      description={formatKoreanMonth(invoice.data.month) + " 청구 금액 " + formatKrw(invoice.data.total_amount)}>
      <Link href={"/customers/" + invoice.data.customer_id + "?month=" + invoice.data.month}>고객으로 이동</Link>
      {/* router.refresh는 결과를 확인하지 못한 취소의 요청 ID(ActionPanel 상태)를 남기므로 전체 새로고침한다. */}
      <Button href="">새로고침</Button>
    </WorkspaceHeader>
    {invoice.data.invoice_number && <p className="field-hint">청구서 번호 {invoice.data.invoice_number}</p>}
    <section className="workspace-panel">
      <div className="workspace-heading">
        <h2>현재 결제 상태</h2>
        {canCollect && readiness === "card-missing" && <ActionPanel title="카드 등록 링크 만들기" operation="createPaymentMethodRegistration"
          description={invoice.data.customer_name + " 고객이 " + (currentPayment.mode === "test" ? "테스트 환경" : "라이브 환경") + "에서 쓸 카드를 등록하는 링크를 만들어요. 만든 링크를 고객에게 전달하세요."}
          fields={[{ name: "id", type: "hidden", label: "", value: invoice.data.customer_id }]} />}
        {canCollect && readiness === "key-missing" && <Link href="/settings">조직 설정에서 토스페이먼츠 키 저장</Link>}
        {canCollect && (readiness === "usable" || readiness === "unknown") && <ActionPanel title={currentPayment.status === "failed" ? "결제 다시 시도" : "등록 카드로 결제"} operation="createPaymentAttempt"
          description={(currentPayment?.mode === "test" ? "테스트 환경: " : "라이브 환경: ") + invoice.data.customer_name + "의 " + formatKrw(invoice.data.total_amount) + " 결제를 " + (card.ok ? "등록 카드(" + card.data.card_number + ")로" : "등록 카드로") + " 요청해요. 결과를 모르는 이전 결제가 있으면 토스페이먼츠에서 먼저 확인하고, 확인되지 않으면 결제를 요청하지 않아요."}
          fields={[{ name: "id", type: "hidden", label: "", value: id }]} />}
      </div>
      {!payments.ok ? <WorkspaceError message={payments.error.message} status={payments.error.status} />
        : !currentPayment || !state ? <WorkspaceError message="현재 결제 상태를 확인하지 못했어요. 새로고침한 뒤 확인하세요." />
          : <><p role="status"><strong>{currentPayment.mode === "test" ? "테스트 환경" : "라이브 환경"} / {state.label}</strong></p><p className="muted">{canCollect && readiness === "card-missing" ? "이 환경에 등록된 카드가 없어 결제를 요청할 수 없어요. 카드 등록 링크를 만들어 고객에게 전달하세요." : canCollect && readiness === "key-missing" ? "이 환경의 토스페이먼츠 키가 없어 결제를 요청할 수 없어요. 조직 설정에서 키를 저장하세요." : state.description}</p></>}
      <h3>결제 시도 기록</h3>
      <WorkspaceTable label="결제 시도 기록" rows={attempts.data.payment_attempts.map(attempt => ({ ...attempt, requested_at: formatKstDateTime(attempt.requested_at) }))}
        columns={[{ key: "requested_at", label: "요청 시각 (KST)" }, { key: "status", label: "결과", isStatus: true, statusLabels: ATTEMPT_STATUS_LABELS }, { key: "amount", label: "금액", isMoney: true }, { key: "mode", label: "업무 환경" }, { key: "failure_message", label: "실패 사유" }]} />
    </section>
    {attempts.data.payment_attempts.filter(attempt => attempt.status === "done").map(attempt => <CancellationHistory key={attempt.id} attempt={attempt} />)}
    <InvoiceViewLinks invoiceId={id} initialLinks={viewLinks.ok ? viewLinks.data.invoice_view_links : []} loadError={viewLinks.ok ? undefined : viewLinks.error.message} checkedAt={new Date().getTime()} />
  </>;
}
