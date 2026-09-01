# Spatial Architecture (planned — Phase 7)

## Room model

The digital room model (see `docs/database/schema.md` — `Room`,
`RoomObject`, `RoomMeasurement`) is the persistent spatial representation
described in product spec section 10. Each `RoomObject` carries `position`,
`rotation`, `dimensions`, and a `measurementSource: "measured" | "estimated"`
flag — never presented to the user without that distinction (spec section 9).

## Measurement sources, in order of trust

1. **ARCore** (depth API / plane detection) — `measured`
2. **Camera geometry** (reference-object scale inference) — `estimated`
3. **Vision-model estimate** (Gemini reasoning over a single image) — `estimated`, lowest confidence

## Status

In progress, built as a sequence of milestones inside `android/camera/`'s
`ArCameraPreview`/`ArScanScreen` (no separate `spatial/` module yet):

1. **Done** — ARCore session lifecycle + camera passthrough (`BackgroundRenderer`).
2. **Done** — horizontal/vertical plane detection, visualized via `PlaneRenderer`.
3. **Done, confirmed on-device** — tap-to-measure: two plane hits produce a
   real-world distance (`MeasurementRenderer` for the on-screen
   markers/lines), and "Finish Scan" captures a photo + POSTs it with the
   collected `MEASURED` measurements to `POST /rooms`
   (`backend/src/agents/roomCaptureAgent.ts`), which persists alongside
   Gemini's `ESTIMATED` object list from `analyzeRoom()`. No object-level
   fusion between the two lists yet (see the measurement trust order above)
   — deliberately deferred.
4. **Done** — product image overlay compositing (`TryInSpaceScreen`, in
   `android/app`, not `:camera` — no AR/ARCore involved yet): search a
   product, take a static room photo, then drag/pinch/rotate the product's
   photo on top of it (`Modifier.graphicsLayer` + `detectTransformGestures`).
   Deliberately independent of the AR scan flow — no anchor, no real-world
   scale, no persistence.
5. **Done** — AR-anchored placement (`ArTryOnPreview`/`ArTryOnScreen`): tap
   a plane to drop a real ARCore `Anchor`, then project its live
   screen-space position every frame (`ArTryOnPreview.kt`'s
   `AnchorProjection` — standard clip-space → NDC → pixel projection using
   the camera's view/projection matrices, same matrices `BackgroundRenderer`/
   `PlaneRenderer` already use) so the product photo overlay tracks it and
   is sized using "pixels per real-world metre at the anchor's depth"
   (ADR-006's Level 4). Deliberately a separate composable/renderer from
   `ArCameraPreview` (Milestones 1-3) rather than a second tap mode on that
   state machine, to avoid regression risk on the now-confirmed-working
   measurement flow. Product real-world width comes from `Product.widthCm`
   when present, else a furniture-scale fallback (60cm) — pinch/twist let
   the user nudge size/rotation manually on top of that estimate.

Not yet done: depth-API-based reconciliation, placement persistence
(Milestone 6 — a `Visualization` row + dedicated buy flow, "View / Buy" for
now just opens the retailer link), and any measurement UI beyond straight
point-to-point distance (e.g. area/volume). Every milestone above is
unverified until confirmed on a real device — this environment has no
Android SDK/emulator to build or run against locally.
