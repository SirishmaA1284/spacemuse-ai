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
4. **Done, confirmed on-device, since made measurement-aware** — product
   image overlay compositing (`TryInSpaceScreen`, in `android/app`, not
   `:camera` for the compositing UI itself): search a product, take a room
   photo, then drag/pinch/rotate the product's photo on top of it
   (`Modifier.graphicsLayer` + `detectTransformGestures`). The product
   photo is background-removed before compositing via ML Kit Subject
   Segmentation (`ProductCutout.kt`, shared with Milestone 5) — a real
   user-reported bug (raw retailer photos rendering as floating white
   cards) surfaced and fixed this after the milestone first shipped. The
   room photo is now captured through the AR camera (`ArPhotoCapture.kt`,
   `:camera`) rather than a plain one: an *optional* tap on a plane before
   capturing records a real-world scale reference (screen pixels per metre
   at that point's depth, computed once at the moment of capture — the
   same screen-space projection technique the pre-3D-rework version of
   Milestone 5 used, reused here since a static photo has no ongoing 3D
   scene to render into), used to size the product correctly by default
   instead of an arbitrary fixed size; pinch still adjusts freely on top,
   now clamped relative to that starting scale rather than a fixed
   absolute range. Skipping the tap falls back to the original fixed-size
   behavior. Devices without ARCore fall back further, to a plain CameraX
   capture with no scale reference at all — same graceful-degrade pattern
   as `ArScanScreen`. `ArPhotoCapture` is a third, independent AR
   composable/renderer (alongside `ArCameraPreview` and `ArTryOnPreview`)
   rather than reusing either — it only needs a single-tap "set a reference
   point" semantic, different from both of the others' tap behavior, and
   this repo's established pattern for a new AR tap semantic is a new file,
   not a mode flag on an already-working one.
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
now just opens the retailer link), and any measurement UI beyond straight
point-to-point distance (e.g. area/volume). Milestone 4's measurement-aware
rework above is unverified until confirmed on a real device — this
environment has no Android SDK/emulator to build or run against locally.
