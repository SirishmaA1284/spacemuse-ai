# Data Flow

## Example: "I want to add a sofa"

```
Android: user speaks/types in Design Studio conversation UI
   → POST /api/v1/designs/{id}/modify { message: "I want to add a sofa" }
   → route handler validates payload shape
   → Coordinator Agent
       → Intent Agent → Gemini (structured output) → IntentResult { intent: "ADD_OBJECT", entity: "sofa" }
           (falls back to keyword rules if Gemini unavailable)
       → dispatch to Furniture Agent (planned) — NOT the full redesign pipeline
       → Furniture Agent would call Shopping Agent → Product Providers → ranked candidates
   → tool registry validates any resulting DesignState mutation
   → Prisma persists updated DesignVersion
   → response returns to Android with the recommendation + "why" explanation
Android: renders recommendation, offers "Try in My Space" (planned)
```

## Data never leaving the device

Raw camera frames are uploaded only for the specific analysis request that
needs them (`rooms/analyze`, `designs/{id}/visualize`); they are not
persisted server-side beyond what's needed to serve that response unless the
user explicitly saves a design (see `docs/security/privacy.md`).

## Cache boundaries

Product data (`Product` table) and preference data are cached server-side
with `lastUpdated` timestamps and surfaced to the client with an explicit
"Price last checked: Xh ago" label — never presented as real-time
(spec section 29).
