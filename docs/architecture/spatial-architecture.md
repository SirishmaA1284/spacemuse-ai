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

Not yet implemented. `android/camera/` currently captures images only; the
ARCore session, depth reconciliation, and the `spatial/` module itself are
scheduled for Phase 7 per `docs/development/roadmap.md`, after room
understanding (Phase 2) and intent detection (Phase 3) land. Building
spatial measurement before room understanding exists would have nothing to
attach measurements to, hence the ordering.
