import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { workspaceFetch, type InvoicePaymentResponse } from "@/lib/api/workspace";
import type { InvoiceResponse } from "@/lib/api/invoices";
import { formatKrw, formatKstDateTime } from "@/lib/format";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { paymentReadiness } from "./card-state";
import { INVOICE_PAYMENT_STATUS_LABELS } from "@/lib/invoice-payment-status";

type PaymentAttemptResponse = {
  id: string; invoice_id: string; status: string; amount: number;
  mode: string; failure_message: string | null; requested_at: string;
};
type PaymentCancellationResponse = {
  id: string; status: string; amount: number; reason: string; requested_at: string;
};

const PAYMENT_STATES: Record<string, { label: string; description: string }> = {
  unpaid: { label: INVOICE_PAYMENT_STATUS_LABELS.unpaid, description: "등록한 카드로 이 인보이스의 결제를 요청할 수 있습니다." },
  failed: { label: INVOICE_PAYMENT_STATUS_LABELS.failed, description: "실패 사유와 등록 카드를 확인한 뒤 다시 결제를 요청하세요." },
  pending: { label: INVOICE_PAYMENT_STATUS_LABELS.pending, description: "진행 중인 결제의 결과가 확인될 때까지 추가로 청구할 수 없습니다. 내역을 새로 조회해 주세요." },
  paid: { label: INVOICE_PAYMENT_STATUS_LABELS.paid, description: "승인된 결제가 있어 이 인보이스로 다시 청구할 수 없습니다." },
  partially_canceled: { label: INVOICE_PAYMENT_STATUS_LABELS.partially_canceled, description: "승인된 결제의 일부를 취소했습니다. 취소는 청구액과 별도로 기록하며 재청구는 별도 작업입니다." },
  canceled: { label: INVOICE_PAYMENT_STATUS_LABELS.canceled, description: "결제 금액 전부를 취소했습니다. 확정 청구액은 보존되며 재청구는 별도 작업입니다." },
  not_required: { label: INVOICE_PAYMENT_STATUS_LABELS.not_required, description: "청구 금액이 0원이므로 결제할 금액이 없습니다." },
};

async function CancellationHistory({ attempt }: { attempt: PaymentAttemptResponse }) {
  const cancellations = await workspaceFetch<{ payment_cancellations: PaymentCancellationResponse[] }>(
    "/v1/payment-attempts/" + encodeURIComponent(attempt.id) + "/cancellations",
  );
  if (!cancellations.ok) return <section className="workspace-panel">
    <h2>승인 결제 취소</h2>
    <p className="field-hint">{formatKstDateTime(attempt.requested_at)} 승인 / {attempt.mode}</p>
    <WorkspaceError message={cancellations.error.message} />
    <p className="muted">취소 내역을 확인한 뒤 새로 요청할 수 있습니다.</p>
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
      <div><h2>승인 결제 취소</h2><p className="field-hint">{formatKstDateTime(attempt.requested_at)} 승인 / {attempt.mode}</p></div>
      {canCancel && <ActionPanel title="부분 또는 전액 취소" operation="createPaymentCancellation" isDanger
        description={(attempt.mode === "test" ? "테스트 결제 취소. " : "실결제 취소. ") + "최대 " + formatKrw(remainingAmount) + "까지 취소할 수 있습니다. 금액을 비우면 남은 금액 전부를 취소합니다. 취소 요청은 되돌릴 수 없습니다."}
        fields={[
          { name: "id", label: "", type: "hidden", value: attempt.id },
          { name: "amount", label: "취소 금액 (원)", type: "number", min: 1, max: remainingAmount, isOptional: true, hint: "최대 " + formatKrw(remainingAmount) + ". 비우면 남은 금액 전부" },
          { name: "reason", label: "취소 사유" },
        ]} />}
    </div>
    {!isValidHistory || remainingAmount < 0 ? <WorkspaceError message="취소 금액을 확인하지 못했습니다. 내역을 다시 조회해 주세요." />
      : <>
        <p>승인액 {formatKrw(attempt.amount)} / 취소 완료 {formatKrw(canceledAmount)} / 잔액 {formatKrw(remainingAmount)}</p>
        {hasPending ? <p role="status"><strong>취소 결과 확인 중</strong><br />진행 중인 취소가 확정될 때까지 추가로 취소할 수 없습니다. 내역을 새로 조회해 주세요.</p>
          : isFullyCanceled ? <p role="status"><strong>전액 취소 완료</strong><br />이 승인 결제에서 추가로 취소할 금액이 없습니다.</p> : null}
      </>}
    <WorkspaceTable rows={rows.map(row => ({ ...row, requested_at: formatKstDateTime(row.requested_at) }))}
      columns={[{ key: "requested_at", label: "요청 시각 (KST)" }, { key: "status", label: "결과", isStatus: true }, { key: "amount", label: "취소액", isMoney: true }, { key: "reason", label: "사유" }]}
      empty="이 승인 결제에 요청한 취소가 없습니다." />
  </section>;
}

export default async function PaymentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [invoice, attempts] = await Promise.all([
    workspaceFetch<InvoiceResponse>("/v1/invoices/" + encodeURIComponent(id)),
    workspaceFetch<{ payment_attempts: PaymentAttemptResponse[] }>("/v1/invoices/" + encodeURIComponent(id) + "/payment-attempts"),
  ]);
  if (!invoice.ok || !attempts.ok) return <WorkspaceError message={!invoice.ok ? invoice.error.message : !attempts.ok ? attempts.error.message : "조회하지 못했습니다."} />;
  const payments = await workspaceFetch<{ invoice_payments: InvoicePaymentResponse[] }>("/v1/invoice-payments", {
    month: invoice.data.month, customer_id: invoice.data.customer_id,
  });
  const currentPayment = payments.ok ? payments.data.invoice_payments.find(payment => payment.invoice_id === id) : undefined;
  const [card, keys] = await Promise.all([
    workspaceFetch<{ card_number: string }>("/v1/customers/" + encodeURIComponent(invoice.data.customer_id) + "/payment-method"),
    workspaceFetch<{ toss_payments_credentials: { mode: string }[] }>("/v1/toss-payments-credentials"),
  ]);
  const readiness = paymentReadiness(currentPayment?.mode, keys, card);
  const state = currentPayment ? PAYMENT_STATES[currentPayment.status] : undefined;
  const canCollect = currentPayment && ["unpaid", "failed"].includes(currentPayment.status);
  return <>
    <WorkspaceHeader title={invoice.data.customer_name + "의 수납"} kicker="도입사 콘솔"
      description={invoice.data.month + " 청구액 " + formatKrw(invoice.data.total_amount) + ". 확정 금액은 결제와 취소 이후에도 보존됩니다."}>
      <Link href="/customers">고객 목록으로 이동</Link>
      <a href="">내역 새로 조회</a>
    </WorkspaceHeader>
    {invoice.data.invoice_number && <p className="field-hint">청구서 번호 {invoice.data.invoice_number}</p>}
    <section className="workspace-panel">
      <div className="workspace-heading">
        <h2>현재 결제 상태</h2>
        {canCollect && readiness === "card-missing" && <ActionPanel title="카드 등록 링크 만들기" operation="createPaymentMethodRegistration"
          description={invoice.data.customer_name + "이 결제 모드의 카드를 등록할 수 있는 링크를 만듭니다. 링크를 고객에게 전달하세요."}
          fields={[{ name: "id", type: "hidden", label: "", value: invoice.data.customer_id }]} />}
        {canCollect && readiness === "key-missing" && <Link href="/settings">설정에서 토스페이먼츠 키 저장</Link>}
        {canCollect && (readiness === "usable" || readiness === "unknown") && <ActionPanel title={currentPayment.status === "failed" ? "결제 다시 시도" : "등록 카드로 결제"} operation="createPaymentAttempt"
          description={(currentPayment?.mode === "test" ? "테스트 결제: " : "실결제: ") + invoice.data.customer_name + "의 " + formatKrw(invoice.data.total_amount) + "을 " + (card.ok ? "등록 카드(" + card.data.card_number + ")로" : "등록 카드로") + " 청구합니다. 결과가 불확실한 이전 시도는 먼저 조회합니다."}
          fields={[{ name: "id", type: "hidden", label: "", value: id }]} />}
      </div>
      {!payments.ok ? <WorkspaceError message={payments.error.message} />
        : !currentPayment || !state ? <WorkspaceError message="현재 결제 상태를 확인하지 못했습니다. 내역을 다시 조회해 주세요." />
          : <><p role="status"><strong>{currentPayment.mode === "test" ? "테스트 결제" : "실결제"} / {state.label}</strong></p><p className="muted">{canCollect && readiness === "card-missing" ? "결제 모드에 맞는 등록 카드가 없어 결제를 요청할 수 없습니다. 카드 등록 링크를 만들어 고객에게 전달하세요." : canCollect && readiness === "key-missing" ? "결제 모드의 토스페이먼츠 키가 없어 결제를 요청할 수 없습니다. 설정에서 키를 저장하세요." : state.description}</p></>}
      <h3>모든 모드의 결제 시도</h3>
      <WorkspaceTable rows={attempts.data.payment_attempts.map(attempt => ({ ...attempt, requested_at: formatKstDateTime(attempt.requested_at) }))}
        columns={[{ key: "requested_at", label: "요청 시각 (KST)" }, { key: "status", label: "결과", isStatus: true }, { key: "amount", label: "금액", isMoney: true }, { key: "mode", label: "모드" }, { key: "failure_message", label: "실패 사유" }]} />
    </section>
    {attempts.data.payment_attempts.filter(attempt => attempt.status === "done").map(attempt => <CancellationHistory key={attempt.id} attempt={attempt} />)}
  </>;
}
