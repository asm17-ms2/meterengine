import { AuditScreen } from "@/components/workspace/AuditScreen";

export default async function AuditPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  return <AuditScreen query={await searchParams} />;
}
