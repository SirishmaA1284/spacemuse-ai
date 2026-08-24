# ADR-006: Progressive Visualization Levels

## Context
Spec section 19 requires "Try in My Space" without mandating every product
have a 3D asset — most real retailer products only expose 2D images.

## Options considered
- Require 3D assets for all try-in-space products (blocks on asset pipeline)
- Progressive levels: image-based → segmentation+placement → 3D asset → AR

## Decision
Implement progressively, using the best available method per product:
Level 1 (image compositing) first, Level 4 (ARCore real-world placement)
last, matching spec section 19 exactly.

## Reason
Gating the whole feature on 3D-asset availability would mean it never ships
for the majority of real retailer catalog items, which are photo-only.

## Consequences
- Visualization quality is inherently inconsistent across products until
  higher levels are built — the UI must label which level was used per
  product rather than implying uniform fidelity.
- Not yet implemented in this pass (Phase 8/10 in roadmap) — this ADR
  records the target strategy ahead of the code existing.
