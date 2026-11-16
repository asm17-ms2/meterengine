import type { Command } from "commander";
import type { ArchiveStatus } from "@meterengine/sdk";
import type { CliDeps } from "../context.ts";
import { clientFor, printJson } from "../context.ts";
import { STATUS_OPTION_DESCRIPTION, parseArchiveStatus, requireArgument } from "../options.ts";

export function registerMeters(program: Command, deps: CliDeps): void {
  const meters = program.command("meters").description("미터. 이벤트의 type이 미터의 event_type과 글자까지 같아야 청구에 잡힌다");
  meters
    .command("list")
    .description("미터 목록을 조회한다 (GET /v1/billable-metrics)")
    .option("--status <status>", STATUS_OPTION_DESCRIPTION, parseArchiveStatus)
    .action(async (options: { status?: ArchiveStatus }, command: Command) => {
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.listBillableMetrics({ status: options.status }));
    });
  meters
    .command("get")
    .description("미터 하나의 이벤트 유형, 집계 방식, 수량 속성, 차원을 조회한다 (GET /v1/billable-metrics/{code})")
    .argument("<code>", "미터 code. 목록은 meters list")
    .action(async (code: string, _options: unknown, command: Command) => {
      requireArgument(code, "미터 code");
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.getBillableMetric(code));
    });
}
