import { z } from "zod";
import { detectIntent } from "../agents/intentAgent.js";
import type { IntentResult } from "../ai/schemas/intentResult.schema.js";

// Every state-mutating (or model-adjacent) capability the AI can invoke is
// registered here with a Zod input schema. Nothing outside this file is
// allowed to let model output touch DesignState directly — see
// docs/api/tools.md and docs/architecture/backend-architecture.md.

interface ToolDefinition<TInput, TOutput> {
  name: string;
  inputSchema: z.ZodType<TInput>;
  handler: (input: TInput) => Promise<TOutput>;
}

const detectIntentTool: ToolDefinition<{ message: string }, IntentResult> = {
  name: "detectIntent",
  inputSchema: z.object({ message: z.string().min(1) }),
  handler: async ({ message }) => detectIntent(message),
};

export const toolRegistry = {
  detectIntent: detectIntentTool,
} as const;

export type ToolName = keyof typeof toolRegistry;

export async function callTool(name: ToolName, rawInput: unknown) {
  const tool = toolRegistry[name];
  const parsed = tool.inputSchema.safeParse(rawInput);
  if (!parsed.success) {
    throw new Error(`Invalid input for tool '${name}': ${parsed.error.message}`);
  }
  return tool.handler(parsed.data as never);
}

// Remaining tools from docs/api/tools.md (scanSpace, analyzeRoom,
// rearrangeObjects, addObject, searchProducts, calculateBudget, etc.) are
// specified there but intentionally not registered here yet — adding a
// handler without a schema is not acceptable in this codebase.
