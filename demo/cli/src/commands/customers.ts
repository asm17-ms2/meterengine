import type { Command } from "commander";
import { MeterEngineApiError } from "@meterengine/sdk";
import type { ArchiveStatus, CustomerResponse, MeterEngineClient } from "@meterengine/sdk";
import type { CliDeps } from "../context.ts";
import { clientFor, printJson } from "../context.ts";
import { UsageError } from "../exit-code.ts";
import { STATUS_OPTION_DESCRIPTION, UUID_PATTERN, parseArchiveStatus, requireArgument } from "../options.ts";

// 서버의 external_id 길이 상한이다. 넘으면 외부 ID로 찾아볼 필요가 없다.
const EXTERNAL_ID_MAX_LENGTH = 255;

export function registerCustomers(program: Command, deps: CliDeps): void {
  const customers = program.command("customers").description("고객");
  customers
    .command("list")
    .description("고객 목록을 조회한다. --external-id로 외부 ID의 고객 UUID를 찾는다 (GET /v1/customers)")
    .option("--external-id <id>", "도입사 시스템의 고객 ID. 그 고객만 낸다")
    .option("--status <status>", STATUS_OPTION_DESCRIPTION, parseArchiveStatus)
    .action(async (options: { externalId?: string; status?: ArchiveStatus }, command: Command) => {
      if (options.externalId !== undefined) requireArgument(options.externalId, "--external-id");
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await client.listCustomers({ external_id: options.externalId, status: options.status }));
    });
  customers
    .command("get")
    .description("고객 한 명을 조회한다. 외부 ID를 넣으면 그 고객의 UUID를 오류 메시지로 알려 준다 (GET /v1/customers/{id})")
    .argument("<id>", "고객 UUID. 외부 ID로 찾으려면 customers list --external-id")
    .action(async (id: string, _options: unknown, command: Command) => {
      requireArgument(id, "고객 id");
      const client = await clientFor(deps, command);
      printJson(deps.stdout, await getCustomer(client, id));
    });
}

async function getCustomer(client: MeterEngineClient, id: string): Promise<CustomerResponse> {
  // UUID가 아니면 서버도 400으로 거절한다. '/'가 든 값은 오류 본문 없이 거절되므로 보내기 전에 외부 ID로 찾는다.
  if (!UUID_PATTERN.test(id)) {
    const match = await findByExternalId(client, id);
    if (match === undefined) {
      throw new UsageError("invalid_customer_id", `"${id}"는 고객 UUID가 아니고 이 외부 ID의 고객도 없습니다. 고객 UUID는 customers list로 찾습니다`);
    }
    throw externalIdInsteadOfId(id, match);
  }
  try {
    return await client.getCustomer(id);
  } catch (error) {
    if (!(error instanceof MeterEngineApiError && error.status === 404 && error.code === "customer_not_found")) throw error;
    // UUID 모양의 외부 ID일 수 있다. 찾기가 실패하면 원래 오류를 그대로 낸다.
    const match = await findByExternalId(client, id).catch(() => undefined);
    if (match === undefined) throw error;
    throw externalIdInsteadOfId(id, match);
  }
}

function externalIdInsteadOfId(id: string, match: CustomerResponse): UsageError {
  const archived = match.archived_at === null || match.archived_at === undefined ? "" : " 보관한 고객입니다.";
  return new UsageError(
    "external_id_instead_of_id",
    `"${id}"는 고객 UUID가 아니라 외부 ID입니다. 이 고객의 UUID는 ${match.id}입니다.${archived} 이벤트의 customer_id에도 UUID를 넣습니다`,
  );
}

// 서버가 외부 ID로 받을 수 없는 값(400)이면 그런 고객이 없는 것이다. 인증 실패나 서버 오류는 그대로 낸다.
async function findByExternalId(client: MeterEngineClient, externalId: string): Promise<CustomerResponse | undefined> {
  if (externalId.length > EXTERNAL_ID_MAX_LENGTH) return undefined;
  try {
    return (await client.listCustomers({ external_id: externalId, status: "all" })).customers[0];
  } catch (error) {
    if (error instanceof MeterEngineApiError && error.status === 400) return undefined;
    throw error;
  }
}
