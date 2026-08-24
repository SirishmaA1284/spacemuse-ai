import { describe, expect, it } from "vitest";
import { detectIntent } from "../../src/agents/intentAgent.js";

// No GEMINI_API_KEY is set in the test environment, so these exercise the
// deterministic rule-based fallback path exclusively — see
// docs/ai/limitations.md.
describe("detectIntent (fallback classifier)", () => {
  it("classifies a rearrangement request", async () => {
    const result = await detectIntent(
      "Can you arrange this room using the things I already have?"
    );
    expect(result.intent).toBe("REARRANGE");
    expect(result.source).toBe("fallback");
  });

  it("classifies an organization request", async () => {
    const result = await detectIntent("How can I make this room feel less cluttered?");
    expect(result.intent).toBe("ORGANIZE");
  });

  it("classifies a product search request", async () => {
    const result = await detectIntent("Find me real sofas that would fit here.");
    expect(result.intent).toBe("SHOP_FOR_PRODUCT");
  });

  it("classifies a budget constraint", async () => {
    const result = await detectIntent("I have ₹40,000 for the sofa, keep it under budget.");
    expect(result.intent).toBe("OPTIMIZE_BUDGET");
  });

  it("classifies a full redesign request distinctly from a partial one", async () => {
    const full = await detectIntent("Redesign the entire room in a modern style.");
    expect(full.intent).toBe("FULL_REDESIGN");

    const partial = await detectIntent("Change only the accent wall.");
    expect(partial.intent).not.toBe("FULL_REDESIGN");
  });

  it("falls back to ASK_QUESTION for an unmatched question", async () => {
    const result = await detectIntent("What time is it?");
    expect(result.intent).toBe("ASK_QUESTION");
  });
});
