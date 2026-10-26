"use client";

const VISUALLY_HIDDEN_STYLE: React.CSSProperties = {
  position: "absolute",
  width: 1,
  height: 1,
  padding: 0,
  margin: -1,
  overflow: "hidden",
  clip: "rect(0, 0, 0, 0)",
  whiteSpace: "nowrap",
  border: 0,
};

// 입력에서 Enter를 누르면 폼을 한 번 제출하는 숨은 제출 버튼.
export function EnterSubmitButton({ disabled = false }: { disabled?: boolean }) {
  return (
    <button
      type="submit"
      tabIndex={-1}
      aria-hidden
      disabled={disabled}
      style={VISUALLY_HIDDEN_STYLE}
    />
  );
}
