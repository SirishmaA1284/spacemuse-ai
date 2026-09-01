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
4. **Done, confirmed on-device** — product image overlay compositing
   (`TryInSpaceScreen`, in `android/app`, not `:camera` — no AR/ARCore
   involved): search a product, take a static room photo, then
   drag/pinch/rotate the product's photo on top of it
   (`Modifier.graphicsLayer` + `detectTransformGestures`). Deliberately
   independent of the AR scan flow — no anchor, no real-world scale, no
   persistence. The product photo is background-removed before
   compositing via ML Kit Subject Segmentation (`ProductCutout.kt`,
   shared with Milestone 5) — a real user-reported bug (raw retailer
   photos rendering as floating white cards) surfaced and fixed this after
   the milestone first shipped.
5. **Done, real-world-oriented rendering added after initial user
   feedback** — AR-anchored placement (`ArTryOnPreview`/`ArTryOnScreen`):
   tap a plane to drop a real ARCore `Anchor`. The product's (background-
   cut) photo is drawn as a **textured quad inside the AR scene itself**
   (`ProductQuadRenderer`), not a Compose overlay — an earlier version drew
   a flat, always-camera-facing Compose overlay positioned via screen-space
   projection, which didn't tilt/foreshorten as the camera moved around it;
   real user feedback ("if I change the camera angle... not appropriate")
   led to moving rendering into the GL layer, where perspective
   foreshortening is exactly what GL's own projection already gives
   `BackgroundRenderer`/`PlaneRenderer` for free. The quad's orientation is
   derived from the anchor's live surface normal plus a world-space "up"
   reference frozen at placement time (world-up directly for walls; the
   camera's forward direction projected onto the plane for floors/ceilings,
   since world-up is degenerate there) — see `computeReferenceUp` in
   `ArTryOnPreview.kt`. Sizing uses the product's real width
   (`Product.widthCm`, or a 60cm furniture-scale fallback) with height
   derived from the cutout bitmap's own aspect ratio. Touch handling moved
   to a raw `MotionEvent` listener (single-finger tap to place/move the
   anchor, two-finger pinch/twist to adjust size/rotation on top of the
   physical estimate) instead of Compose gesture modifiers, since the
   product is no longer a Compose-rendered element to attach a
   `pointerInput` to. Deliberately a separate composable/renderer from
   `ArCameraPreview` (Milestones 1-3) rather than a second tap mode on that
   state machine, to avoid regression risk on the confirmed-working
   measurement flow.

Not yet done: depth-API-based reconciliation, placement persistence
(Milestone 6 — a `Visualization` row + dedicated buy flow, "View / Buy" for
now just opens the retailer link), and making the *static-photo* flow
(Milestone 4) measurement-aware the way Milestone 5 is (would need
capturing that photo through the AR camera to get a scale reference, since
a plain photo alone carries no depth information) — also any measurement
UI beyond straight point-to-point distance (e.g. area/volume). The
Milestone 5 GL rewrite above is unverified until confirmed on a real
device — this environment has no Android SDK/emulator to build or run
against locally.
