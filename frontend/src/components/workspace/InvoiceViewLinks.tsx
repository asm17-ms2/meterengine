"use client";

import { useTenantScope } from "@/components/auth/TenantAccess";
import { useEffect, useState } from "react";
import { createInvoiceViewLink, revokeInvoiceViewLink } from "@/app/(console)/invoices/view-link-actions";
import { formatKstDateTime } from "@/lib/format";
import { isValidInvoiceViewToken, type CreatedInvoiceViewLink, type InvoiceViewLink } from "@/lib/invoice-view-links";
import styles from "./InvoiceViewLinks.module.css";

export function InvoiceViewLinks({ invoiceId, initialLinks, loadError, checkedAt }: {
  invoiceId: string; initialLinks: InvoiceViewLink[]; loadError?: string; checkedAt: number;
}) {
  const canManage = useTenantScope("admin");
  const [links, setLinks] = useState(initialLinks);
  const [createdLink, setCreatedLink] = useState<CreatedInvoiceViewLink | null>(null);
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
      if (!isValidInvoiceViewToken(result.data.token)) { setError("발급한 링크를 확인하지 못했습니다. 목록을 새로 조회해 주세요."); return; }
      setCreatedLink(result.data);
      setSharedUrl(new URL(`/invoice-links#${result.data.token}`, window.location.origin).href);
      setLinks(current => [result.data, ...current]);
      setFeedback("고객 조회 링크를 발급했습니다. 이 화면을 닫기 전에 복사해 주세요.");
    } catch { setError("발급 결과를 확인하지 못했습니다. 목록을 새로 조회한 뒤 다시 시도해 주세요."); }
    finally { setIsCreating(false); }
  }

  async function revoke(link: InvoiceViewLink) {
    if (isBusy || !canManage || !window.confirm("이 링크의 고객 조회를 중지할까요? 이미 전달한 링크도 더 이상 사용할 수 없습니다.")) return;
    setRevokingId(link.id); setError(""); setFeedback("");
    try {
      const result = await revokeInvoiceViewLink(invoiceId, link.id);
      if (!result.ok) { setError(result.message); return; }
      setLinks(current => current.map(row => row.id === link.id ? result.data : row));
      if (createdLink?.id === link.id) { setCreatedLink(null); setSharedUrl(""); }
      setFeedback("링크를 폐기했습니다. 이 링크로 청구서를 조회할 수 없습니다.");
    } catch { setError("폐기 결과를 확인하지 못했습니다. 목록을 새로 조회해 주세요."); }
    finally { setRevokingId(null); }
  }

  async function copy() {
    try { await navigator.clipboard.writeText(sharedUrl); setFeedback("고객 조회 링크를 복사했습니다."); }
    catch { setError("복사하지 못했습니다. 아래 링크를 직접 선택해 복사해 주세요."); }
  }

  return <section id="invoice-view-links" className={`workspace-panel ${styles.panel}`} aria-labelledby="invoice-view-links-title" aria-busy={isBusy}>
    <div><h2 id="invoice-view-links-title">고객 조회 링크</h2><p className="muted">고객이 로그인 없이 이 청구서와 결제 상태를 확인할 수 있습니다. 링크를 가진 사람은 누구나 조회할 수 있으므로 해당 고객에게만 전달해 주세요.</p></div>
    {canManage && <><div className={styles.createRow}>
      <label className={styles.period}>사용 기간<select value={expiresInDays} onChange={event => setExpiresInDays(Number(event.target.value))} disabled={isBusy}>
        <option value={7}>7일</option><option value={30}>30일</option><option value={90}>90일</option>
      </select></label>
    </div>
    <div><label className={styles.retryPermission}><input type="checkbox" checked={canAllowPaymentRetry} onChange={event => setCanAllowPaymentRetry(event.target.checked)} disabled={isBusy} aria-describedby="invoice-retry-permission-hint" /><span>고객의 실패 결제 재시도 허용</span></label><p id="invoice-retry-permission-hint" className={styles.permissionHint}>이 링크를 가진 사람은 금액 확인 후 등록 카드로 실패한 결제를 다시 요청할 수 있습니다. 해당 고객에게만 전달해 주세요. 기존 조회 전용 링크는 바뀌지 않습니다.</p></div>
    <div className={styles.createRow}><button className="month-apply" type="button" disabled={isBusy} onClick={create}>{isCreating ? "발급 중" : "고객 조회 링크 발급"}</button></div></>}
    {!canManage && <p className="field-hint">조회 전용입니다. 링크 발급과 폐기는 관리 권한이 있는 사용자에게 요청하세요.</p>}
    {error && <p role="alert" className="field-error">{error}</p>}
    {feedback && <p role="status">{feedback}</p>}
    {canManage && createdLink && sharedUrl && <div className={styles.issued}>
      <strong>발급한 링크는 이 화면에서만 확인할 수 있습니다</strong>
      <p>{createdLink.allow_payment_retry ? "실패 결제 재시도 허용: 고객이 등록 카드로 다시 결제할 수 있습니다." : "조회 전용: 이 링크로 결제를 실행할 수 없습니다."}</p>
      <p>{formatKstDateTime(createdLink.expires_at)} KST까지 사용할 수 있습니다. 목록에서는 링크 원문을 다시 확인할 수 없습니다.</p>
      <label>전달할 고객 조회 링크<textarea readOnly value={sharedUrl} rows={3} onFocus={event => event.currentTarget.select()} /></label>
      <div className={styles.buttons}><button type="button" className="month-apply" onClick={copy}>고객 조회 링크 복사</button><a href={sharedUrl} target="_blank" rel="noreferrer">고객 화면 열기</a></div>
    </div>}
    {loadError ? <p role="alert">{loadError} <a href="">목록 새로 조회</a></p> : null}
    <p className="field-hint">메일은 자동 발송하지 않습니다. 폐기와 만료는 이 링크의 조회와 재시도 권한을 종료하며 확정 청구와 결제 기록은 유지합니다.</p>
    {links.length ? <ul className={styles.list}>{links.map(link => {
      const isExpired = Date.parse(link.expires_at) <= currentTime;
      const state = link.revoked_at ? "폐기됨" : isExpired ? "만료됨" : "사용 가능";
      return <li key={link.id}>
        <div><strong>{state} / {link.mode === "test" ? "테스트" : "실결제"}</strong><span>{link.allow_payment_retry ? "실패 결제 재시도 허용" : "조회 전용"}</span><span>발급 {formatKstDateTime(link.created_at)} KST</span><span>만료 {formatKstDateTime(link.expires_at)} KST</span>{link.revoked_at && <span>폐기 {formatKstDateTime(link.revoked_at)} KST</span>}</div>
        {canManage && !link.revoked_at && !isExpired && <button type="button" className={`month-apply ${styles.revoke}`} onClick={() => revoke(link)} disabled={isBusy}>{revokingId === link.id ? "폐기 중" : "링크 폐기"}</button>}
      </li>;
    })}</ul> : !loadError && <p className="muted">발급한 고객 조회 링크가 없습니다.</p>}
  </section>;
}
