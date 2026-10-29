const SERVICE_ERROR_BRAND = Symbol.for("meterengine.lumen.service-error.v1");

export type ServiceErrorDetails = {
  status: number;
  code: string;
  message: string;
  isRetryable: boolean;
};

export class ServiceError extends Error {
  readonly [SERVICE_ERROR_BRAND] = true;
  readonly status: number;
  readonly code: string;
  readonly isRetryable: boolean;

  constructor(status: number, code: string, message: string, isRetryable = false) {
    super(message);
    this.status = status;
    this.code = code;
    this.isRetryable = isRetryable;
  }
}

export function isServiceError(error: unknown): error is ServiceErrorDetails {
  if (typeof error !== "object" || error === null) return false;
  try {
    const ownValue = (key: PropertyKey): unknown => Object.getOwnPropertyDescriptor(error, key)?.value;
    const status = ownValue("status");
    const code = ownValue("code");
    const message = ownValue("message");
    return ownValue(SERVICE_ERROR_BRAND) === true
      && typeof status === "number" && Number.isInteger(status) && status >= 400 && status <= 599
      && typeof code === "string" && /^[a-z][a-z_]{0,63}$/.test(code)
      && typeof message === "string" && message.trim().length > 0 && message.length <= 1000
      && typeof ownValue("isRetryable") === "boolean";
  } catch {
    return false;
  }
}
