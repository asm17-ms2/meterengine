import { ScreenHeader } from "@/components/screen/ScreenHeader";

/**
 * 아직 만들지 않은 화면. 셸의 내비게이션이 404로 떨어지지 않게 자리를 채운다.
 * 각 화면이 구현되면 이 자리표시자를 지운다.
 */
export function NotImplemented({
  title,
  issue,
  reason,
}: {
  title: string;
  /** 이 화면을 만드는 Jira 이슈 키 */
  issue: string;
  reason: string;
}) {
  return (
    <>
      <ScreenHeader title={title} />
      <div className="empty-state">
        <div className="empty-state__title">아직 만들지 않았습니다</div>
        <p className="empty-state__body">
          {issue}에서 만든다. {reason}
        </p>
      </div>
    </>
  );
}
