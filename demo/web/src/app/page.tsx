import { connection } from "next/server";

import { OverviewPanel, type TargetStatus } from "@/components/OverviewPanel";
import { getBridgeHealth } from "@/lib/bridge-client";
import { describeBridgeHealth } from "@/lib/bridge-health";
import { clientFor, targetNames, toErrorView, type ClientOverrides } from "@/lib/meter-engine";

const OVERVIEW_CLIENT_OVERRIDES: ClientOverrides = { maxRetries: 0, timeoutMs: 3000 };

async function checkTarget(name: string): Promise<TargetStatus> {
  try {
    const response = await clientFor(name, OVERVIEW_CLIENT_OVERRIDES).listCustomers();
    return { name, ok: true, customer_count: response.customers.length };
  } catch (error) {
    return { name, ok: false, error: toErrorView(error) };
  }
}

export default async function OverviewPage() {
  await connection();
  const healthPromise = getBridgeHealth();
  let names: string[];
  try {
    names = targetNames();
  } catch (error) {
    const bridgeMessage = describeBridgeHealth(await healthPromise);
    return <OverviewPanel targets={[]} configError={toErrorView(error).message} bridgeMessage={bridgeMessage} />;
  }
  const [targets, health] = await Promise.all([Promise.all(names.map(checkTarget)), healthPromise]);
  return <OverviewPanel targets={targets} bridgeMessage={describeBridgeHealth(health)} />;
}
