# ADR-007: Node.js + TypeScript + Express + Prisma for Backend

## Context
Backend needs to orchestrate Gemini calls, product providers, and
persistence, and be buildable/testable in this environment (Node 22
available; no JDK, so a JVM backend was not a realistic option here anyway).

## Options considered
- Node.js + TypeScript + Express + Prisma
- Python + FastAPI + SQLAlchemy

## Decision
Node.js + TypeScript + Express + Prisma + Zod.

## Reason
Google's `@google/genai` SDK has first-class TypeScript/Node support; a
single language (TypeScript) across prompt schemas, tool-call schemas, and
API contracts reduces translation drift versus a Python backend serving a
Kotlin client. Prisma gives a single schema file that generates both the
migration DDL and a typed client, matching the section-55 entity list
directly. Express is intentionally minimal/boring — this system's
complexity belongs in the agent layer, not the HTTP framework.

## Consequences
- Team must be comfortable with TypeScript across the whole backend.
- Any future Python-only ML tooling (e.g. a custom vision model) would run
  as a separate service called over HTTP, not in-process.
