# ADR-004: Pluggable Product Provider Abstraction

## Context
Product spec section 15/82 requires real product data from multiple possible
sources (retailer APIs, affiliate feeds, aggregators) without hardcoding any
one as a permanent dependency, and forbids scraping sites that disallow it.

## Options considered
- Hardcode one shopping API directly into the shopping agent
- Provider interface (`ProductProvider`) with one implementation per source,
  registered at startup based on which API keys are configured

## Decision
Provider interface (`backend/src/products/productProvider.ts`), with
`GoogleShoppingProvider` (via SerpApi) as the first real implementation.
Amazon PA API and Flipkart Affiliate providers are documented as the next
additions, not built in this pass.

## Reason
No single provider covers all target retailers (IKEA/Pepperfry/Urban Ladder
have no public API), and provider availability depends entirely on which
keys the deployer configures. An interface lets the Shopping Agent be
written once against `ProductProvider[]` and remain correct as providers are
added or removed.

## Consequences
- Every provider must independently guarantee it never fabricates data —
  each `Product` record carries `source` and `dataStatus`; a provider with a
  missing key returns an empty result set, not fake data.
- Retailers without an API (IKEA, Pepperfry, Urban Ladder as of this
  writing) are out of reach until/unless an affiliate program or licensed
  feed is set up — not solvable by scraping (explicitly disallowed by spec
  section 15).
