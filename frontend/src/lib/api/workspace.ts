import "server-only";
import { serverFetch } from "@/lib/api/client";
import { config } from "@/lib/config";
export function workspaceFetch<T>(path: string, params?: Record<string, string | undefined>) { return serverFetch<T>(config.apiBaseUrl, path, params); }
