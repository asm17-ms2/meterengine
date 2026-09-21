import Link from "next/link";

import { RetryButton } from "@/components/screen/RetryButton";
import type { ApiError } from "@/lib/api/client";

function toBodyMessage(error: ApiError): string {
  switch (error.code) {
    case "network_error":
    case "malformed_response":
    case "dev_forced":
      return error.message;
    case "validation_error":
      return error.errors?.length
        ? error.errors.map((fieldError) => fieldError.message).join(" / ")
        : "조회 조건을 확인해주세요.";
    case "unknown_organization":
      return "도입사를 찾을 수 없습니다. 설정을 확인해주세요.";
    case "endpoint_not_found":
      return "요청한 주소를 찾을 수 없습니다.";
    default:
      return "잠시 후 다시 시도해주세요.";
  }
}

export function ErrorState({
  title,
  error,
  narrowerHref,
}: {
  title: string;
  error: ApiError;
  narrowerHref?: string;
}) {
  const bodyMessage = toBodyMessage(error);
  const statusSuffix = error.status > 0 ? ` (${error.status})` : "";

  return (
    <div className="error-state">
      <div className="error-state__title">{title}</div>
      <p className="error-state__body">
        {bodyMessage}
        {statusSuffix}
      </p>
      <div className="error-state__actions">
        <RetryButton />
        {narrowerHref ? (
          <Link className="btn btn-secondary" href={narrowerHref}>
            기간 좁히기
          </Link>
        ) : null}
      </div>
    </div>
  );
}
