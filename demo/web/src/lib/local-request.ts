import "server-only";

import { headers } from "next/headers";

import { isLocalHost } from "@/lib/local-host";

export async function assertLocalRequest(): Promise<void> {
  if (!isLocalHost((await headers()).get("host"))) {
    throw new Error("로컬 주소로 연 화면에서만 실행할 수 있습니다");
  }
}
