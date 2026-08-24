# System Architecture

## Overview

SpaceMuse AI is a client/backend system: an Android client (camera, AR,
on-device Gemma, UI) talks to a backend service that owns all cloud
credentials, orchestrates AI agents, and persists design state.

```
┌─────────────────────────────┐
│         Android Client       │
│  Camera / ARCore / Compose   │
│  On-device Gemma (offline)   │
│  Local DataStore/Room cache  │
└───────────────┬──────────────┘
                │ HTTPS (versioned REST, /api/v1)
                ▼
┌─────────────────────────────┐
│          Backend API         │
│  Express + TypeScript         │
│  Coordinator Agent            │
│   ├─ Intent Agent             │
│   ├─ Room Understanding       │
│   ├─ Design / Color / Lighting│
│   ├─ Budget Agent             │
│   ├─ Shopping Agent           │
│   └─ Visualization            │
│  Tool Registry (validated)    │
│  Product Provider abstraction │
└───┬───────────┬──────────┬───┘
    │           │          │
    ▼           ▼          ▼
 Gemini API  Product APIs  Postgres/SQLite
 (reasoning, (SerpApi,     (Prisma ORM)
  vision,     Amazon PA,
  image gen)  Flipkart...)
```

## Why client/backend, not client-only

Gemini API keys, retailer affiliate credentials, and Firebase admin
credentials must never live on-device (see `docs/security/security.md`).
The backend is also where budget optimization, product ranking, and
multi-provider product search happen — logic that benefits from server-side
caching (`docs/database/schema.md` — `Product`, `ProductProvider` tables)
shared across all users rather than duplicated per device.

## What runs where

| Concern | Location | Why |
|---|---|---|
| Camera capture, AR measurement | Android (CameraX, ARCore) | Needs device sensors |
| Lightweight local reasoning, personalization, offline fallback | Android (Gemma, on-device) | Privacy, offline support — see `docs/architecture/gemma.md` |
| Advanced multimodal reasoning, image generation, Live voice | Backend → Gemini API | Requires cloud model, API key must stay server-side |
| Product search, ranking, budget optimization | Backend | Shared caching, credential custody |
| Design state, versions, shopping list persistence | Backend (Postgres/SQLite via Prisma) | Cross-device sync |
| User preferences (non-sensitive subset) | Both — cached locally, synced to backend | Fast local reads, durable cross-device state |

See `docs/architecture/data-flow.md` for request-level sequencing and
`docs/architecture/agent-architecture.md` for how the Coordinator Agent
routes a request to the correct sub-agent.
