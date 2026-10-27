import { connection } from "next/server";

import { BridgeConfigForm } from "@/components/BridgeConfigForm";
import { bridgeErrorText, getBridgeConfig, isRecord } from "@/lib/bridge-client";
import { toBridgeFields } from "@/lib/bridge-form";

const CONFIG_ENDPOINT_MISSING =
  "이 브리지에는 설정 조회가 없습니다. 설정 엔드포인트가 있는 코드로 브리지를 다시 띄우세요(launchd면 python3 demo/otel_bridge.py stop 뒤 start).";

export default async function BridgeConfigPage() {
  await connection();
  const result = await getBridgeConfig();
  if (result.ok && result.status === 404) {
    return <BridgeConfigForm fields={[]} loadError={CONFIG_ENDPOINT_MISSING} />;
  }
  if (!result.ok || result.status !== 200 || !isRecord(result.body)) {
    return <BridgeConfigForm fields={[]} loadError={bridgeErrorText(result)} />;
  }
  return <BridgeConfigForm fields={toBridgeFields(result.body)} />;
}
