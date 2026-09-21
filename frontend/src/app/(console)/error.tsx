"use client";

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
