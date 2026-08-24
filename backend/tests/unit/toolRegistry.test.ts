import { describe, expect, it } from "vitest";
import { callTool } from "../../src/tools/toolRegistry.js";

describe("toolRegistry", () => {
  it("runs detectIntent with valid input", async () => {
    const result = await callTool("detectIntent", { message: "Find me a sofa" });
    expect(result).toHaveProperty("intent");
  });

  it("rejects malformed input before it reaches the handler", async () => {
    await expect(callTool("detectIntent", { message: "" })).rejects.toThrow(
      /Invalid input for tool/
    );
  });

  it("rejects input missing required fields", async () => {
    await expect(callTool("detectIntent", {})).rejects.toThrow(/Invalid input for tool/);
  });
});
