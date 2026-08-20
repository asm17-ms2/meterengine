"use client";

import { useEffect, useRef } from "react";

/**
 * 화면 가운데를 덮는 모달. 고객 화면의 등록/수정 폼과 삭제 확인이 쓴다.
 *
 * EventDetailDrawer와 나눠 둔 이유: 드로어는 오른쪽에서 밀려 나와 목록을 옆에
 * 두고 읽는 자리이고, 이쪽은 답을 하기 전에는 뒤로 못 가는 자리다. 스타일
 * (.drawer vs .dialog)도 디자인 시스템에서 갈라져 있다.
 *
 * 백드롭 클릭과 Escape로 닫는다. 저장 중에는 둘 다 막아야 해서 onClose를
 * 호출부가 조건부로 넘긴다 (undefined면 닫히지 않는다).
 */
export function Dialog({
  labelledBy,
  onClose,
  action,
  className,
  style,
  children,
}: {
  /** 제목 요소의 id. 스크린리더가 이 모달을 뭐라고 읽을지 정한다. */
  labelledBy: string;
  /** undefined면 백드롭과 Escape로 닫히지 않는다 (저장 중). */
  onClose?: () => void;
  /**
   * 있으면 패널 자체가 <form>이 된다. 안에 <form>을 따로 두지 않는 이유:
   * .dialog가 flex 컨테이너라 자식 사이 간격(gap)을 직접 만든다. 그 안에 폼을
   * 한 겹 끼우면 자식이 폼 하나뿐이 되어 간격이 전부 사라진다.
   */
  action?: (formData: FormData) => void;
  className?: string;
  style?: React.CSSProperties;
  children: React.ReactNode;
}) {
  const panelRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!onClose) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onClose?.();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  useEffect(() => {
    // 열자마자 포커스를 패널 안으로 옮긴다. 그러지 않으면 방금 누른 표의 버튼에
    // 포커스가 남아 Tab이 모달이 아니라 뒤쪽 목록을 훑는다.
    // autoFocus를 단 요소가 있으면 그쪽이 이미 가져갔으므로 건드리지 않는다.
    const panel = panelRef.current;
    if (!panel) return;
    if (panel.contains(document.activeElement)) return;
    panel.focus();
  }, []);

  const panelProps = {
    ref: (el: HTMLElement | null) => {
      panelRef.current = el;
    },
    className: className ? `dialog elev-lg ${className}` : "dialog elev-lg",
    style,
    role: "dialog",
    "aria-modal": true,
    tabIndex: -1,
    "aria-labelledby": labelledBy,
    // 패널 안을 눌렀을 때 백드롭까지 올라가 닫히는 것을 막는다.
    onClick: (event: React.MouseEvent) => event.stopPropagation(),
  };

  return (
    <div
      className="dialog-backdrop"
      onClick={onClose ? () => onClose() : undefined}
    >
      {action ? (
        <form {...panelProps} action={action}>
          {children}
        </form>
      ) : (
        <div {...panelProps}>{children}</div>
      )}
    </div>
  );
}
