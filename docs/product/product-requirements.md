# Product Requirements

## Vision

An AI system that sees and understands a user's physical living space and
acts as a professional interior designer — spatial planner, organization
consultant, shopping assistant, and design advisor — through natural
conversation and camera input.

## Core principles (non-negotiable)

1. **Intent before action** — never assume a full redesign.
2. **Preserve before replacing** — rearrange/restyle/reuse before buying new.
3. **Minimum necessary change** — smallest change that satisfies the request.
4. **User remains in control** — explicit constraints (keep/move/remove,
   budget ceilings, "use only what I own") are hard constraints, not
   suggestions.
5. **Real products only** — no fabricated products, prices, or availability.
6. **Explain why** — every material recommendation states its reasoning.
7. **AI visualization is not reality** — measured vs. estimated vs.
   AI-generated vs. real product data must always be visually distinguished.

Full detail and rationale for each principle: see the original product spec
(sections 3, 93) — summarized here for engineering reference.

## Primary user journey

Scan/Upload → Understand Room → Understand Intent → Clarify if needed →
Room Model → Recommendations → Explain → User selects/modifies → Visualize →
Find Products → Try Products → Validate Space/Budget → Save → Compare
Versions → Implement/Purchase.

## Out of scope for v0 (see roadmap)

Gemini Live voice, on-device Gemma inference, ARCore measurement,
image-generation visualization, try-in-space, multi-room/whole-home
budgeting, predictive "future living" simulation, sustainability scoring.
These are architected (see `docs/architecture/`) but not implemented in this
pass.
