"use client";

import { ArrowRightOutlined, LoadingOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { useState } from "react";
import { COMPANY, METRICS } from "@/lib/catalog";
import type { SessionResponse } from "@/lib/contracts";
import { Notice } from "./Feedback";

export function LoginScreen({ session, error, isBusy, onLogin, onReload }: { session: SessionResponse | null; error: string; isBusy: boolean; onLogin: (accountId: string) => void; onReload: () => void }) {
  const [accountId, setAccountId] = useState("");
  const accounts = session?.accounts ?? [];
  const selected = accountId || accounts[0]?.id || "";
  return <main id="main-content" className="welcome">
    <section className="welcome-story" aria-labelledby="welcome-title">
      <p className="wordmark wordmark-large">{COMPANY.name}</p>
      <h1 id="welcome-title">대화, API, 일괄 처리를<br />한 계정에서 쓰세요.</h1>
      <p className="welcome-description">모델을 쓴 만큼 토큰이 쌓이고, 이번 달 청구 예정액에 바로 반영돼요.</p>
      <div className="welcome-meter" aria-label="토큰 종류">
        <p>요금은 토큰 네 가지로 따로 계산해요</p>
        <ul>{METRICS.map(metric => <li key={metric.key}><span className={`token-swatch token-${metric.key}`} aria-hidden="true" />{metric.name}</li>)}</ul>
      </div>
    </section>
    <section className="welcome-signin" aria-labelledby="signin-title">
      <h2 id="signin-title">회사 계정으로 시작하기</h2>
      <p>체험할 회사를 고르세요. 회사마다 대화와 사용 내역을 따로 보관해요.</p>
      {session === null && !error ? <div className="loading-copy" role="status"><LoadingOutlined spin aria-hidden="true" /> 회사 계정을 불러오고 있어요.</div>
        : !session?.configured ? <Notice title={error ? "서비스에 연결하지 못했어요" : "서비스 연결을 준비하고 있어요"} onRetry={onReload}>{error || "체험 계정과 요금 설정이 준비되면 여기에서 시작할 수 있어요."}</Notice>
        : <form onSubmit={event => { event.preventDefault(); if (selected) onLogin(selected); }}>
          <fieldset className="account-choices" disabled={isBusy}>
            <legend>회사 계정</legend>
            {accounts.map(account => <label key={account.id} className="account-choice">
              <input type="radio" name="accountId" value={account.id} checked={selected === account.id} onChange={() => setAccountId(account.id)} />
              <span><strong>{account.name}</strong><small>{account.email}</small></span>
            </label>)}
          </fieldset>
          {error && <p className="field-error" role="alert">{error}</p>}
          <Button type="primary" htmlType="submit" block loading={isBusy} disabled={!selected} icon={<ArrowRightOutlined aria-hidden="true" />} iconPlacement="end">이 계정으로 시작</Button>
        </form>}
      <p className="demo-identity-note">체험용 계정이라 가입이나 본인 확인 없이 들어가요.</p>
    </section>
  </main>;
}
