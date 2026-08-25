import { z } from "zod";

// Single source of truth for room types — mirrors the Room.roomType comment
// in backend/prisma/schema.prisma. Update both together.
export const ROOM_TYPES = [
  "living_room",
  "bedroom",
  "kitchen",
  "dining",
  "bathroom",
  "balcony",
  "workspace",
  "other",
] as const;

export const RoomTypeEnum = z.enum(ROOM_TYPES);

export const MeasurementSourceEnum = z.enum(["MEASURED", "ESTIMATED"]);

export const RoomObjectClassificationEnum = z.enum([
  "KEEP",
  "MOVE",
  "REMOVE",
  "REPLACE",
  "MODIFY",
]);

export const RoomObjectResultSchema = z.object({
  id: z.string(),
  type: z.string(),
  classification: RoomObjectClassificationEnum.optional(),
  widthCm: z.number().optional(),
  heightCm: z.number().optional(),
  depthCm: z.number().optional(),
  confidence: z.number().min(0).max(1).optional(),
  measurementSource: MeasurementSourceEnum,
});

export const RoomMeasurementResultSchema = z.object({
  label: z.string(),
  valueCm: z.number(),
  measurementSource: MeasurementSourceEnum,
  confidence: z.number().min(0).max(1).optional(),
});

export const RoomAnalysisSchema = z.object({
  roomType: RoomTypeEnum,
  objects: z.array(RoomObjectResultSchema),
  measurements: z.array(RoomMeasurementResultSchema),
  summary: z.string(),
  source: z.enum(["gemini", "demo"]),
});

export type RoomAnalysis = z.infer<typeof RoomAnalysisSchema>;
export type RoomObjectResult = z.infer<typeof RoomObjectResultSchema>;
export type RoomMeasurementResult = z.infer<typeof RoomMeasurementResultSchema>;
