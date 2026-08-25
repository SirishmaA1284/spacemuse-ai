import { GoogleShoppingProvider } from "../products/providers/googleShoppingProvider.js";
import type {
  ProductProvider,
  ProductResult,
  ProductSearchQuery,
} from "../products/productProvider.js";

// Registered providers — see docs/decisions/ADRs/ADR-004-product-provider-architecture.md.
// Written against ProductProvider[] so adding Amazon/Flipkart later is just
// another entry here, not a change to this agent.
const PROVIDERS: ProductProvider[] = [new GoogleShoppingProvider()];

export interface ShoppingSearchResult {
  results: ProductResult[];
  // false means no provider has credentials configured at all — distinct
  // from a configured provider genuinely finding zero matches, so the
  // client can tell "nothing to search with" from "searched, found
  // nothing" rather than showing a misleading blank result either way.
  providersConfigured: boolean;
}

// `providers` defaults to the real registered list; tests inject their own
// so provider-selection logic is verifiable without depending on which
// keys happen to be set in the ambient environment.
export async function searchProducts(
  query: ProductSearchQuery,
  providers: ProductProvider[] = PROVIDERS
): Promise<ShoppingSearchResult> {
  const configuredProviders = providers.filter((provider) => provider.isConfigured);
  if (configuredProviders.length === 0) {
    return { results: [], providersConfigured: false };
  }

  const resultsPerProvider = await Promise.all(
    configuredProviders.map((provider) => provider.search(query))
  );

  return { results: resultsPerProvider.flat(), providersConfigured: true };
}
