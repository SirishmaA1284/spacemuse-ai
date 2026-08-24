# ADR-005: Spatial Representation as Structured Room Graph

## Context
Need a persistent digital room model (spec section 10) that can hold both
measured (ARCore) and estimated (vision-model) data without conflating them.

## Options considered
- Store raw point clouds/meshes per room
- Structured object graph (`Room` → `RoomObject[]` with position/rotation/
  dimensions + a measurement-source flag)

## Decision
Structured object graph, stored relationally (Prisma `Room`/`RoomObject`/
`RoomMeasurement`), not raw geometry.

## Reason
Design reasoning (layout rules, budget, product fit) operates on discrete
objects and their relationships, not raw point clouds — a relational model
is directly queryable by the design-rule engine (spec section 33) and
answers "what's the distance between the sofa and the walkway" without a
geometry pipeline. Raw AR mesh/point-cloud data, if captured, can be kept
client-side for rendering without needing to round-trip through the backend.

## Consequences
- Precise mesh-level visualization (e.g. photorealistic AR overlay) is not
  directly served by this schema — that's a client-side rendering concern
  layered on top, using the structured positions as anchors.
- Every object must carry a `measurementSource` field; this is enforced at
  the schema level (spec section 9), not left to convention.
