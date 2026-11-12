"use client";

import { Alert, App, Button, Flex } from "antd";
import { alertIcon } from "@/components/screen/alert-icon";
import { EmptyState } from "@/components/screen/EmptyState";
import { useTenantScope } from "@/components/auth/TenantAccess";
import { useEffect, useState } from "react";
import { createInvoiceViewLink, revokeInvoiceViewLink } from "@/app/(console)/invoices/view-link-actions";
import { formatKstDateTime } from "@/lib/format";
import { isValidInvoiceViewToken, type InvoiceViewLinkSecretResponse, type InvoiceViewLinkResponse } from "@/lib/invoice-view-links";
import styles from "./InvoiceViewLinks.module.css";

// 같은 문구도 다시 읽히도록 상태 영역을 비운 뒤 다음 프레임에 넣는다.
function announce(setFeedback: (message: string) => void, message: string) { setFeedback(""); requestAnimationFrame(() => setFeedback(message)); }

export function InvoiceViewLinks({ invoiceId, initialLinks, loadError, checkedAt }: {
  invoiceId: string; initialLinks: InvoiceViewLinkResponse[]; loadError?: string; checkedAt: number;
}) {
  const canManage = useTenantScope("admin");
  const { modal } = App.useApp();
  const [links, setLinks] = useState(initialLinks);
  const [createdLink, setCreatedLink] = useState<InvoiceViewLinkSecretResponse | null>(null);
  const [sharedUrl, setSharedUrl] = useState("");
  const [expiresInDays, setExpiresInDays] = useState(30);
  const [canAllowPaymentRetry, setCanAllowPaymentRetry] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState("");
  const [error, setError] = useState("");
  const [currentTime, setCurrentTime] = useState(checkedAt);
  const isBusy = isCreating || revokingId !== null;

  useEffect(() => {
    const timer = window.setInterval(() => setCurrentTime(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  async function create() {
    if (isBusy || !canManage) return;
    setIsCreating(true); setFeedback(""); setError("");
    try {
      const result = await createInvoiceViewLink(invoiceId, expiresInDays, canAllowPaymentRetry);
      if (!result.ok) { setError(result.message); return; }
      if (!isValidInvoiceViewToken(result.data.token)) { setError("발급한 링크를 확인하지 못했어요. 목록을 새로 조회하세요."); return; }
      setCreatedLink(result.data);
      setSharedUrl(new URL(`/invoice-links#${result.data.token}`, window.location.origin).href);
      setLinks(current => [result.data, ...current]);
      announce(setFeedback, "고객 조회 링크를 발급했어요. 이 화면을 닫기 전에 복사하세요.");
    } catch { setError("발급 결과를 확인하지 못했어요. 목록을 새로 조회해 발급됐는지 확인한 뒤 다시 시도해 주세요."); }
    finally { setIsCreating(false); }
  }

  function confirmRevoke(link: InvoiceViewLinkResponse) {
    if (isBusy || !canManage) return;
    modal.confirm({
      title: "이 링크를 폐기할까요?",
      content: "이미 전달한 링크도 더 이상 쓸 수 없어요. 확정 청구와 결제 기록은 그대로 남아요.",
      okText: "링크 폐기", okButtonProps: { danger: true }, cancelText: "닫기",
      onOk: () => revoke(link),
    });
  }

  async function revoke(link: InvoiceViewLinkResponse) {
    if (isBusy || !canManage) return;
    setRevokingId(link.id); setError(""); setFeedback("");
    try {
      const result = await revokeInvoiceViewLink(invoiceId, link.id);
      if (!result.ok) { setError(result.message); return; }
      setLinks(current => current.map(row => row.id === link.id ? result.data : row));
      if (createdLink?.id === link.id) { setCreatedLink(null); setSharedUrl(""); }
      announce(setFeedback, "링크를 폐기했어요. 이 링크로는 청구서를 볼 수 없어요.");
    } catch { setError("폐기 결과를 확인하지 못했어요. 목록을 새로 조회하세요."); }
    finally { setRevokingId(null); }
  }

  async function copy() {
    try { await navigator.clipboard.writeText(sharedUrl); announce(setFeedback, "고객 조회 링크를 복사했어요."); }
    catch { setError("복사하지 못했어요. 아래 링크를 직접 선택해 복사해 주세요."); }
  }

  return <section id="invoice-view-links" className={`workspace-panel ${styles.panel}`} aria-labelledby="invoice-view-links-title" aria-busy={isBusy}>
    <div><h2 id="invoice-view-links-title">고객 조회 링크</h2><p className="muted">고객이 로그인 없이 이 인보이스와 결제 상태를 확인할 수 있어요. 링크를 가진 사람은 누구나 볼 수 있으니 그 고객에게만 전달하세요.</p></div>
    {canManage && <><div className={styles.createRow}>
      <label className={styles.period}>유효 기간<select value={expiresInDays} onChange={event => setExpiresInDays(Number(event.target.value))} disabled={isBusy}>
        <option value={7}>7일</option><option value={30}>30일</option><option value={90}>90일</option>
      </select></label>
    </div>
    <div><label className={styles.retryPermission}><input type="checkbox" checked={canAllowPaymentRetry} onChange={event => setCanAllowPaymentRetry(event.target.checked)} disabled={isBusy} aria-describedby="invoice-retry-permission-hint" /><span>고객의 실패 결제 재시도 허용</span></label><p id="invoice-retry-permission-hint" className={styles.permissionHint}>이 링크를 가진 사람은 금액을 확인한 뒤 등록된 카드로 실패한 결제를 다시 요청할 수 있어요. 이미 발급한 링크에는 적용되지 않아요.</p></div>
    <div className={styles.createRow}><Button type="primary" disabled={isBusy} onClick={create}>{isCreating ? "발급 중" : "고객 조회 링크 발급"}</Button></div></>}
    {!canManage && <p className="field-hint">링크 발급과 폐기는 전체 관리 권한이 있는 사용자만 할 수 있어요.</p>}
    {error && <Alert type="error" showIcon title={error} />}
    <p role="status" style={feedback ? undefined : { margin: 0 }}>{feedback}</p>
    {canManage && createdLink && sharedUrl && <div className={styles.issued}>
      <strong>발급한 링크는 이 화면에서만 확인할 수 있어요</strong>
      <p>{createdLink.allow_payment_retry ? "실패 결제 재시도 허용: 고객이 등록 카드로 다시 결제할 수 있어요." : "조회 전용: 이 링크로는 결제할 수 없어요."}</p>
      <p>{formatKstDateTime(createdLink.expires_at)} KST에 만료돼요. 목록에서는 링크 원문을 다시 확인할 수 없어요.</p>
      <label>전달할 고객 조회 링크<textarea readOnly value={sharedUrl} rows={3} onFocus={event => event.currentTarget.select()} /></label>
      <div className={styles.buttons}><Button onClick={copy}>고객 조회 링크 복사</Button><a href={sharedUrl} target="_blank" rel="noreferrer">고객 화면 열기</a></div>
    </div>}
    {/* router.refresh는 useState로 받은 목록을 바꾸지 않으므로 전체 새로고침한다. */}
    {loadError ? <Alert type="error" showIcon icon={alertIcon("error")} title="데이터를 불러오지 못했어요" description={<Flex vertical gap="small" align="start"><span>{loadError}</span><Button href="">목록 새로 조회</Button></Flex>} /> : null}
    <p className="field-hint">이 링크는 메일로 보내지 않아요. 복사해 고객에게 직접 전달하세요. 링크를 폐기하거나 기한이 지나면 조회와 재시도를 할 수 없어요.</p>
    {links.length ? <ul className={styles.list}>{links.map(link => {
      const isExpired = Date.parse(link.expires_at) <= currentTime;
      const state = link.revoked_at ? "폐기됨" : isExpired ? "만료됨" : "사용 가능";
      return <li key={link.id}>
        <div><strong>{state} / {link.mode === "test" ? "테스트 결제" : "실결제"}</strong><span>{link.allow_payment_retry ? "실패 결제 재시도 허용" : "조회 전용"}</span><span>발급 {formatKstDateTime(link.created_at)} KST</span><span>만료 {formatKstDateTime(link.expires_at)} KST</span>{link.revoked_at && <span>폐기 {formatKstDateTime(link.revoked_at)} KST</span>}</div>
        {canManage && !link.revoked_at && !isExpired && <Button type="link" size="small" danger onClick={() => confirmRevoke(link)} disabled={isBusy}>{revokingId === link.id ? "폐기 중" : "링크 폐기"}</Button>}
      </li>;
    })}</ul> : !loadError && <EmptyState title="발급한 고객 조회 링크가 없어요" />}
  </section>;
}
