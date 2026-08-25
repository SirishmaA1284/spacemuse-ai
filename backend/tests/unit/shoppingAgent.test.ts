import { describe, expect, it } from "vitest";
import { searchProducts } from "../../src/agents/shoppingAgent.js";

// No SERPAPI_KEY (or any other product provider credential) is set in the
// test environment, so this exercises the "no provider configured" path —
// per docs/decisions/ADRs/ADR-004-product-provider-architecture.md, an
// unconfigured provider must return an empty array, never fake data.
describe("searchProducts (no providers configured)", () => {
  it("reports providersConfigured: false rather than a misleading empty match", async () => {
    const result = await searchProducts({ query: "sofa" });
    expect(result.providersConfigured).toBe(false);
    expect(result.results).toEqual([]);
  });
});
