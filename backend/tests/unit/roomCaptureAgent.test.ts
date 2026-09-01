import { afterAll, describe, expect, it } from "vitest";
import { createRoomFromScan } from "../../src/agents/roomCaptureAgent.js";
import { prisma } from "../../src/database/client.js";

// Unlike the other agent tests, this one genuinely writes to the local dev
// database (no test-DB isolation exists in this repo yet) -- it's the
// first-ever write path into Room/RoomObject/RoomMeasurement, and the
// riskiest part of this milestone is the Prisma nested-create syntax, which
// can only be verified against a real database. Cleans up every row it
// creates in afterAll so repeated runs don't accumulate test rooms.
describe("createRoomFromScan", () => {
  const createdRoomIds: string[] = [];

  afterAll(async () => {
    for (const roomId of createdRoomIds) {
      await prisma.roomObject.deleteMany({ where: { roomId } });
      await prisma.roomMeasurement.deleteMany({ where: { roomId } });
      await prisma.room.delete({ where: { id: roomId } });
    }
    await prisma.$disconnect();
  });

  it("persists a Room with Gemini's ESTIMATED objects and the client's MEASURED measurements", async () => {
    const result = await createRoomFromScan({
      imageBase64: "notarealimage==", // falls back to deterministic demo analysis
      measuredMeasurements: [
        { label: "floor_width", valueCm: 320, measurementSource: "MEASURED", confidence: 0.9 },
      ],
    });
    createdRoomIds.push(result.roomId);

    expect(result.analysis.source).toBe("demo");
    expect(result.analysis.objects.length).toBeGreaterThan(0);
    expect(result.analysis.objects.every((obj) => obj.measurementSource === "ESTIMATED")).toBe(true);
    expect(
      result.analysis.measurements.some(
        (m) => m.label === "floor_width" && m.measurementSource === "MEASURED"
      )
    ).toBe(true);

    const persisted = await prisma.room.findUnique({
      where: { id: result.roomId },
      include: { objects: true, measurements: true },
    });
    expect(persisted).not.toBeNull();
    expect(persisted!.objects.length).toBe(result.analysis.objects.length);
    expect(persisted!.measurements.length).toBe(result.analysis.measurements.length);
    expect(persisted!.measurements.some((m) => m.measurementSource === "MEASURED")).toBe(true);
  });

  it("uses the given roomType override instead of Gemini's default", async () => {
    const result = await createRoomFromScan({
      imageBase64: "notarealimage==",
      roomType: "bedroom",
      measuredMeasurements: [],
    });
    createdRoomIds.push(result.roomId);
    expect(result.analysis.roomType).toBe("bedroom");
  });
});
