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
   scale, no persistence. Sets up Milestone 5 (anchor the same overlay into
   the live AR scene via a real ARCore `Anchor`, scaled using a Milestone
   3-captured room's measurements) and Milestone 6 (persist the placement as
   a `Visualization` row + a dedicated buy flow).

Not yet done: depth-API-based reconciliation, AR-anchored placement
(Milestone 5), placement persistence (Milestone 6), and any measurement UI
beyond straight point-to-point distance (e.g. area/volume). Every milestone
above is unverified until confirmed on a real device — this environment has
no Android SDK/emulator to build or run against locally.
