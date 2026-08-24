import { config, isSerpApiConfigured } from "../../config/env.js";
import type {
  ProductProvider,
  ProductResult,
  ProductSearchQuery,
} from "../productProvider.js";

// Real integration via SerpApi's Google Shopping engine
// (https://serpapi.com/google-shopping-api). Chosen because Google itself
// does not offer a direct public "search shopping results" REST API for
// this use case — SerpApi is a licensed aggregator over Google Shopping
// results. Requires SERPAPI_KEY (see .env.example).
//
// SerpApi's raw prices are strings like "₹29,999.00" — parsed defensively;
// if parsing fails for an item, that item is dropped rather than guessed.

interface SerpApiShoppingResult {
  product_id?: string;
  title?: string;
  source?: string;
  price?: string;
  extracted_price?: number;
  currency?: string;
  product_link?: string;
  link?: string;
  thumbnail?: string;
}

interface SerpApiResponse {
  shopping_results?: SerpApiShoppingResult[];
}

function toMinorUnits(amount: number): number {
  return Math.round(amount * 100);
}

export class GoogleShoppingProvider implements ProductProvider {
  readonly name = "serpapi_google_shopping";

  get isConfigured(): boolean {
    return isSerpApiConfigured;
  }

  async search(query: ProductSearchQuery): Promise<ProductResult[]> {
    if (!this.isConfigured) {
      return [];
    }

    const params = new URLSearchParams({
      engine: "google_shopping",
      q: query.query,
      gl: "in",
      hl: "en",
      api_key: config.serpApiKey as string,
    });

    const response = await fetch(`https://serpapi.com/search.json?${params.toString()}`);
    if (!response.ok) {
      throw new Error(`SerpApi request failed: ${response.status} ${response.statusText}`);
    }

    const data = (await response.json()) as SerpApiResponse;
    const results = data.shopping_results ?? [];
    const now = new Date().toISOString();

    return results
      .filter(
        (item): item is SerpApiShoppingResult & { product_id: string; title: string } =>
          typeof item.product_id === "string" &&
          typeof item.title === "string" &&
          typeof item.extracted_price === "number"
      )
      .map((item) => ({
        externalId: item.product_id,
        name: item.title,
        brand: item.source,
        category: query.category ?? "unknown",
        priceMinor: toMinorUnits(item.extracted_price as number),
        currency: item.currency ?? "INR",
        availability: "unknown" as const,
        imageUrl: item.thumbnail,
        productUrl: item.product_link ?? item.link ?? "",
        dataStatus: "VERIFIED" as const,
        lastUpdated: now,
      }))
      .filter((item) => item.productUrl.length > 0)
      .filter((item) =>
        query.maxPriceMinor ? item.priceMinor <= query.maxPriceMinor : true
      );
  }
}
