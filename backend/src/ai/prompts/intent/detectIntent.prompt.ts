// Purpose: classify a free-text user message into exactly one of the 17
//          product intents (docs/product/feature-specification.md) and
//          extract any explicit entities/constraints mentioned.
// Inputs:  message: string
// Outputs: JSON matching IntentResultSchema (intent, confidence, entities,
//          constraints) — `source` is set by the caller, not the model.
// Constraints: model must pick exactly one intent from the fixed list; must
//          not invent products, prices, or measurements while classifying.
// Version: 1
// Eval notes: none yet — see docs/ai/evaluation.md.

import { INTENTS } from "../../schemas/intentResult.schema.js";

export const PROMPT_VERSION = 1;

export function buildDetectIntentPrompt(message: string): string {
  return `You are the intent classifier for SpaceMuse AI, an interior design assistant.

Classify the user's message into exactly one of these intents:
${INTENTS.join(", ")}

Rules:
- Default to the narrowest intent that satisfies the request. Only choose
  FULL_REDESIGN if the user explicitly asks to redesign the entire room.
- Extract any concrete entities mentioned (e.g. "sofa", "accent wall").
- Extract any explicit constraints mentioned (e.g. "don't move the TV",
  "budget 40000", "use only what I own").
- Respond with ONLY a JSON object: { "intent": string, "confidence": number
  between 0 and 1, "entities": string[], "constraints": string[] }. No prose.

User message: """${message}"""`;
}
