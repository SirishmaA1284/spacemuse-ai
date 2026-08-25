import { describe, expect, it } from "vitest";
import { searchProducts } from "../../src/agents/shoppingAgent.js";
import type { ProductProvider, ProductResult } from "../../src/products/productProvider.js";

// Injects fake providers rather than relying on which real provider keys
// happen to be set in the ambient environment (SERPAPI_KEY etc.) — keeps
// this deterministic regardless of .env, unlike a test that calls the real
// searchProducts() default and asserts on environment state.
function fakeProvider(overrides: Partial<ProductProvider>): ProductProvider {
  return {
    name: "fake",
    isConfigured: true,
    search: async () => [],
    ...overrides,
  };
}

const SAMPLE_RESULT: ProductResult = {
  externalId: "1",
  name: "Sample Sofa",
  category: "sofa",
  priceMinor: 1999900,
  currency: "INR",
  availability: "unknown",
  productUrl: "https://example.com/sofa",
  dataStatus: "VERIFIED",
  lastUpdated: new Date().toISOString(),
};

describe("searchProducts", () => {
  it("reports providersConfigured: false rather than a misleading empty match when nothing is configured", async () => {
    const unconfigured = fakeProvider({ isConfigured: false });
    const result = await searchProducts({ query: "sofa" }, [unconfigured]);
    expect(result.providersConfigured).toBe(false);
    expect(result.results).toEqual([]);
  });

  it("reports providersConfigured: false for an empty provider list", async () => {
    const result = await searchProducts({ query: "sofa" }, []);
    expect(result.providersConfigured).toBe(false);
    expect(result.results).toEqual([]);
  });

  it("aggregates results from configured providers and reports providersConfigured: true", async () => {
    const configured = fakeProvider({ search: async () => [SAMPLE_RESULT] });
    const result = await searchProducts({ query: "sofa" }, [configured]);
    expect(result.providersConfigured).toBe(true);
    expect(result.results).toEqual([SAMPLE_RESULT]);
  });

  it("only queries configured providers, skipping unconfigured ones", async () => {
    const configured = fakeProvider({ name: "a", search: async () => [SAMPLE_RESULT] });
    const unconfigured = fakeProvider({
      name: "b",
      isConfigured: false,
      search: async () => {
        throw new Error("must not be called");
      },
    });
    const result = await searchProducts({ query: "sofa" }, [configured, unconfigured]);
    expect(result.results).toEqual([SAMPLE_RESULT]);
  });
});
