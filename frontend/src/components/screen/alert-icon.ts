"use client";

import {
  CheckCircleFilled,
  CloseCircleFilled,
  ExclamationCircleFilled,
  InfoCircleFilled,
} from "@ant-design/icons";
import { createElement } from "react";

type AlertType = "success" | "info" | "warning" | "error";

const ALERT_ICONS = {
  success: CheckCircleFilled,
  info: InfoCircleFilled,
  warning: ExclamationCircleFilled,
  error: CloseCircleFilled,
} as const satisfies Record<AlertType, unknown>;

export function alertIcon(type: AlertType): React.ReactNode {
  return createElement(ALERT_ICONS[type], { "aria-hidden": true, "aria-label": undefined });
}
