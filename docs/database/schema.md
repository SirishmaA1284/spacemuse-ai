# Database Schema

Full definition: `backend/src/database/schema.prisma` (source of truth —
this doc explains intent, the Prisma file is authoritative on types).

## Entities

```
User 1───* Room 1───* RoomObject
User 1───* UserPreference
User 1───* DesignMemory
Room 1───* RoomMeasurement
Room 1───* Design 1───* DesignVersion
Design 1───* DesignConstraint
Design 1───* DesignRecommendation
Design 1───1 Budget 1───* ShoppingListItem
Design 1───* Conversation 1───* ConversationMessage
Design 1───* Visualization
Product *───1 ProductProvider (source)
DesignRecommendation *───* Product (via ShoppingListItem)
```

## Notable fields

- Every `RoomObject` and `RoomMeasurement` carries
  `measurementSource: MEASURED | ESTIMATED` and a `confidence Float?` —
  never optional-away the distinction (spec section 9).
- `Product.dataStatus: VERIFIED | ESTIMATED | CACHED | UNAVAILABLE | DEMO`
  and `lastUpdated DateTime` — required on every row (spec section 16/29).
- `DesignConstraint` stores user hard constraints ("keep sofa", "budget <
  50000") as structured `{ type, targetObjectId?, value }` — these are read
  by every agent before generating a recommendation, not just design-time
  advisory text.
- `DesignVersion` is append-only; `Design.currentVersionId` points at the
  active one. Nothing is destructively overwritten (spec section 45).

## Status

Schema is written and `prisma generate`-valid (verified in this pass — see
`docs/development/development-log.md`). No migration has been run against a
real Postgres instance; local dev uses SQLite (`DATABASE_URL="file:./dev.db"`
in `.env.example`). Prisma's SQLite connector does not support native
`enum` types, so enum-like columns (`measurementSource`, `dataStatus`, etc.)
are modeled as `String` with the allowed values documented in a schema
comment; switching the datasource to Postgres — which does support native
enums — before production is a tracked item in
`docs/development/technical-debt.md`.
