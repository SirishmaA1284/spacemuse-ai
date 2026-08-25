package com.spacemuse.ai.core.model

import kotlinx.serialization.Serializable

// Mirrors backend/src/products/productProvider.ts and
// backend/src/agents/shoppingAgent.ts — keep both in sync manually until a
// shared schema-generation step exists (tracked in
// docs/development/technical-debt.md).
@Serializable
data class Product(
    val externalId: String,
    val name: String,
    val brand: String? = null,
    val category: String,
    val priceMinor: Int,
    val currency: String,
    val availability: String, // in_stock | out_of_stock | unknown
    val widthCm: Float? = null,
    val heightCm: Float? = null,
    val depthCm: Float? = null,
    val material: String? = null,
    val color: String? = null,
    val styleTags: List<String>? = null,
    val imageUrl: String? = null,
    val productUrl: String,
    val dataStatus: String, // VERIFIED | ESTIMATED | CACHED | UNAVAILABLE | DEMO
    val lastUpdated: String
)

@Serializable
data class ProductSearchResponse(
    val results: List<Product>,
    // false means no shopping provider has credentials configured at all —
    // distinct from a configured provider finding zero matches. Never show
    // this as if it were "no products found".
    val providersConfigured: Boolean
)
