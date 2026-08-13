"use client";

/**
 * 렌더링 중 예외가 났을 때의 최후 그물이다.
 *
 * 데이터를 못 불러온 상황은 여기로 오지 않는다. serverFetch가 던지지 않고 Result를
 * 돌려주고, 각 화면이 ErrorState를 제자리에 그린다. 여기까지 왔다면 컴포넌트 버그다.
 *
 * prop은 { error, reset }만 쓴다. Next는 unstable_retry도 넘기지만 이름 그대로
 * 불안정하다.
 */
export default function ConsoleError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="error-state">
      <div className="error-state__title">화면을 그리지 못했습니다</div>
      <p className="error-state__body">
        예상하지 못한 오류가 발생했습니다. 계속 발생하면 개발팀에 알려주세요.
        {error.digest ? ` (${error.digest})` : ""}
      </p>
      <div className="error-state__actions">
        <button type="button" className="btn btn-primary" onClick={reset}>
          다시 시도
        </button>
      </div>
    </div>
  );
}
