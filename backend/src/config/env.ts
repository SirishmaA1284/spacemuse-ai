import "dotenv/config";

interface AppConfig {
  nodeEnv: string;
  port: number;
  geminiApiKey: string | undefined;
  geminiModel: string;
  serpApiKey: string | undefined;
  databaseUrl: string;
}

function readConfig(): AppConfig {
  const port = Number(process.env.PORT ?? "4000");
  if (Number.isNaN(port)) {
    throw new Error(`Invalid PORT env var: ${process.env.PORT}`);
  }

  if (!process.env.DATABASE_URL) {
    throw new Error(
      "DATABASE_URL is required. Copy .env.example to .env and set it (SQLite default: file:./dev.db)."
    );
  }

  return {
    nodeEnv: process.env.NODE_ENV ?? "development",
    port,
    geminiApiKey: process.env.GEMINI_API_KEY || undefined,
    geminiModel: process.env.GEMINI_MODEL ?? "gemini-2.5-flash",
    serpApiKey: process.env.SERPAPI_KEY || undefined,
    databaseUrl: process.env.DATABASE_URL,
  };
}

export const config = readConfig();

export const isGeminiConfigured = Boolean(config.geminiApiKey);
export const isSerpApiConfigured = Boolean(config.serpApiKey);
