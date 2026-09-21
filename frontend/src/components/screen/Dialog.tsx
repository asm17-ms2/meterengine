"use client";

import { useEffect, useRef } from "react";

export function Dialog({
  labelledBy,
  onClose,
  action,
  className,
  style,
  children,
}: {
  labelledBy: string;
  /** undefined면 백드롭과 Escape로 닫히지 않는다 (저장 중). */
  onClose?: () => void;
  action?: (formData: FormData) => void;
  className?: string;
  style?: React.CSSProperties;
  children: React.ReactNode;
}) {
  const panelRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!onClose) return;
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onClose?.();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  useEffect(() => {
    const panel = panelRef.current;
    if (!panel) return;
    if (panel.contains(document.activeElement)) return;
    panel.focus();
  }, []);

  const panelProps = {
    ref: (element: HTMLElement | null) => {
      panelRef.current = element;
    },
    className: className ? `dialog elev-lg ${className}` : "dialog elev-lg",
    style,
    role: "dialog",
    "aria-modal": true,
    tabIndex: -1,
    "aria-labelledby": labelledBy,
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
