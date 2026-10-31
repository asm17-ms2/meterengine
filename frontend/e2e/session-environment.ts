import { requiresWorkspaceEnvironment } from "../src/lib/api/environment-scope";

export function sessionHeadersFor(path: string, headers: Record<string, string>): Record<string, string> {
  return requiresWorkspaceEnvironment(new URL(path, "http://fixture.local").pathname)
    ? { ...headers, "X-MeterEngine-Environment": "test" }
    : headers;
}
