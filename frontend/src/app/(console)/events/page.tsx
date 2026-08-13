import { NotImplemented } from "@/components/screen/NotImplemented";

export default function EventsPage() {
  return (
    <NotImplemented
      title="이벤트 로그"
      issue="MS2-134"
      reason="이벤트 목록 API(GET /v1/events, MS2-131)가 아직 없어서, 계약을 먼저 정의하고 목으로 화면을 만든다."
    />
  );
}
