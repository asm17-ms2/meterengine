export type GenerationOutput = { output: string; inputTokens: number; outputTokens: number };
export type GenerationContext = { requestId: string; accountId: string };
export interface GenerationProvider {
  generate(prompt: string, modelId: string, context?: GenerationContext): Promise<GenerationOutput>;
}

export class LocalDemoProvider implements GenerationProvider {
  async generate(prompt: string, modelId: string): Promise<GenerationOutput> {
    const subject = prompt.replace(/\s+/g, " ").trim().slice(0, 180);
    const output = modelId === "lumen-fast"
      ? `초안의 중심은 "${subject}"입니다.\n\n1. 전달할 내용을 한 문장으로 정리합니다.\n2. 읽는 사람이 바로 실행할 다음 행동을 덧붙입니다.\n3. 중복 표현을 덜고 사실과 제안을 구분합니다.\n\n이 응답은 서비스 흐름을 확인하기 위해 로컬 시연 모델이 만든 예시입니다.`
      : `요청을 바탕으로 정리한 업무 초안입니다.\n\n주제\n${subject}\n\n구성 제안\n먼저 독자가 알아야 할 결론을 짧게 설명합니다. 이어서 그 결론을 뒷받침하는 근거와 구체적인 예를 제시합니다. 마지막에는 담당자와 다음 행동을 명확하게 적습니다.\n\n검토할 점\n수치와 고유명사는 원본 자료와 대조하고, 확정된 사실과 아직 결정하지 않은 내용을 나눠 확인하세요.\n\n이 응답은 서비스 흐름을 확인하기 위해 로컬 시연 모델이 만든 예시입니다.`;
    return { output, inputTokens: Array.from(prompt).length, outputTokens: Array.from(output).length };
  }
}
