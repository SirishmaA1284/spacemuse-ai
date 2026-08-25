import { GoogleGenAI, type Content } from "@google/genai";
import { config, isGeminiConfigured } from "../../config/env.js";

export type GeminiJsonResult<T> =
  | { ok: true; data: T }
  | { ok: false; reason: string };

let client: GoogleGenAI | null = null;

function getClient(): GoogleGenAI {
  if (!client) {
    client = new GoogleGenAI({ apiKey: config.geminiApiKey });
  }
  return client;
}

// Shared by generateStructuredJson and generateStructuredJsonFromImage —
// both request structured JSON output and parse it the same way; only the
// message contents differ (text-only vs. text + inline image).
async function runStructuredJson<T>(
  contents: string | Content[],
  responseSchema: Record<string, unknown>
): Promise<GeminiJsonResult<T>> {
  if (!isGeminiConfigured) {
    return { ok: false, reason: "GEMINI_API_KEY not configured" };
  }

  try {
    const response = await getClient().models.generateContent({
      model: config.geminiModel,
      contents,
      config: {
        responseMimeType: "application/json",
        responseSchema,
      },
    });

    const text = response.text;
    if (!text) {
      return { ok: false, reason: "Empty response from Gemini" };
    }

    const parsed = JSON.parse(text) as T;
    return { ok: true, data: parsed };
  } catch (error) {
    const reason = error instanceof Error ? error.message : "Unknown Gemini error";
    return { ok: false, reason };
  }
}

// Calls Gemini requesting structured JSON output and parses it. Never
// throws — callers must handle the `ok: false` branch (e.g. fall back to a
// deterministic rule, per docs/ai/limitations.md). This is the ONLY place
// in the backend that talks to the Gemini SDK directly (alongside
// generateStructuredJsonFromImage below).
export function generateStructuredJson<T>(
  prompt: string,
  responseSchema: Record<string, unknown>
): Promise<GeminiJsonResult<T>> {
  return runStructuredJson<T>(prompt, responseSchema);
}

// Same contract as generateStructuredJson, but attaches an inline image
// part alongside the text prompt — used for room-photo analysis.
export function generateStructuredJsonFromImage<T>(
  prompt: string,
  imageBase64: string,
  mimeType: string,
  responseSchema: Record<string, unknown>
): Promise<GeminiJsonResult<T>> {
  const contents: Content[] = [
    {
      role: "user",
      parts: [{ text: prompt }, { inlineData: { data: imageBase64, mimeType } }],
    },
  ];
  return runStructuredJson<T>(contents, responseSchema);
}
