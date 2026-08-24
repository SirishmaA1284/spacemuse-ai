import express from "express";
import cors from "cors";
import { config } from "./config/env.js";
import { v1Router } from "./api/v1/routes/router.js";

const app = express();

app.use(cors());
app.use(express.json({ limit: "15mb" })); // room photos can be large

app.use("/api/v1", v1Router);

app.use((_req, res) => {
  res.status(404).json({ error: "not_found" });
});

app.listen(config.port, () => {
  console.log(`SpaceMuse AI backend listening on port ${config.port} (${config.nodeEnv})`);
});
