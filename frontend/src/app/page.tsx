import { redirect } from "next/navigation";

/** 콘솔의 첫 화면은 사용량 집계다. */
export default function Home() {
  redirect("/usage");
}
