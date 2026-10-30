import { createHash, randomBytes, randomUUID } from "node:crypto";
import { chmodSync, mkdirSync } from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";
import type { IngestEventRequest } from "@meterengine/sdk";
import type { ServiceAccount } from "./config.ts";
import type { GenerationView } from "./contracts.ts";
import { ServiceError } from "./service-error.ts";

type GenerationRow = {
  account_id: string;
  id: string;
  request_hash: string;
  model_id: string;
  model_name: string;
  prompt: string;
  output: string;
  input_tokens: number;
  output_tokens: number;
  created_at: string;
  state: GenerationView["state"];
  lease_token: string | null;
  lease_until: number | null;
  metering_status: GenerationView["meteringStatus"];
};

export type GenerationClaim = { generation: GenerationView; leaseToken: string | null };
export type OutboxClaim = { transactionId: string; accountId: string; generationId: string; leaseToken: string; event: IngestEventRequest };

const GENERATION_LEASE_MS = 60000;
const DELIVERY_LEASE_MS = 30000;
const SESSION_AGE_MS = 8 * 60 * 60 * 1000;

function tokenHash(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}

function generationView(row: GenerationRow): GenerationView {
  return {
    id: row.id,
    modelId: row.model_id,
    modelName: row.model_name,
    prompt: row.prompt,
    output: row.output,
    inputTokens: row.input_tokens,
    outputTokens: row.output_tokens,
    totalTokens: row.input_tokens + row.output_tokens,
    createdAt: row.created_at,
    state: row.state,
    meteringStatus: row.metering_status,
  };
}

export class LumenStore {
  private readonly db: DatabaseSync;

  constructor(databasePath: string, accounts: ServiceAccount[] = []) {
    if (databasePath !== ":memory:") mkdirSync(path.dirname(databasePath), { recursive: true, mode: 0o700 });
    this.db = new DatabaseSync(databasePath, { timeout: 5000 });
    if (databasePath !== ":memory:") chmodSync(databasePath, 0o600);
    this.db.exec(`
      PRAGMA journal_mode = WAL;
      CREATE TABLE IF NOT EXISTS account_binding (id TEXT PRIMARY KEY, customer_id TEXT NOT NULL UNIQUE) STRICT;
      CREATE TABLE IF NOT EXISTS source_binding (id INTEGER PRIMARY KEY CHECK(id=1), source_hash TEXT NOT NULL) STRICT;
      CREATE TABLE IF NOT EXISTS session (token_hash TEXT PRIMARY KEY, account_id TEXT NOT NULL, expires_at INTEGER NOT NULL) STRICT;
      CREATE TABLE IF NOT EXISTS generation (
        account_id TEXT NOT NULL, id TEXT NOT NULL, request_hash TEXT NOT NULL,
        model_id TEXT NOT NULL, model_name TEXT NOT NULL, prompt TEXT NOT NULL, output TEXT NOT NULL DEFAULT '',
        input_tokens INTEGER NOT NULL DEFAULT 0, output_tokens INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('processing','completed','failed')),
        lease_token TEXT, lease_until INTEGER,
        PRIMARY KEY(account_id,id)
      ) STRICT;
      CREATE TABLE IF NOT EXISTS metering_outbox (
        transaction_id TEXT PRIMARY KEY, account_id TEXT NOT NULL, generation_id TEXT NOT NULL,
        event_json TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('pending','delivering','delivered','failed')),
        attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at INTEGER NOT NULL DEFAULT 0,
        lease_token TEXT, lease_until INTEGER,
        UNIQUE(account_id,generation_id),
        FOREIGN KEY(account_id,generation_id) REFERENCES generation(account_id,id)
      ) STRICT;
      CREATE INDEX IF NOT EXISTS generation_account_created ON generation(account_id,created_at DESC,id);
      CREATE INDEX IF NOT EXISTS metering_due ON metering_outbox(status,next_attempt_at,lease_until);
    `);
    try {
      this.transaction(() => {
        for (const account of accounts) {
          const existing = this.db.prepare("SELECT customer_id FROM account_binding WHERE id=?").get(account.id) as { customer_id: string } | undefined;
          if (existing && existing.customer_id !== account.customerId) throw new Error("기존 시연 계정의 고객 연결을 바꿀 수 없습니다. 별도 데이터 파일을 사용하세요.");
          this.db.prepare("INSERT INTO account_binding(id,customer_id) VALUES (?,?) ON CONFLICT(id) DO NOTHING").run(account.id, account.customerId);
        }
      });
    } catch (error) {
      this.db.close();
      throw error;
    }
  }

  close(): void { this.db.close(); }

  bindSource(source: string): void {
    this.transaction(() => {
      const hash = tokenHash(source);
      const existing = this.db.prepare("SELECT source_hash FROM source_binding WHERE id=1").get() as { source_hash: string } | undefined;
      if (existing && existing.source_hash !== hash) throw new Error("저장된 요청의 MeterEngine 연결 설정이 다릅니다. 기존 데이터 파일을 보존하고 별도 데이터 파일을 사용하세요.");
      if (!existing) {
        const populated = this.db.prepare("SELECT EXISTS(SELECT 1 FROM generation) OR EXISTS(SELECT 1 FROM metering_outbox) AS populated").get() as { populated: number };
        if (populated.populated) throw new Error("기존 요청의 MeterEngine 연결 설정을 확인할 수 없습니다. 기존 데이터 파일을 보존하고 별도 데이터 파일을 사용하세요.");
        this.db.prepare("INSERT INTO source_binding(id,source_hash) VALUES (1,?)").run(hash);
      }
    });
  }

  private transaction<T>(work: () => T): T {
    this.db.exec("BEGIN IMMEDIATE");
    try {
      const result = work();
      this.db.exec("COMMIT");
      return result;
    } catch (error) {
      this.db.exec("ROLLBACK");
      throw error;
    }
  }

  createSession(accountId: string, now: number): string {
    const token = randomBytes(32).toString("base64url");
    this.db.prepare("DELETE FROM session WHERE expires_at<=?").run(now);
    this.db.prepare("INSERT INTO session(token_hash,account_id,expires_at) VALUES (?,?,?)").run(tokenHash(token), accountId, now + SESSION_AGE_MS);
    return token;
  }

  sessionAccount(token: string | undefined, now: number): string | null {
    if (!token || !/^[A-Za-z0-9_-]{43}$/.test(token)) return null;
    const result = this.db.prepare("SELECT account_id FROM session WHERE token_hash=? AND expires_at>?").get(tokenHash(token), now) as { account_id: string } | undefined;
    return result?.account_id ?? null;
  }

  revokeSession(token: string | undefined): void {
    if (token) this.db.prepare("DELETE FROM session WHERE token_hash=?").run(tokenHash(token));
  }

  private generationRow(accountId: string, id: string): GenerationRow | undefined {
    return this.db.prepare(`SELECT g.*, CASE o.status WHEN 'delivered' THEN 'delivered' WHEN 'failed' THEN 'failed' ELSE 'pending' END AS metering_status
      FROM generation g LEFT JOIN metering_outbox o ON o.account_id=g.account_id AND o.generation_id=g.id
      WHERE g.account_id=? AND g.id=?`).get(accountId, id) as GenerationRow | undefined;
  }

  getGeneration(accountId: string, id: string): GenerationView | null {
    const row = this.generationRow(accountId, id);
    return row ? generationView(row) : null;
  }

  reserveGeneration(accountId: string, id: string, modelId: string, modelName: string, prompt: string, now: number): GenerationClaim {
    const requestHash = tokenHash(JSON.stringify({ modelId, prompt }));
    return this.transaction(() => {
      const existing = this.generationRow(accountId, id);
      if (existing && existing.request_hash !== requestHash) throw new ServiceError(409, "request_conflict", "같은 요청 번호로 다른 내용을 보낼 수 없습니다. 새 요청으로 시작하세요.");
      if (existing && (existing.state === "completed" || (existing.state === "processing" && (existing.lease_until ?? 0) > now))) {
        return { generation: generationView(existing), leaseToken: null };
      }
      const leaseToken = randomUUID();
      if (existing) {
        this.db.prepare("UPDATE generation SET state='processing',lease_token=?,lease_until=? WHERE account_id=? AND id=?").run(leaseToken, now + GENERATION_LEASE_MS, accountId, id);
      } else {
        this.db.prepare("INSERT INTO generation(account_id,id,request_hash,model_id,model_name,prompt,created_at,state,lease_token,lease_until) VALUES (?,?,?,?,?,?,?,'processing',?,?)")
          .run(accountId, id, requestHash, modelId, modelName, prompt, new Date(now).toISOString(), leaseToken, now + GENERATION_LEASE_MS);
      }
      return { generation: generationView(this.generationRow(accountId, id)!), leaseToken };
    });
  }

  completeGeneration(accountId: string, id: string, leaseToken: string, output: string, inputTokens: number, outputTokens: number, event: IngestEventRequest): void {
    this.transaction(() => {
      const result = this.db.prepare("UPDATE generation SET state='completed',output=?,input_tokens=?,output_tokens=?,lease_token=NULL,lease_until=NULL WHERE account_id=? AND id=? AND state='processing' AND lease_token=?")
        .run(output, inputTokens, outputTokens, accountId, id, leaseToken);
      if (result.changes === 0) return;
      this.db.prepare("INSERT INTO metering_outbox(transaction_id,account_id,generation_id,event_json,status) VALUES (?,?,?,?,'pending')")
        .run(event.transaction_id, accountId, id, JSON.stringify(event));
    });
  }

  failGeneration(accountId: string, id: string, leaseToken: string): void {
    this.db.prepare("UPDATE generation SET state='failed',lease_token=NULL,lease_until=NULL WHERE account_id=? AND id=? AND state='processing' AND lease_token=?").run(accountId, id, leaseToken);
  }

  listGenerations(accountId: string, start?: string, end?: string): GenerationView[] {
    const rows = this.db.prepare(`SELECT g.*, CASE o.status WHEN 'delivered' THEN 'delivered' WHEN 'failed' THEN 'failed' ELSE 'pending' END AS metering_status
      FROM generation g LEFT JOIN metering_outbox o ON o.account_id=g.account_id AND o.generation_id=g.id
      WHERE g.account_id=? ${start && end ? "AND g.created_at>=? AND g.created_at<?" : ""} ORDER BY g.created_at DESC,g.id DESC LIMIT 100`).all(...(start && end ? [accountId, start, end] : [accountId])) as GenerationRow[];
    return rows.map(generationView);
  }

  stats(accountId: string, start: string, end: string): { totalRequests: number; inputTokens: number; outputTokens: number; pendingMetering: number; failedMetering: number } {
    const row = this.db.prepare(`SELECT COUNT(*) AS total_requests, COALESCE(SUM(g.input_tokens),0) AS input_tokens,COALESCE(SUM(g.output_tokens),0) AS output_tokens,
      COALESCE(SUM(CASE WHEN o.status IN ('pending','delivering') THEN 1 ELSE 0 END),0) AS pending_metering,
      COALESCE(SUM(CASE WHEN o.status='failed' THEN 1 ELSE 0 END),0) AS failed_metering
      FROM generation g LEFT JOIN metering_outbox o ON o.account_id=g.account_id AND o.generation_id=g.id
      WHERE g.account_id=? AND g.created_at>=? AND g.created_at<? AND g.state='completed'`).get(accountId, start, end) as Record<string, number>;
    return { totalRequests: row.total_requests, inputTokens: row.input_tokens, outputTokens: row.output_tokens, pendingMetering: row.pending_metering, failedMetering: row.failed_metering };
  }

  claimOutbox(now: number, options: { accountId?: string; generationId?: string; force?: boolean } = {}): OutboxClaim | null {
    return this.transaction(() => {
      const conditions = ["(status IN ('pending','failed') OR (status='delivering' AND lease_until<=?))"];
      const values: (string | number)[] = [now];
      if (!options.force) { conditions.push("next_attempt_at<=?"); values.push(now); }
      if (options.accountId) { conditions.push("account_id=?"); values.push(options.accountId); }
      if (options.generationId) { conditions.push("generation_id=?"); values.push(options.generationId); }
      const row = this.db.prepare(`SELECT transaction_id,account_id,generation_id,event_json FROM metering_outbox WHERE ${conditions.join(" AND ")} ORDER BY next_attempt_at,transaction_id LIMIT 1`).get(...values) as { transaction_id: string; account_id: string; generation_id: string; event_json: string } | undefined;
      if (!row) return null;
      const leaseToken = randomUUID();
      this.db.prepare("UPDATE metering_outbox SET status='delivering',lease_token=?,lease_until=?,attempts=attempts+1 WHERE transaction_id=?").run(leaseToken, now + DELIVERY_LEASE_MS, row.transaction_id);
      return { transactionId: row.transaction_id, accountId: row.account_id, generationId: row.generation_id, leaseToken, event: JSON.parse(row.event_json) as IngestEventRequest };
    });
  }

  finishOutbox(claim: OutboxClaim, now: number, isDelivered: boolean, retryAt = now + 5000): void {
    this.db.prepare("UPDATE metering_outbox SET status=?,lease_token=NULL,lease_until=NULL,next_attempt_at=? WHERE transaction_id=? AND lease_token=? AND status='delivering'")
      .run(isDelivered ? "delivered" : "failed", isDelivered ? 0 : retryAt, claim.transactionId, claim.leaseToken);
  }

  makeOutboxDue(accountId: string, generationId?: string): void {
    const filter = generationId ? " AND generation_id=?" : "";
    this.db.prepare(`UPDATE metering_outbox SET next_attempt_at=0 WHERE account_id=? AND status IN ('pending','failed')${filter}`)
      .run(...(generationId ? [accountId, generationId] : [accountId]));
  }

  outboxCounts(accountId: string): { delivered: number; pending: number; failed: number } {
    const rows = this.db.prepare("SELECT status,COUNT(*) AS amount FROM metering_outbox WHERE account_id=? GROUP BY status").all(accountId) as Array<{ status: string; amount: number }>;
    const result = { delivered: 0, pending: 0, failed: 0 };
    for (const row of rows) {
      if (row.status === "delivered") result.delivered += row.amount;
      else if (row.status === "failed") result.failed += row.amount;
      else result.pending += row.amount;
    }
    return result;
  }
}
