import { ErrorAlert } from "@/components/screen/ErrorAlert";
import { type ApiError, toDisplayMessage } from "@/lib/api/client";

export function ErrorState({
  title,
  error,
  narrowerHref,
  resetHref,
}: {
  title: string;
  error: ApiError;
  narrowerHref?: string;
  resetHref?: string;
}) {
  const statusSuffix = error.status > 0 ? ` (${error.status})` : "";

  return (
    <ErrorAlert
      title={title}
      message={`${toDisplayMessage(error)}${statusSuffix}`}
      narrowerHref={narrowerHref}
      resetHref={resetHref}
    />
  );
}
