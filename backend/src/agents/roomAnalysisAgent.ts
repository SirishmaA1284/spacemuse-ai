import { randomUUID } from "node:crypto";
import { generateStructuredJsonFromImage } from "../ai/gemini/geminiClient.js";
import { buildAnalyzeRoomPrompt } from "../ai/prompts/room-analysis/analyzeRoom.prompt.js";
import {
  ROOM_TYPES,
  RoomAnalysisSchema,
  type RoomAnalysis,
} from "../ai/schemas/roomAnalysis.schema.js";

export interface AnalyzeRoomInput {
  imageBase64?: string;
  note?: string;
}

const GEMINI_ROOM_RESPONSE_SCHEMA = {
  type: "object",
  properties: {
    roomType: { type: "string", enum: ROOM_TYPES as unknown as string[] },
    objects: {
      type: "array",
      items: {
        type: "object",
        properties: {
          type: { type: "string" },
          classification: {
            type: "string",
            enum: ["KEEP", "MOVE", "REMOVE", "REPLACE", "MODIFY"],
          },
          widthCm: { type: "number" },
          heightCm: { type: "number" },
          depthCm: { type: "number" },
          confidence: { type: "number" },
          measurementSource: { type: "string", enum: ["ESTIMATED"] },
        },
        required: ["type", "measurementSource"],
      },
    },
    measurements: {
      type: "array",
      items: {
        type: "object",
        properties: {
          label: { type: "string" },
          valueCm: { type: "number" },
          measurementSource: { type: "string", enum: ["ESTIMATED"] },
          confidence: { type: "number" },
        },
        required: ["label", "valueCm", "measurementSource"],
      },
    },
    summary: { type: "string" },
  },
  required: ["roomType", "objects", "measurements", "summary"],
};

// Deterministic placeholder so the app has something real to show
// end-to-end without a GEMINI_API_KEY or a real photo (e.g. the emulator's
// fake camera feed). Always tagged source: "demo" so the UI can flag it as
// placeholder data — never presented as if it were a real scan. See
// docs/ai/limitations.md.
function demoAnalysis(): RoomAnalysis {
  return {
    roomType: "living_room",
    objects: [
      {
        id: randomUUID(),
        type: "sofa",
        classification: "KEEP",
        widthCm: 180,
        heightCm: 80,
        depthCm: 90,
        confidence: 0.6,
        measurementSource: "ESTIMATED",
      },
      {
        id: randomUUID(),
        type: "coffee_table",
        classification: "MOVE",
        widthCm: 90,
        heightCm: 40,
        depthCm: 50,
        confidence: 0.55,
        measurementSource: "ESTIMATED",
      },
      {
        id: randomUUID(),
        type: "tv_stand",
        classification: "KEEP",
        widthCm: 120,
        heightCm: 45,
        depthCm: 40,
        confidence: 0.6,
        measurementSource: "ESTIMATED",
      },
      {
        id: randomUUID(),
        type: "rug",
        classification: "KEEP",
        widthCm: 200,
        heightCm: 1,
        depthCm: 140,
        confidence: 0.5,
        measurementSource: "ESTIMATED",
      },
      {
        id: randomUUID(),
        type: "floor_lamp",
        classification: "MOVE",
        widthCm: 30,
        heightCm: 150,
        depthCm: 30,
        confidence: 0.5,
        measurementSource: "ESTIMATED",
      },
    ],
    measurements: [
      { label: "room_width", valueCm: 400, measurementSource: "ESTIMATED", confidence: 0.4 },
      { label: "room_length", valueCm: 500, measurementSource: "ESTIMATED", confidence: 0.4 },
    ],
    summary:
      "Demo analysis — a typical living room layout with a sofa, coffee table, TV stand, rug, and floor lamp. Configure GEMINI_API_KEY on the backend to replace this with a real scan of your photo.",
    source: "demo",
  };
}

export async function analyzeRoom(input: AnalyzeRoomInput): Promise<RoomAnalysis> {
  if (!input.imageBase64) {
    return demoAnalysis();
  }

  const prompt = buildAnalyzeRoomPrompt(input.note);
  const geminiResult = await generateStructuredJsonFromImage<unknown>(
    prompt,
    input.imageBase64,
    "image/jpeg",
    GEMINI_ROOM_RESPONSE_SCHEMA
  );

  if (geminiResult.ok) {
    const raw = geminiResult.data as Record<string, unknown>;
    const objects = Array.isArray(raw.objects)
      ? raw.objects.map((obj) => ({ id: randomUUID(), ...(obj as Record<string, unknown>) }))
      : [];
    const parsed = RoomAnalysisSchema.safeParse({ ...raw, objects, source: "gemini" });
    if (parsed.success) {
      return parsed.data;
    }
    // Model returned a malformed structure — do not trust it, fall back.
  }

  return demoAnalysis();
}
