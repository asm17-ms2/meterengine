import Link from "next/link";

// 없는 주소의 화면. Next 기본 404 화면은 영어이고 본문 영역(#main-content)이 없어 따로 둔다.
export default function NotFound() {
  return <main id="main-content" className="not-found">
    <h1>페이지를 찾을 수 없어요</h1>
    <p>주소가 바뀌었거나 없는 페이지예요.</p>
    <Link href="/">처음 화면으로 가기</Link>
  </main>;
}
