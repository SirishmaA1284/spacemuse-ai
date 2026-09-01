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
3. **Done** — tap-to-measure: two plane hits produce a real-world distance
   (`MeasurementRenderer` for the on-screen markers/lines), and "Finish Scan"
   captures a photo + POSTs it with the collected `MEASURED` measurements to
   `POST /rooms` (`backend/src/agents/roomCaptureAgent.ts`), which persists
   alongside Gemini's `ESTIMATED` object list from `analyzeRoom()`. No
   object-level fusion between the two lists yet (see the measurement trust
   order above) — deliberately deferred.

Not yet done: depth-API-based reconciliation, and any measurement UI beyond
straight point-to-point distance (e.g. area/volume). Every milestone above
is unverified until confirmed on a real device — this environment has no
Android SDK/emulator to build or run against locally.
