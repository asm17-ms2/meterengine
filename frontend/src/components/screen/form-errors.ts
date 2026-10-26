"use client";

import type { FormInstance } from "antd";

export function clearFieldErrors(form: FormInstance) {
  form.setFields(form.getFieldsError().map(({ name }) => ({ name, errors: [] })));
}
