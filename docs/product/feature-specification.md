# Feature Specification — Intents

The Coordinator Agent classifies every user request into exactly one of:

```
REARRANGE            ORGANIZE            ADD_OBJECT
REMOVE_OBJECT         REPLACE_OBJECT       CHANGE_COLOR
CHANGE_STYLE           IMPROVE_LIGHTING      IMPROVE_STORAGE
SHOP_FOR_PRODUCT        TRY_PRODUCT            COMPARE_PRODUCTS
OPTIMIZE_BUDGET          VISUALIZE_CHANGE        DESIGN_ROOM
DESIGN_MULTIPLE_ROOMS     FULL_REDESIGN            ASK_QUESTION
```

Defined as a TypeScript union + Zod enum in
`backend/src/ai/schemas/intentResult.schema.ts`, single source of truth —
docs and code must not drift; if you add an intent, add it there first.

## Implementation status

| Intent | Detection | Execution agent |
|---|---|---|
| All 17 intents | Implemented (Gemini + rule-based fallback) | Not yet implemented — Coordinator returns a structured "recognized, not yet actionable" result for anything except `ASK_QUESTION` |

Detection landing ahead of execution is intentional: it lets the mobile
client and API contract stabilize before each execution agent is built
(see `docs/development/roadmap.md`).

## Routing rule

Full redesign (`FULL_REDESIGN`) only triggers on explicit, unambiguous user
request. Every other intent maps to the narrowest agent capable of
satisfying it — see Principle 3 in `docs/product/product-requirements.md`.
