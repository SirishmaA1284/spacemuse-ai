# Agent Architecture

```
Coordinator Agent (backend/src/agents/coordinatorAgent.ts)
 ├── Intent Agent            — implemented (Gemini + rule-based fallback)
 ├── Room Understanding       — planned (Phase 2)
 ├── Spatial Reasoning        — planned (Phase 7)
 ├── Design Agent             — planned (Phase 4)
 ├── Color Agent              — planned (Phase 4)
 ├── Furniture Agent          — planned (Phase 4)
 ├── Lighting Agent           — planned (Phase 4)
 ├── Organization Agent       — planned (Phase 4)
 ├── Budget Agent             — planned (Phase 11)
 ├── Shopping Agent           — planned (Phase 9)
 ├── Product Visualization    — planned (Phase 8/10)
 ├── Design Health            — planned (Phase 12)
 ├── Sustainability           — planned (Phase 13)
 └── Memory                   — planned (Phase 6, mostly on-device/Gemma)
```

## Coordinator responsibility

`coordinatorAgent.ts` is the only entry point agents are called through from
route handlers. It:

1. Calls the Intent Agent to classify the request into one of the intents in
   `docs/product/feature-specification.md`.
2. Routes to the relevant sub-agent(s) — **not** the full redesign pipeline,
   per Principle 3 (Minimum Necessary Change) in the product spec.
3. Returns a typed result the route handler can serialize directly.

Sub-agents not yet implemented return a structured "not yet implemented"
result rather than a fabricated response — see
`backend/src/agents/coordinatorAgent.ts` and
`docs/development/technical-debt.md`.

## Adding a new agent

1. Define its input/output types.
2. If it can mutate `DesignState`, register the mutation as a tool in
   `backend/src/tools/toolRegistry.ts` with a Zod schema.
3. Wire it into `coordinatorAgent.ts`'s intent → agent dispatch table.
4. Add unit tests under `backend/tests/unit/`.
5. Update this document's table and `docs/development/roadmap.md`.
