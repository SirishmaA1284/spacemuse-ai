# Backend Architecture

## Stack

Node.js 22 + TypeScript (strict), Express, Prisma ORM, Zod for schema
validation. See `docs/decisions/ADRs/ADR-007-backend-stack.md`.

## Layout

```
backend/src/
├── api/v1/routes/    HTTP route handlers (thin — validate, call agent, respond)
├── agents/           Coordinator + specialized agents (see agent-architecture.md)
├── ai/
│   ├── gemini/        Gemini API client wrapper
│   ├── prompts/        Versioned prompt templates, one dir per domain
│   └── schemas/         Zod schemas for structured AI output
├── products/
│   ├── productProvider.ts   Provider interface
│   └── providers/             One file per real integration
├── database/          Prisma client + schema
├── security/           Auth middleware
├── tools/               Tool registry (schema-validated function calls)
└── config/              Env loading/validation (fails fast on boot if malformed)
```

## Request lifecycle

```
HTTP request
  → route handler (api/v1/routes) — validates input shape (Zod)
  → Coordinator Agent — detects intent, dispatches to sub-agent
  → sub-agent — may call Gemini, a product provider, or the tool registry
  → tool registry — every state-mutating call is schema-validated before
    it's allowed to touch DesignState (see docs/api/tools.md)
  → Prisma — persist result
  → route handler — respond with typed JSON
```

## Why a tool registry, not free-form LLM state mutation

Section 53/80 of the product spec is explicit: model output must never
directly mutate application state. `src/tools/toolRegistry.ts` defines every
allowed state-mutating operation with a Zod schema; the Gemini response is
parsed into a structured `DesignModification` (or similar) type, validated
against the schema, and only then applied. Invalid or malformed model output
is rejected and logged, never silently applied.

## Degradation without credentials

Every external integration (Gemini, product providers) is written to detect
a missing API key at call time and return a clearly-labeled fallback/error
rather than throwing an unhandled exception or fabricating a response. See
`docs/ai/limitations.md`.
