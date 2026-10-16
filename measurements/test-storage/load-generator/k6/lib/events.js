const MODELS = ['claude-opus-5-5', 'claude-sonnet-5-5', 'claude-haiku-5-5', 'claude-fable-5-1'];
const TYPE_CYCLE = 10;
const LLM_SLOTS = 7;
const CHAT_SLOTS = 2;
const LONG_NOTE_CYCLE = 20;
const LONG_NOTE = 'x'.repeat(512);

export function isResend(sequence, period) {
  return sequence % period === period - 1;
}

export function transactionId(runId, step, sequence) {
  return `${runId}-${step}-${sequence}`;
}

function llmRequest(n) {
  const properties = {
    model: MODELS[Math.floor(n / 3) % MODELS.length],
    input_tokens: 100 + (n % 4000),
    output_tokens: 50 + (n % 2000),
    cache_read_tokens: n % 1000,
    cache_creation_tokens: n % 300,
  };
  if (n % LONG_NOTE_CYCLE === 1) {
    properties.note = LONG_NOTE;
  }
  return { type: 'llm_request', properties };
}

function typedProperties(n) {
  const slot = n % TYPE_CYCLE;
  if (slot < LLM_SLOTS) {
    return llmRequest(n);
  }
  if (slot < LLM_SLOTS + CHAT_SLOTS) {
    return { type: 'chat_completion', properties: { token: 10 + (n % 5000) } };
  }
  return { type: 'network_traffic', properties: { egress_gb: (n % 1000) / 100 } };
}

export function buildEvent(customerIds, id, n) {
  return {
    transaction_id: id,
    customer_id: customerIds[Math.floor(n / TYPE_CYCLE) % customerIds.length],
    ...typedProperties(n),
    timestamp: new Date().toISOString(),
  };
}
