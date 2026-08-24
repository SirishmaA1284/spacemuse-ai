import { z } from "zod";

// Single source of truth for the 17 intents — see
// docs/product/feature-specification.md. Update both together.
export const INTENTS = [
  "REARRANGE",
  "ORGANIZE",
  "ADD_OBJECT",
  "REMOVE_OBJECT",
  "REPLACE_OBJECT",
  "CHANGE_COLOR",
  "CHANGE_STYLE",
  "IMPROVE_LIGHTING",
  "IMPROVE_STORAGE",
  "SHOP_FOR_PRODUCT",
  "TRY_PRODUCT",
  "COMPARE_PRODUCTS",
  "OPTIMIZE_BUDGET",
  "VISUALIZE_CHANGE",
  "DESIGN_ROOM",
  "DESIGN_MULTIPLE_ROOMS",
  "FULL_REDESIGN",
  "ASK_QUESTION",
] as const;

export const IntentEnum = z.enum(INTENTS);
export type Intent = z.infer<typeof IntentEnum>;

export const IntentResultSchema = z.object({
  intent: IntentEnum,
  confidence: z.number().min(0).max(1),
  entities: z.array(z.string()).default([]),
  constraints: z.array(z.string()).default([]),
  source: z.enum(["gemini", "fallback"]),
});

export type IntentResult = z.infer<typeof IntentResultSchema>;
