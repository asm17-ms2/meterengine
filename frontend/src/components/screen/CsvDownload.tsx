"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { Alert, Button } from "antd";
import { useEffect, useRef, useState } from "react";

import { useTenantAccess } from "@/components/auth/TenantAccess";
import { filenameFromDisposition } from "@/lib/export-filename";
import { csvDownloadParameters } from "@/lib/resource-query";
import type { SearchResource } from "@/components/screen/ResourceSearchControls";

export function CsvDownload({ resource }: { resource: SearchResource | "events" }) {
  const search = useWorkspaceSearch().toString();
  const organizationId = useTenantAccess()?.organizationId;
  const [isLoading, setIsLoading] = useState(false);
  const [isIncludingProperties, setIsIncludingProperties] = useState(false);
  const [error, setError] = useState("");
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), [search, resource]);

  async function download() {
    if (!organizationId) { setError("도입사를 확인한 뒤 다시 내보내세요."); return; }
    active.current?.abort();
    const controller = new AbortController();
    active.current = controller;
    setIsLoading(true);
    setError("");
    try {
      const query = csvDownloadParameters(resource, new URLSearchParams(search), organizationId);
      if (resource === "events") query.set("include_properties", String(isIncludingProperties));
      const response = await fetch(`/exports/${resource}${query.size ? `?${query}` : ""}`, { cache: "no-store", credentials: "same-origin", signal: controller.signal });
      if (!response.ok) {
        const result: unknown = await response.json().catch(() => null);
        throw new Error(result && typeof result === "object" && "message" in result && typeof result.message === "string" ? result.message : "내보내지 못했습니다. 조건을 좁히거나 다시 시도하세요.");
      }
      if (!response.headers.get("Content-Type")?.includes("text/csv")) throw new Error("세션과 업무 환경을 확인한 뒤 다시 내보내세요.");
      const file = await response.blob();
      if (controller.signal.aborted) return;
      const url = URL.createObjectURL(file);
      const link = document.createElement("a");
      link.href = url;
      link.download = filenameFromDisposition(response.headers.get("Content-Disposition"), resource);
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : "내보내지 못했습니다. 다시 시도하세요.");
    } finally {
      if (active.current === controller) setIsLoading(false);
    }
  }

  return <div className="workspace-field">{resource === "events" && <label style={{ display: "flex", alignItems: "center", gap: 8, minHeight: 44 }}><input type="checkbox" checked={isIncludingProperties} onChange={event => setIsIncludingProperties(event.target.checked)} style={{ width: 18, height: 18, minHeight: 18, margin: 0, accentColor: "#5746ad" }} />원본 속성 포함 (JSON)</label>}<Button loading={isLoading} onClick={download} style={{ alignSelf: "flex-start" }}>조건 전체 CSV 내보내기</Button>{error && <Alert type="error" showIcon title={error} />}</div>;
}
