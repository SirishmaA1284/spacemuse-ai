# AI Evaluation Framework (planned)

## Dimensions to measure (per product spec section 69)

- **Vision**: object recognition, room classification, material recognition
- **Reasoning**: design relevance, constraint adherence, budget adherence
- **Conversation**: context retention, intent accuracy, modification accuracy
- **Shopping**: product compatibility, budget relevance, dimension compatibility
- **Visualization**: requested-change accuracy, object preservation, scene consistency

## Current status

No evaluation dataset or automated eval harness exists yet — only intent
detection has real logic to evaluate. `backend/tests/unit/intentAgent.test.ts`
covers this with a small hand-written case set (not a formal eval dataset).

## Plan

Once Room Understanding (Phase 2) lands, build a small representative-room
image dataset (10-20 rooms, varied types/lighting) under
`backend/tests/fixtures/rooms/` with hand-labeled expected `RoomAnalysis`
output, and score model output against it. Do not fabricate eval numbers
before this exists.
