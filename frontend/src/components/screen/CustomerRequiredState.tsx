import { EmptyState } from "@/components/screen/EmptyState";

// 달 전체를 한 번에 계산하지 않도록 고객을 고르기 전에는 조회하지 않는다.
export function CustomerRequiredState({ subject }: { subject: string }) {
  return <EmptyState title="고객을 고르세요" body={`${subject} 고객 한 명씩 조회해요. 조회 조건에서 고객을 고른 뒤 조건 적용을 누르세요.`} />;
}
