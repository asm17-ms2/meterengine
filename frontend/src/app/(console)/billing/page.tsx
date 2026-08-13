import { NotImplemented } from "@/components/screen/NotImplemented";

export default function BillingPage() {
  return (
    <NotImplemented
      title="청구 예정액"
      issue="MS2-127"
      reason="청구 예정액 조회 API(MS2-124)가 먼저 필요하다. 단가는 어떤 응답에도 없고, 금액은 화면에서 계산하지 않는다."
    />
  );
}
