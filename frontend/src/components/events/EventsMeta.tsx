import type { Result } from "@/lib/api/client";
import type { ListEventsResponse } from "@/lib/api/events";
import { formatDecimal } from "@/lib/format";

/** 화면 제목 오른쪽 메타. 같은 프라미스를 보되 Suspense 경계가 따로다. */
export async function EventsMeta({
  events,
}: {
  events: Promise<Result<ListEventsResponse>>;
}) {
  const result = await events;
  if (!result.ok) return null;

  return (
    <>
      총 <b>{formatDecimal(result.data.total)}</b>건, 이 페이지{" "}
      <b>{result.data.events.length}</b>줄
    </>
  );
}
