import { createHash } from "node:crypto";
import { MODELS } from "./catalog.ts";
import type { LatencyProfile, SurfaceId } from "./catalog.ts";
import type { TokenUsage } from "./contracts.ts";
import { estimateTokens, PROMPT_CACHE_MIN_TOKENS } from "./tokens.ts";

export type ChatTurn = { modelId: string; prompt: string; output: string };
export type GenerationInput = { surface: SurfaceId; modelId: string; prompt: string; system?: string; history?: ChatTurn[]; isSystemCached?: boolean };
export type GenerationOutput = { output: string; usage: TokenUsage; latencyMs: number };
export type GenerationContext = { requestId: string; accountId: string };
export interface GenerationProvider {
  generate(input: GenerationInput, context?: GenerationContext): Promise<GenerationOutput>;
}

export { estimateTokens, PROMPT_CACHE_MIN_TOKENS, PROMPT_CACHE_TTL_MS } from "./tokens.ts";

// 대화 화면이 모든 요청 앞에 붙이는 서비스 지시의 토큰 수. 모델마다 캐시에 올라가 있다고 본다.
export const CHAT_SYSTEM_TOKENS = 1800;
export const EMBEDDING_DIMENSIONS = 256;

type Model = (typeof MODELS)[number];

const ZERO_USAGE: TokenUsage = { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 };

// 요청 = 서비스 지시 + 앞선 차례 + 새 메시지. 캐시 읽기, 캐시 쓰기, 입력을 더하면 요청 전체가 된다.
export function chatUsage(history: ChatTurn[], modelId: string, prompt: string, output: string): TokenUsage {
  const turns = history.map(turn => estimateTokens(turn.prompt) + estimateTokens(turn.output));
  const context = turns.reduce((sum, value) => sum + value, CHAT_SYSTEM_TOKENS);
  const latest = turns.at(-1) ?? 0;
  const isSameModel = history.length === 0 || history.at(-1)!.modelId === modelId;
  return {
    input: estimateTokens(prompt),
    output: estimateTokens(output),
    cacheRead: isSameModel ? context - latest : 0,
    cacheWrite: isSameModel ? latest : context,
  };
}

// 시스템 지시가 길면 처음 부를 때 캐시에 쓰고 같은 지시로 다시 부르면 읽는다.
export function apiUsage(system: string, prompt: string, output: string, isSystemCached: boolean): TokenUsage {
  const systemTokens = estimateTokens(system);
  const isCacheable = systemTokens >= PROMPT_CACHE_MIN_TOKENS;
  return {
    input: estimateTokens(prompt) + (isCacheable ? 0 : systemTokens),
    output: estimateTokens(output),
    cacheRead: isCacheable && isSystemCached ? systemTokens : 0,
    cacheWrite: isCacheable && !isSystemCached ? systemTokens : 0,
  };
}

export function embeddingUsage(text: string): TokenUsage {
  return { ...ZERO_USAGE, input: estimateTokens(text) };
}

function digest(value: string): Buffer {
  return createHash("sha256").update(value).digest();
}

function pick<T>(choices: readonly T[], seed: Buffer): T {
  return choices[seed[0] % choices.length];
}

function embedding(text: string): number[] {
  const values: number[] = [];
  for (let block = 0; values.length < EMBEDDING_DIMENSIONS; block++) {
    const bytes = digest(`${block}:${text}`);
    for (let index = 0; index + 1 < bytes.length && values.length < EMBEDDING_DIMENSIONS; index += 2) {
      values.push(bytes.readInt16BE(index) / 32768);
    }
  }
  const length = Math.hypot(...values) || 1;
  return values.map(value => Math.round((value / length) * 1e6) / 1e6);
}

function textReply(model: Model, prompt: string, hasHistory: boolean): string {
  const subject = prompt.replace(/\s+/g, " ").trim().slice(0, 160);
  const seed = digest(`${model.id}:${prompt}`);
  const lead = hasHistory ? "앞서 나눈 이야기에 이어서 답할게요.\n\n" : "";
  if (model.reply === "label") {
    return `분류: ${pick(["문의", "요청", "의견"], seed)}\n요약: ${subject.slice(0, 60)}\n확신도: ${(0.72 + (seed[1] % 25) / 100).toFixed(2)}`;
  }
  if (model.reply === "brief") {
    return lead + pick([
      `짧게 정리하면 이래요.\n\n- 해야 할 일을 한 문장으로 적어요.\n- 바로 할 수 있는 첫 행동을 하나 정해요.\n- 겹치는 표현을 덜어 내요.\n\n요청: ${subject}`,
      `요청하신 내용을 바로 쓸 수 있게 줄였어요.\n\n${subject}\n\n먼저 결론을 한 줄로 쓰고, 근거는 두 가지만 남겨 보세요. 읽는 사람이 다음에 할 일을 알 수 있으면 충분해요.`,
    ], seed);
  }
  return lead + pick([
    `요청하신 내용을 세 단계로 나눠 정리했어요.\n\n1. 목표 정하기\n무엇을 끝내야 하는지 한 문장으로 적어요. 기준이 분명하면 다음 단계에서 고를 것이 줄어요.\n\n2. 근거 모으기\n결론을 받칠 수치와 사례를 모아요. 출처가 다른 자료는 날짜와 단위를 맞춰 비교해요.\n\n3. 다음 행동 적기\n누가 언제까지 무엇을 할지 적어요. 정하지 못한 항목은 따로 표시해 두면 검토가 빨라져요.\n\n요청: ${subject}`,
    `먼저 범위를 좁히면 결과가 빨리 나와요.\n\n요청: ${subject}\n\n꼭 필요한 조건과 있으면 좋은 조건을 나눠 보세요. 꼭 필요한 조건만으로 첫 안을 만들고, 나머지는 다음 차례에 하나씩 더하면 비교하기 쉬워요.\n\n검토할 때는 숫자와 고유명사를 원본과 맞춰 보고, 확정한 내용과 아직 정하지 않은 내용을 나눠 적어 두세요.`,
  ], seed);
}

// 응답 시간은 모델과 출력 길이로 정한 모형 값이고 실제로 기다리지 않는다.
export function modeledLatency(profile: LatencyProfile, usage: TokenUsage, seedText: string): number {
  return profile.base + profile.perOutputToken * usage.output + Math.floor(usage.input / 50) + (digest(seedText)[2] % 120);
}

export class LocalDemoProvider implements GenerationProvider {
  async generate(input: GenerationInput): Promise<GenerationOutput> {
    const model = MODELS.find(item => item.id === input.modelId);
    if (!model) throw new Error("Unknown model");
    const system = input.system ?? "";
    let output: string;
    let usage: TokenUsage;
    if (model.kind === "embedding") {
      output = JSON.stringify(embedding(input.prompt));
      usage = embeddingUsage(input.prompt);
    } else {
      const history = input.history ?? [];
      output = textReply(model, input.prompt, history.length > 0);
      usage = input.surface === "chat" ? chatUsage(history, model.id, input.prompt, output)
        : input.surface === "api" ? apiUsage(system, input.prompt, output, input.isSystemCached ?? false)
        : { ...ZERO_USAGE, input: estimateTokens(input.prompt), output: estimateTokens(output) };
    }
    return { output, usage, latencyMs: modeledLatency(model.latency, usage, `${input.surface}:${input.prompt}`) };
  }
}
