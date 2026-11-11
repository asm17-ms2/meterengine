import { CONSENT_DOCUMENTS, consentEffectiveDate } from "@/lib/auth/consents";

export function ConsentFields({ disabled = false }: { disabled?: boolean }) {
  return <fieldset className="consent-fields" disabled={disabled}>
    <legend>약관과 개인정보 처리방침</legend>
    <p className="field-hint">{consentEffectiveDate()} 시행. 전문은 새 창에서 열립니다.</p>
    {CONSENT_DOCUMENTS.map(doc => <div key={doc.document}>
      <a className="consent-document-link" href={doc.href} target="_blank" rel="noopener">{doc.title} 전문 보기<span className="consent-document-link-note"> (새 창)</span></a>
      <label className="consent-check"><input type="checkbox" required name={doc.document} />{doc.checkLabel}</label>
    </div>)}
  </fieldset>;
}
