import { prisma } from "../database/client.js";
import { analyzeRoom } from "./roomAnalysisAgent.js";
import type { RoomAnalysis, RoomMeasurementResult } from "../ai/schemas/roomAnalysis.schema.js";

export interface CreateRoomFromScanInput {
  imageBase64: string;
  roomType?: RoomAnalysis["roomType"];
  // Router-level Zod validation enforces measurementSource === "MEASURED"
  // here (see RoomCreateSchema in router.ts) — this agent trusts that by
  // the time it receives them, never re-derives or coerces the source.
  measuredMeasurements: RoomMeasurementResult[];
}

export interface CreateRoomFromScanResult {
  roomId: string;
  analysis: RoomAnalysis;
}

const DEMO_USER_EMAIL = "demo-user@spacemuse.ai";

// Stopgap until real auth exists (Phase 17 — see docs/development/technical-debt.md;
// authMiddleware.ts is currently a pass-through no-op that never resolves a
// userId). Room.userId is a required FK, and no User row exists anywhere in
// this codebase yet — find-or-create a single fixed demo user rather than
// inventing a broader auth mechanism as a side effect of this feature.
async function getOrCreateDemoUser() {
  return prisma.user.upsert({
    where: { email: DEMO_USER_EMAIL },
    update: {},
    create: { email: DEMO_USER_EMAIL },
  });
}

// First-ever write path from a room scan into Room/RoomObject/RoomMeasurement
// (see docs/development/roadmap.md's "Immediate next steps" — this was
// pending before AR work started). Object *identification* still comes from
// Gemini vision via the existing analyzeRoom() (ESTIMATED, unchanged) —
// ARCore gives geometry/scale, not "this is a sofa". The client's
// MEASURED plane-extent measurements and Gemini's ESTIMATED object list are
// kept as two parallel lists rather than fuzzy-matched against each other
// (e.g. "this MEASURED extent belongs to that ESTIMATED sofa") — real
// object-level measurement fusion is a harder problem, deliberately
// deferred (see docs/architecture/spatial-architecture.md's measurement
// trust order).
export async function createRoomFromScan(
  input: CreateRoomFromScanInput
): Promise<CreateRoomFromScanResult> {
  const analysis = await analyzeRoom({ imageBase64: input.imageBase64 });
  const user = await getOrCreateDemoUser();
  const roomType = input.roomType ?? analysis.roomType;
  const combinedMeasurements = [...input.measuredMeasurements, ...analysis.measurements];

  const room = await prisma.room.create({
    data: {
      userId: user.id,
      name: `Room scan ${new Date().toISOString()}`,
      roomType,
      objects: {
        create: analysis.objects.map((obj) => ({
          type: obj.type,
          classification: obj.classification,
          widthCm: obj.widthCm,
          heightCm: obj.heightCm,
          depthCm: obj.depthCm,
          confidence: obj.confidence,
          measurementSource: obj.measurementSource,
        })),
      },
      measurements: {
        create: combinedMeasurements.map((m) => ({
          label: m.label,
          valueCm: m.valueCm,
          measurementSource: m.measurementSource,
          confidence: m.confidence,
        })),
      },
    },
  });

  return {
    roomId: room.id,
    analysis: { ...analysis, roomType, measurements: combinedMeasurements },
  };
}
