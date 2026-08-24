// Provider abstraction — see docs/decisions/ADRs/ADR-004-product-provider-architecture.md.
// Every implementation must set `dataStatus` honestly and must never
// return fabricated products. A provider with no configured credentials
// must return an empty array, not demo/fake data, unless explicitly asked
// for demo mode (not implemented — no provider currently supports it).

export interface ProductSearchQuery {
  query: string;
  category?: string;
  maxPriceMinor?: number; // minor currency units (paise)
  currency?: string;
}

export interface ProductResult {
  externalId: string;
  name: string;
  brand?: string;
  category: string;
  priceMinor: number;
  currency: string;
  availability: "in_stock" | "out_of_stock" | "unknown";
  widthCm?: number;
  heightCm?: number;
  depthCm?: number;
  material?: string;
  color?: string;
  styleTags?: string[];
  imageUrl?: string;
  productUrl: string;
  dataStatus: "VERIFIED" | "ESTIMATED" | "CACHED" | "UNAVAILABLE" | "DEMO";
  lastUpdated: string; // ISO timestamp
}

export interface ProductProvider {
  readonly name: string;
  readonly isConfigured: boolean;
  search(query: ProductSearchQuery): Promise<ProductResult[]>;
}
