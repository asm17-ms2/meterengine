import { NotImplemented } from "@/components/screen/NotImplemented";

export default function UsagePage() {
  return (
    <NotImplemented
      title="사용량 집계"
      issue="MS2-136"
      reason="GET /v1/usage로 고객별 미터 라인을 보여준다."
    />
  );
}
