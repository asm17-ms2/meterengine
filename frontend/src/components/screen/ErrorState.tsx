import Link from "next/link";

import { RetryButton } from "@/components/screen/RetryButton";
import { type ApiError, toDisplayMessage } from "@/lib/api/client";

export function ErrorState({
  title,
  error,
  narrowerHref,
}: {
  title: string;
  error: ApiError;
  narrowerHref?: string;
}) {
  const bodyMessage = toDisplayMessage(error);
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
