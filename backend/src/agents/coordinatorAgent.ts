import { detectIntent } from "./intentAgent.js";
import type { IntentResult } from "../ai/schemas/intentResult.schema.js";

export interface ModifyDesignResult {
  intentResult: IntentResult;
  status: "answered" | "recognized_not_yet_actionable";
  message: string;
}

// Entry point for the /designs/:id/modify route. Routes to the narrowest
// capable agent for the detected intent (Principle 3 — Minimum Necessary
// Change). Only ASK_QUESTION is actually executed today; every other
// intent's execution agent is not yet implemented (see
// docs/architecture/agent-architecture.md) — that is reported honestly
// rather than faked.
export async function handleDesignModification(
  message: string
): Promise<ModifyDesignResult> {
  const intentResult = await detectIntent(message);

  if (intentResult.intent === "ASK_QUESTION") {
    return {
      intentResult,
      status: "answered",
      message:
        "General Q&A is not yet backed by a real answer-generation agent " +
        "in this pass — intent classification succeeded, but no response " +
        "content is generated yet.",
    };
  }

  return {
    intentResult,
    status: "recognized_not_yet_actionable",
    message: `Intent '${intentResult.intent}' was recognized but its execution agent is not implemented yet. See docs/development/roadmap.md.`,
  };
}
