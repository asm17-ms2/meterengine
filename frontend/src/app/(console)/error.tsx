"use client";

import { Button, Result } from "antd";

const ERROR_BODY = "예상하지 못한 오류가 발생했습니다. 계속 발생하면 개발팀에 알려주세요.";

export default function ConsoleError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <Result
      status="error"
      title="화면을 그리지 못했습니다"
      subTitle={`${ERROR_BODY}${error.digest ? ` (${error.digest})` : ""}`}
      extra={
        <Button type="primary" onClick={reset}>
          다시 시도
        </Button>
      }
    />
  );
}
