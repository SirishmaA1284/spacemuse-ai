# Roadmap

Phases per product spec section 78/91. Status reflects this repository's
actual state, updated as work lands.

| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation (repo, docs, ADRs, env config) | **Done** |
| 2 | Camera + Room Intelligence | Not started — route/schema stubbed only |
| 3 | Intent Detection | **Done** (Gemini + rule-based fallback) |
| 4 | Design Reasoning (rearrange/color/lighting/furniture agents) | Not started |
| 5 | Gemini Live | Not started |
| 6 | Gemma On-Device | Module scaffold only |
| 7 | Spatial / AR | Not started |
| 8 | Visualization (image-based) | Not started |
| 9 | Real Product Discovery | Provider interface + 1 provider implemented, not called by any agent yet |
| 10 | Try-In-Space | Not started |
| 11 | Budget Optimization | Not started (schema exists) |
| 12 | Design Health | Not started |
| 13 | Sustainability | Not started |
| 14 | Predictive Design | Not started |
| 15 | Multi-Room | Not started (schema supports multiple `Room`s per `User` already) |
| 16 | Whole Home | Not started |
| 17 | Production Hardening (auth, rate limits, Postgres migration) | Not started |

## Immediate next steps (order matters — see data-flow.md)

1. Wire `rooms/analyze` to real Gemini vision call → `RoomAnalysis` (Phase 2).
2. Build Room Understanding agent + KEEP/MOVE/REMOVE/REPLACE classification
   (spec section 11) before Rearrangement Mode, since rearrangement needs an
   object list to operate on.
3. Implement Rearrangement + Organization agents (Phase 4 subset) — these
   are the "zero-budget mode" default (spec section 27) and don't require
   product search, so they're the cheapest path to an end-to-end useful
   flow.
4. Wire Shopping Agent to the existing `GoogleShoppingProvider`.
