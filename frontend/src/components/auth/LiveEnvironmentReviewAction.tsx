"use client";

import { Alert, App, Button, Modal } from "antd";
import { startTransition, useActionState, useEffect, useId, useRef, useState, type FormEvent } from "react";
import { reviewLiveEnvironmentApplicationAction, type LiveEnvironmentReviewState } from "@/app/admin/actions";

type Props = { id: string; target: string; value: "approved" | "rejected" };

function ReviewDialog({ id, target, value, onClose }: Props & { onClose: () => void }) {
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string>();
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const { message } = App.useApp();
  // 검토를 마친 신청은 목록에서 빠져 이 창도 함께 닫히므로 결과는 화면 위 알림으로 남긴다.
  const [state, action, isPending] = useActionState<LiveEnvironmentReviewState, FormData>(async (previous, data) => {
    const result = await reviewLiveEnvironmentApplicationAction(previous, data);
    if (result.done && result.message) message.success(result.message);
    if (result.isStale && result.message) message.warning(result.message);
    return result;
  }, {});
  const isRejection = value === "rejected";
  const label = isRejection ? "신청 거절" : "이용 승인";
  const reasonId = useId();
  const hasFailed = Boolean(state.message && !state.done && !state.isStale);

  useEffect(() => {
    if (hasFailed) reasonRef.current?.focus();
  }, [hasFailed, state]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isRejection && !reason.trim()) {
      setReasonError("거절 사유를 입력하세요.");
      reasonRef.current?.focus();
      return;
    }
    const data = new FormData(event.currentTarget);
    startTransition(() => action(data));
  }

  return <Modal className="workspace-dialog" width="min(560px, calc(100vw - 32px))" style={{ top: 24 }} styles={{ body: { maxHeight: "calc(100dvh - 200px)", overflowY: "auto", padding: 8, marginInline: -8 } }} title={`${label} 확인`} open onCancel={onClose} footer={null} closable={!isPending} mask={{ closable: !isPending }} keyboard={!isPending}>
    <p><strong>{target}</strong></p>
    <p>{isRejection ? "거절 사유는 신청한 조직의 화면에 그대로 보여요. 조직은 내용을 고쳐 다시 신청할 수 있어요." : "승인하면 이 조직이 실제 고객에게 청구하고 결제를 받을 수 있어요. 승인한 뒤에는 실제 환경을 다시 잠글 수 없어요."}</p>
    <form noValidate onSubmit={submit} className="workspace-form" aria-busy={isPending}>
      <input type="hidden" name="id" value={id} /><input type="hidden" name="status" value={value} />
      {isRejection && <div className="workspace-field"><label htmlFor={reasonId}>거절 사유<span className="required-label"> (필수)</span></label><textarea ref={reasonRef} id={reasonId} name="rejection_reason" rows={4} maxLength={500} required value={reason} onChange={event => { setReason(event.target.value); setReasonError(undefined); }} aria-invalid={Boolean(reasonError)} aria-describedby={[`${reasonId}-hint`, reasonError ? `${reasonId}-error` : undefined].filter(Boolean).join(" ")} /><small id={`${reasonId}-hint`} className="field-hint">무엇을 고쳐 다시 신청하면 되는지 적으세요. 500자까지 쓸 수 있어요.</small>{reasonError && <small id={`${reasonId}-error`} className="field-error">{reasonError}</small>}</div>}
      {hasFailed && <Alert type="error" showIcon title={<span role="alert">{state.message}</span>} />}
      <div className="confirmation-actions"><Button onClick={onClose} disabled={isPending}>닫기</Button><Button type="primary" danger={isRejection} htmlType="submit" loading={isPending} disabled={state.done || state.isStale}>{label} 확정</Button></div>
    </form>
  </Modal>;
}

// 창을 닫으면 입력한 사유와 지난 오류를 버리도록 열 때마다 새로 만든다.
export function LiveEnvironmentReviewAction(props: Props) {
  const [isOpen, setIsOpen] = useState(false);
  const isRejection = props.value === "rejected";
  return <>
    <Button danger={isRejection} onClick={() => setIsOpen(true)}>{isRejection ? "신청 거절" : "이용 승인"}</Button>
    {isOpen && <ReviewDialog {...props} onClose={() => setIsOpen(false)} />}
  </>;
}
