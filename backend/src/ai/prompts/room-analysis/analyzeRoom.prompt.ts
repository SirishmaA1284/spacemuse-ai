// Purpose: analyze a room photo and produce a structured breakdown of room
//          type, detected objects (KEEP/MOVE/REMOVE/REPLACE/MODIFY
//          candidates), and any dimensions visually estimable from the
//          image.
// Inputs:  note?: string — optional free-text context from the user
//          (an image is attached separately as an inline part, not text)
// Outputs: JSON matching RoomAnalysisSchema minus per-object `id` and
//          `source` (both assigned by the backend after parsing) — see
//          backend/src/ai/schemas/roomAnalysis.schema.ts
// Constraints: model must not report a MEASURED measurementSource — a
//          single photo can only ever produce ESTIMATED values (spec
//          section 9, "never render an ESTIMATED value as if it were
//          MEASURED"); must not invent product names, brands, or prices.
// Version: 1
// Eval notes: none yet — see docs/ai/evaluation.md.

import { ROOM_TYPES } from "../../schemas/roomAnalysis.schema.js";

export const PROMPT_VERSION = 1;

export function buildAnalyzeRoomPrompt(note?: string): string {
  return `You are the room-analysis vision model for SpaceMuse AI, an interior design assistant.

Look at the attached room photo and identify:
- The room type — exactly one of: ${ROOM_TYPES.join(", ")}.
- Every distinct piece of furniture/decor you can see, each with an
  estimated classification of KEEP, MOVE, REMOVE, REPLACE, or MODIFY
  (default to KEEP unless something looks clearly damaged, out of place, or
  redundant).
- Any dimensions you can visually estimate, in centimeters. Every
  measurement derived from a photo is an ESTIMATE, never MEASURED — only a
  real AR/sensor scan can produce a MEASURED value.
- A one or two sentence summary of the room.
${note ? `\nAdditional context from the user: """${note}"""\n` : ""}
Respond with ONLY a JSON object:
{
  "roomType": string,
  "objects": [{ "type": string, "classification": string, "widthCm": number, "heightCm": number, "depthCm": number, "confidence": number, "measurementSource": "ESTIMATED" }],
  "measurements": [{ "label": string, "valueCm": number, "measurementSource": "ESTIMATED", "confidence": number }],
  "summary": string
}
No prose outside the JSON.`;
}
