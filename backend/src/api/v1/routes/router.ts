import { Router } from "express";
import { z } from "zod";
import { handleDesignModification } from "../../../agents/coordinatorAgent.js";
import { analyzeRoom } from "../../../agents/roomAnalysisAgent.js";
import { createRoomFromScan } from "../../../agents/roomCaptureAgent.js";
import { searchProducts } from "../../../agents/shoppingAgent.js";
import { RoomMeasurementResultSchema, RoomTypeEnum } from "../../../ai/schemas/roomAnalysis.schema.js";
import { isGeminiConfigured } from "../../../config/env.js";

export const v1Router = Router();

v1Router.get("/health", (_req, res) => {
  res.json({ status: "ok", geminiConfigured: isGeminiConfigured });
});

const RoomAnalyzeSchema = z.object({
  imageBase64: z.string().min(1).optional(),
  note: z.string().optional(),
});

v1Router.post("/rooms/analyze", async (req, res) => {
  const parsed = RoomAnalyzeSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: "invalid_request", details: parsed.error.flatten() });
    return;
  }
  const analysis = await analyzeRoom(parsed.data);
  res.json(analysis);
});

// Separate from /rooms/analyze (which stays stateless — nothing depends on
// it starting to persist as a side effect). This is the first-ever write
// path into Room/RoomObject/RoomMeasurement; see roomCaptureAgent.ts.
const RoomCreateSchema = z.object({
  imageBase64: z.string().min(1),
  roomType: RoomTypeEnum.optional(),
  measuredMeasurements: z
    .array(RoomMeasurementResultSchema.extend({ measurementSource: z.literal("MEASURED") }))
    .default([]),
});

v1Router.post("/rooms", async (req, res) => {
  const parsed = RoomCreateSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: "invalid_request", details: parsed.error.flatten() });
    return;
  }
  const result = await createRoomFromScan(parsed.data);
  res.json(result);
});

const ModifyDesignSchema = z.object({
  message: z.string().min(1),
});

v1Router.post("/designs/:id/modify", async (req, res) => {
  const parsed = ModifyDesignSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: "invalid_request", details: parsed.error.flatten() });
    return;
  }

  const result = await handleDesignModification(parsed.data.message);
  res.json(result);
});

const ProductSearchQuerySchema = z.object({
  q: z.string().min(1),
  category: z.string().optional(),
  maxPrice: z.coerce.number().positive().optional(), // rupees; converted to minor units below
});

v1Router.get("/products/search", async (req, res) => {
  const parsed = ProductSearchQuerySchema.safeParse(req.query);
  if (!parsed.success) {
    res.status(400).json({ error: "invalid_request", details: parsed.error.flatten() });
    return;
  }

  const { q, category, maxPrice } = parsed.data;
  const result = await searchProducts({
    query: q,
    category,
    maxPriceMinor: maxPrice !== undefined ? Math.round(maxPrice * 100) : undefined,
  });
  res.json(result);
});

// Routes specified in docs/api/api-specification.md but not yet
// implemented. Return 501 with the target roadmap phase rather than a bare
// 404, so the documented API contract is visible even before it's built.
const notImplemented = (phase: string) => (_req: import("express").Request, res: import("express").Response) => {
  res.status(501).json({ error: "not_implemented", phase });
};

v1Router.post("/designs", notImplemented("Phase 4 — Design Reasoning"));
v1Router.get("/designs/:id", notImplemented("Phase 4 — Design Reasoning"));
v1Router.post("/designs/:id/visualize", notImplemented("Phase 8 — Visualization"));
v1Router.get("/products/:id", notImplemented("Phase 9 — Real Product Discovery"));
v1Router.post("/products/compare", notImplemented("Phase 9 — Real Product Discovery"));
v1Router.post("/products/try-in-space", notImplemented("Phase 10 — Try-In-Space"));
v1Router.get("/preferences", notImplemented("Phase 6 — Gemma On-Device / Preferences sync"));
v1Router.put("/preferences", notImplemented("Phase 6 — Gemma On-Device / Preferences sync"));
v1Router.post("/budget/optimize", notImplemented("Phase 11 — Budget Optimization"));
v1Router.get("/design-health/:id", notImplemented("Phase 12 — Design Health"));
