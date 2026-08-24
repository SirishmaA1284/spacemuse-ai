import { generateStructuredJson } from "../ai/gemini/geminiClient.js";
import { buildDetectIntentPrompt } from "../ai/prompts/intent/detectIntent.prompt.js";
import {
  INTENTS,
  IntentResultSchema,
  type IntentResult,
} from "../ai/schemas/intentResult.schema.js";

const GEMINI_INTENT_RESPONSE_SCHEMA = {
  type: "object",
  properties: {
    intent: { type: "string", enum: INTENTS as unknown as string[] },
    confidence: { type: "number" },
    entities: { type: "array", items: { type: "string" } },
    constraints: { type: "array", items: { type: "string" } },
  },
  required: ["intent", "confidence"],
};

// Deterministic fallback so the app remains usable without a Gemini key.
// Deliberately simple keyword matching — never presented as equal-quality
// to the model path (source: "fallback" is always set on the result).
// See docs/ai/limitations.md.
function classifyWithRules(message: string): IntentResult {
  const text = message.toLowerCase();

  const rules: Array<[RegExp, (typeof INTENTS)[number]]> = [
    // FULL_REDESIGN must be checked before CHANGE_STYLE/CHANGE_COLOR, since
    // a full-redesign request often also mentions a style or color keyword
    // ("redesign the entire room in a modern style") and the narrower
    // intent would otherwise shadow it.
    [/redesign (the )?(entire|whole|full)/, "FULL_REDESIGN"],
    [/rearrange|arrange.*already have|use what i have/, "REARRANGE"],
    [/organi[sz]e|declutter|less cluttered/, "ORGANIZE"],
    [/add (a|an|some)/, "ADD_OBJECT"],
    [/remove|get rid of/, "REMOVE_OBJECT"],
    [/replace|swap/, "REPLACE_OBJECT"],
    [/colou?r|paint|wall.*(green|blue|red|beige|white)/, "CHANGE_COLOR"],
    [/style|theme|japandi|scandinavian|industrial|bohemian/, "CHANGE_STYLE"],
    [/lighting|lamp|brighter|dim/, "IMPROVE_LIGHTING"],
    [/storage|organi[sz]er|shelves/, "IMPROVE_STORAGE"],
    [/find|shop|buy|search for/, "SHOP_FOR_PRODUCT"],
    [/try (this|it|the) .* in my (room|space)/, "TRY_PRODUCT"],
    [/compare/, "COMPARE_PRODUCTS"],
    [/budget|under ₹|under \$|cheaper/, "OPTIMIZE_BUDGET"],
    [/show me how|visuali[sz]e|preview/, "VISUALIZE_CHANGE"],
    [/design (my|the) (living room|bedroom|kitchen)/, "DESIGN_ROOM"],
    [/design.*(rooms|home|house)/, "DESIGN_MULTIPLE_ROOMS"],
    [/\?$/, "ASK_QUESTION"],
  ];

  for (const [pattern, intent] of rules) {
    if (pattern.test(text)) {
      return {
        intent,
        confidence: 0.5,
        entities: [],
        constraints: [],
        source: "fallback",
      };
    }
  }

  return {
    intent: "ASK_QUESTION",
    confidence: 0.3,
    entities: [],
    constraints: [],
    source: "fallback",
  };
}

export async function detectIntent(message: string): Promise<IntentResult> {
  const prompt = buildDetectIntentPrompt(message);
  const geminiResult = await generateStructuredJson<unknown>(
    prompt,
    GEMINI_INTENT_RESPONSE_SCHEMA
  );

  if (geminiResult.ok) {
    const parsed = IntentResultSchema.safeParse({
      ...(geminiResult.data as Record<string, unknown>),
      source: "gemini",
    });
    if (parsed.success) {
      return parsed.data;
    }
    // Model returned malformed structure — do not trust it, fall back.
  }

  return classifyWithRules(message);
}
