import { createHash, randomBytes, randomUUID } from "node:crypto";
import { chmodSync, mkdirSync } from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";
import type { IngestEventRequest } from "@meterengine/sdk";
import type { SurfaceId } from "./catalog.ts";
import type { ServiceAccount } from "./config.ts";
import type { ConversationSummary, ConversationView, GenerationView, TokenUsage, UsageStats } from "./contracts.ts";
import type { ChatTurn } from "./provider.ts";
import { ServiceError } from "./service-error.ts";

type GenerationRow = {
  account_id: string;
  id: string;
  request_hash: string;
  surface: SurfaceId;
  model_id: string;
  model_name: string;
  conversation_id: string | null;
  turn: number | null;
  batch_id: string | null;
  batch_index: number | null;
  system_prompt: string;
  prompt: string;
  output: string;
  input_tokens: number;
  output_tokens: number;
  cache_read_tokens: number;
  cache_write_tokens: number;
  latency_ms: number;
  created_at: string;
  state: GenerationView["state"];
  lease_token: string | null;
  lease_until: number | null;
  metering_status: GenerationView["meteringStatus"];
};
type ConversationRow = { id: string; title: string; model_id: string; created_at: string; updated_at: string; turns: number };

export type GenerationSpec = {
  id: string;
  surface: SurfaceId;
  modelId: string;
  modelName: string;
  prompt: string;
  system?: string;
  conversationId?: string;
  batchId?: string;
  batchIndex?: number;
};
export type GenerationResult = { output: string; usage: TokenUsage; latencyMs: number };
export type GenerationClaim = { generation: GenerationView; leaseToken: string | null; turn: number | null };
export type OutboxClaim = { transactionId: string; accountId: string; generationId: string; leaseToken: string; event: IngestEventRequest };

const GENERATION_LEASE_MS = 60000;
const DELIVERY_LEASE_MS = 30000;
const SESSION_AGE_MS = 8 * 60 * 60 * 1000;
const WITH_METERING = `SELECT g.*, CASE o.status WHEN 'delivered' THEN 'delivered' WHEN 'failed' THEN 'failed' ELSE 'pending' END AS metering_status
  FROM generation g LEFT JOIN metering_outbox o ON o.account_id=g.account_id AND o.generation_id=g.id`;

function tokenHash(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}

function generationView(row: GenerationRow): GenerationView {
  const usage = { input: row.input_tokens, output: row.output_tokens, cacheRead: row.cache_read_tokens, cacheWrite: row.cache_write_tokens };
  return {
    id: row.id,
    surface: row.surface,
    modelId: row.model_id,
    modelName: row.model_name,
    conversationId: row.conversation_id,
    batchId: row.batch_id,
    system: row.system_prompt,
    prompt: row.prompt,
    output: row.output,
    usage,
    totalTokens: usage.input + usage.output + usage.cacheRead + usage.cacheWrite,
    latencyMs: row.latency_ms,
    createdAt: row.created_at,
    state: row.state,
    meteringStatus: row.metering_status,
  };
}

function conversationSummary(row: ConversationRow): ConversationSummary {
  return { id: row.id, title: row.title, modelId: row.model_id, createdAt: row.created_at, updatedAt: row.updated_at, turns: row.turns };
}

export function conversationTitle(prompt: string): string {
  const line = prompt.replace(/\s+/g, " ").trim();
  return Array.from(line).length > 40 ? `${Array.from(line).slice(0, 40).join("")}...` : line;
}

export class OpenAbStore {
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
      CREATE TABLE IF NOT EXISTS conversation (
        account_id TEXT NOT NULL, id TEXT NOT NULL, title TEXT NOT NULL, model_id TEXT NOT NULL,
        created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
        PRIMARY KEY(account_id,id)
      ) STRICT;
      CREATE TABLE IF NOT EXISTS generation (
        account_id TEXT NOT NULL, id TEXT NOT NULL, request_hash TEXT NOT NULL,
        surface TEXT NOT NULL CHECK(surface IN ('chat','api','batch')),
        model_id TEXT NOT NULL, model_name TEXT NOT NULL,
        conversation_id TEXT, turn INTEGER, batch_id TEXT, batch_index INTEGER,
        system_prompt TEXT NOT NULL DEFAULT '', prompt TEXT NOT NULL, output TEXT NOT NULL DEFAULT '',
        input_tokens INTEGER NOT NULL DEFAULT 0, output_tokens INTEGER NOT NULL DEFAULT 0,
        cache_read_tokens INTEGER NOT NULL DEFAULT 0, cache_write_tokens INTEGER NOT NULL DEFAULT 0, latency_ms INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('processing','completed','failed')),
        lease_token TEXT, lease_until INTEGER,
        PRIMARY KEY(account_id,id),
        FOREIGN KEY(account_id,conversation_id) REFERENCES conversation(account_id,id)
      ) STRICT;
      CREATE TABLE IF NOT EXISTS prompt_cache (account_id TEXT NOT NULL, cache_key TEXT NOT NULL, expires_at INTEGER NOT NULL, PRIMARY KEY(account_id,cache_key)) STRICT;
      CREATE TABLE IF NOT EXISTS metering_outbox (
        transaction_id TEXT PRIMARY KEY, account_id TEXT NOT NULL, generation_id TEXT NOT NULL,
        event_json TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('pending','delivering','delivered','failed')),
        attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at INTEGER NOT NULL DEFAULT 0,
        lease_token TEXT, lease_until INTEGER,
        UNIQUE(account_id,generation_id),
        FOREIGN KEY(account_id,generation_id) REFERENCES generation(account_id,id)
      ) STRICT;
      CREATE INDEX IF NOT EXISTS generation_account_created ON generation(account_id,created_at DESC,id);
      CREATE INDEX IF NOT EXISTS generation_conversation ON generation(account_id,conversation_id,turn);
      CREATE INDEX IF NOT EXISTS generation_batch ON generation(account_id,batch_id,batch_index);
      CREATE INDEX IF NOT EXISTS conversation_updated ON conversation(account_id,updated_at DESC);
      CREATE INDEX IF NOT EXISTS metering_due ON metering_outbox(status,next_attempt_at,lease_until);
    `);
    try {
      this.transaction(() => {
        for (const account of accounts) {
          const existing = this.db.prepare("SELECT customer_id FROM account_binding WHERE id=?").get(account.id) as { customer_id: string } | undefined;
          if (existing && existing.customer_id !== account.customerId) throw new Error("기존 체험 계정의 고객 연결을 바꿀 수 없습니다. 별도 데이터 파일을 사용하세요.");
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
    return this.db.prepare(`${WITH_METERING} WHERE g.account_id=? AND g.id=?`).get(accountId, id) as GenerationRow | undefined;
  }

  getGeneration(accountId: string, id: string): GenerationView | null {
    const row = this.generationRow(accountId, id);
    return row ? generationView(row) : null;
  }

  reserveGeneration(accountId: string, spec: GenerationSpec, now: number): GenerationClaim {
    const system = spec.system ?? "";
    const requestHash = tokenHash(JSON.stringify([spec.surface, spec.modelId, spec.conversationId ?? null, spec.batchId ?? null, spec.batchIndex ?? null, system, spec.prompt]));
    return this.transaction(() => {
      const existing = this.generationRow(accountId, spec.id);
      if (existing && existing.request_hash !== requestHash) throw new ServiceError(409, "request_conflict", "같은 요청 번호로 다른 내용을 보낼 수 없어요. 새 요청으로 보내 주세요.");
      if (existing && (existing.state === "completed" || (existing.state === "processing" && (existing.lease_until ?? 0) > now))) {
        return { generation: generationView(existing), leaseToken: null, turn: existing.turn };
      }
      const leaseToken = randomUUID();
      const createdAt = new Date(now).toISOString();
      if (existing) {
        this.db.prepare("UPDATE generation SET state='processing',lease_token=?,lease_until=? WHERE account_id=? AND id=?").run(leaseToken, now + GENERATION_LEASE_MS, accountId, spec.id);
      } else {
        let turn: number | null = null;
        if (spec.conversationId) {
          this.db.prepare("INSERT INTO conversation(account_id,id,title,model_id,created_at,updated_at) VALUES (?,?,?,?,?,?) ON CONFLICT(account_id,id) DO UPDATE SET model_id=excluded.model_id,updated_at=excluded.updated_at")
            .run(accountId, spec.conversationId, conversationTitle(spec.prompt), spec.modelId, createdAt, createdAt);
          const last = this.db.prepare("SELECT COALESCE(MAX(turn),0) AS turn FROM generation WHERE account_id=? AND conversation_id=?").get(accountId, spec.conversationId) as { turn: number };
          turn = last.turn + 1;
        }
        this.db.prepare(`INSERT INTO generation(account_id,id,request_hash,surface,model_id,model_name,conversation_id,turn,batch_id,batch_index,system_prompt,prompt,created_at,state,lease_token,lease_until)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'processing',?,?)`)
          .run(accountId, spec.id, requestHash, spec.surface, spec.modelId, spec.modelName, spec.conversationId ?? null, turn, spec.batchId ?? null, spec.batchIndex ?? null, system, spec.prompt, createdAt, leaseToken, now + GENERATION_LEASE_MS);
      }
      const row = this.generationRow(accountId, spec.id)!;
      return { generation: generationView(row), leaseToken, turn: row.turn };
    });
  }

  chatHistory(accountId: string, conversationId: string, beforeTurn: number): ChatTurn[] {
    const rows = this.db.prepare("SELECT model_id,prompt,output FROM generation WHERE account_id=? AND conversation_id=? AND turn<? AND state='completed' ORDER BY turn")
      .all(accountId, conversationId, beforeTurn) as Array<{ model_id: string; prompt: string; output: string }>;
    return rows.map(row => ({ modelId: row.model_id, prompt: row.prompt, output: row.output }));
  }

  // 같은 계정과 키로 만료 전에 다시 쓰면 true를 돌려주고 만료 시각을 늘린다.
  usePromptCache(accountId: string, cacheKey: string, now: number, ttlMs: number): boolean {
    return this.transaction(() => {
      const row = this.db.prepare("SELECT expires_at FROM prompt_cache WHERE account_id=? AND cache_key=?").get(accountId, cacheKey) as { expires_at: number } | undefined;
      this.db.prepare("INSERT INTO prompt_cache(account_id,cache_key,expires_at) VALUES (?,?,?) ON CONFLICT(account_id,cache_key) DO UPDATE SET expires_at=excluded.expires_at").run(accountId, cacheKey, now + ttlMs);
      return row !== undefined && row.expires_at > now;
    });
  }

  completeGeneration(accountId: string, id: string, leaseToken: string, result: GenerationResult, event: IngestEventRequest): void {
    this.transaction(() => {
      const { usage } = result;
      const updated = this.db.prepare(`UPDATE generation SET state='completed',output=?,input_tokens=?,output_tokens=?,cache_read_tokens=?,cache_write_tokens=?,latency_ms=?,lease_token=NULL,lease_until=NULL
        WHERE account_id=? AND id=? AND state='processing' AND lease_token=?`)
        .run(result.output, usage.input, usage.output, usage.cacheRead, usage.cacheWrite, result.latencyMs, accountId, id, leaseToken);
      if (updated.changes === 0) return;
      this.db.prepare("INSERT INTO metering_outbox(transaction_id,account_id,generation_id,event_json,status) VALUES (?,?,?,?,'pending')")
        .run(event.transaction_id, accountId, id, JSON.stringify(event));
    });
  }

  failGeneration(accountId: string, id: string, leaseToken: string): void {
    this.db.prepare("UPDATE generation SET state='failed',lease_token=NULL,lease_until=NULL WHERE account_id=? AND id=? AND state='processing' AND lease_token=?").run(accountId, id, leaseToken);
  }

  listGenerations(accountId: string, start?: string, end?: string): GenerationView[] {
    const range = start && end ? "AND g.created_at>=? AND g.created_at<?" : "";
    const rows = this.db.prepare(`${WITH_METERING} WHERE g.account_id=? ${range} ORDER BY g.created_at DESC,g.id DESC LIMIT 100`)
      .all(...(start && end ? [accountId, start, end] : [accountId])) as GenerationRow[];
    return rows.map(generationView);
  }

  listBatch(accountId: string, batchId: string): GenerationView[] {
    const rows = this.db.prepare(`${WITH_METERING} WHERE g.account_id=? AND g.batch_id=? ORDER BY g.batch_index`).all(accountId, batchId) as GenerationRow[];
    return rows.map(generationView);
  }

  listConversations(accountId: string): ConversationSummary[] {
    const rows = this.db.prepare(`SELECT c.id,c.title,c.model_id,c.created_at,c.updated_at,(SELECT COUNT(*) FROM generation g WHERE g.account_id=c.account_id AND g.conversation_id=c.id) AS turns
      FROM conversation c WHERE c.account_id=? ORDER BY c.updated_at DESC,c.id LIMIT 50`).all(accountId) as ConversationRow[];
    return rows.map(conversationSummary);
  }

  getConversation(accountId: string, id: string): ConversationView | null {
    const row = this.db.prepare(`SELECT c.id,c.title,c.model_id,c.created_at,c.updated_at,(SELECT COUNT(*) FROM generation g WHERE g.account_id=c.account_id AND g.conversation_id=c.id) AS turns
      FROM conversation c WHERE c.account_id=? AND c.id=?`).get(accountId, id) as ConversationRow | undefined;
    if (!row) return null;
    const messages = this.db.prepare(`${WITH_METERING} WHERE g.account_id=? AND g.conversation_id=? ORDER BY g.turn`).all(accountId, id) as GenerationRow[];
    return { ...conversationSummary(row), messages: messages.map(generationView) };
  }

  stats(accountId: string, start: string, end: string): UsageStats {
    const row = this.db.prepare(`SELECT COUNT(*) AS total_requests, COALESCE(SUM(g.input_tokens),0) AS input_tokens, COALESCE(SUM(g.output_tokens),0) AS output_tokens,
      COALESCE(SUM(g.cache_read_tokens),0) AS cache_read_tokens, COALESCE(SUM(g.cache_write_tokens),0) AS cache_write_tokens,
      COALESCE(SUM(CASE WHEN o.status IN ('pending','delivering') THEN 1 ELSE 0 END),0) AS pending_metering,
      COALESCE(SUM(CASE WHEN o.status='failed' THEN 1 ELSE 0 END),0) AS failed_metering
      FROM generation g LEFT JOIN metering_outbox o ON o.account_id=g.account_id AND o.generation_id=g.id
      WHERE g.account_id=? AND g.created_at>=? AND g.created_at<? AND g.state='completed'`).get(accountId, start, end) as Record<string, number>;
    return {
      totalRequests: row.total_requests,
      usage: { input: row.input_tokens, output: row.output_tokens, cacheRead: row.cache_read_tokens, cacheWrite: row.cache_write_tokens },
      pendingMetering: row.pending_metering,
      failedMetering: row.failed_metering,
    };
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
