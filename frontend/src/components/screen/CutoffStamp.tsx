import { QueryStamp } from "@/components/screen/QueryStamp";
import { formatKstShortStamp } from "@/lib/format";
import { cutoffOf } from "@/lib/month";

export function CutoffStamp({ month, now }: { month: string; now: Date }) {
  const cutoff = cutoffOf(month);
  return cutoff <= now ? (
    <QueryStamp label="집계 기준" text={`${formatKstShortStamp(cutoff)}까지 받은 사용량`} />
  ) : (
    <QueryStamp label="집계 마감" text={`${formatKstShortStamp(cutoff)} 예정`} />
  );
}
