export function DemoBanner() {
  return <div className="demo-banner" role="note" aria-label="데모 안내">
    <span className="demo-banner-tag">데모</span>
    <p>MeterEngine을 붙인 연동 예제 서비스예요. 사용량과 청구는 MeterEngine에 실제로 기록하고, 응답은 실제 모델 대신 로컬 모형이 만들어요.</p>
  </div>;
}
