import { describe, expect, it } from "vitest";
import { analyzeRoom } from "../../src/agents/roomAnalysisAgent.js";

// analyzeRoom({}) (no image) always short-circuits to the deterministic
// demo analysis regardless of GEMINI_API_KEY, so these are stable
// regardless of environment — see docs/ai/limitations.md.
describe("analyzeRoom (demo fallback)", () => {
  it("returns a demo analysis when no image is provided", async () => {
    const result = await analyzeRoom({});
    expect(result.source).toBe("demo");
    expect(result.roomType).toBe("living_room");
    expect(result.objects.length).toBeGreaterThan(0);
  });

  it("gives every object a unique id", async () => {
    const result = await analyzeRoom({});
    const ids = new Set(result.objects.map((obj) => obj.id));
    expect(ids.size).toBe(result.objects.length);
  });

  it("never reports a MEASURED source in demo data", async () => {
    const result = await analyzeRoom({});
    for (const obj of result.objects) {
      expect(obj.measurementSource).toBe("ESTIMATED");
    }
    for (const measurement of result.measurements) {
      expect(measurement.measurementSource).toBe("ESTIMATED");
    }
  });

  it("falls back to demo data when given an image that isn't valid Gemini input", async () => {
    // Covers both cases this repo runs under: GEMINI_API_KEY unset (never
    // calls out) and configured (garbage image data fails and falls back).
    const result = await analyzeRoom({ imageBase64: "notarealimage==" });
    expect(result.source).toBe("demo");
  });
});
