import { Skeleton } from "antd";

export default function Loading() {
  return <section aria-busy="true" aria-label="고객 사용량과 청구 내역을 불러오는 중"><Skeleton active paragraph={{ rows: 8 }} /></section>;
}
