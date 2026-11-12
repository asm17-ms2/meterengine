"use client";

import { Button, Result } from "antd";

const ERROR_BODY = "예상하지 못한 오류가 났어요. 다시 시도해 주세요.";

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
      title="화면을 표시하지 못했어요"
      subTitle={`${ERROR_BODY}${error.digest ? ` 오류 코드: ${error.digest}` : ""}`}
      extra={
        <Button type="primary" onClick={reset}>
          다시 시도
        </Button>
      }
    />
  );
}
