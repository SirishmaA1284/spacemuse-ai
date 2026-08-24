import { describe, expect, it } from "vitest";
import {
  calculateBudgetSummary,
  cheapestItemToCutForBudget,
} from "../../src/agents/budgetCalculator.js";

describe("calculateBudgetSummary", () => {
  it("computes total spent and remaining within budget", () => {
    const summary = calculateBudgetSummary(
      [
        { name: "Sofa", priceMinor: 2999900 },
        { name: "Rug", priceMinor: 600000 },
        { name: "Lamp", priceMinor: 499900 },
      ],
      5000000
    );

    expect(summary.totalSpentMinor).toBe(4099800);
    expect(summary.remainingMinor).toBe(900200);
    expect(summary.overBudget).toBe(false);
  });

  it("flags overBudget when total exceeds the budget", () => {
    const summary = calculateBudgetSummary(
      [{ name: "Sofa", priceMinor: 6200000 }],
      5000000
    );

    expect(summary.overBudget).toBe(true);
    expect(summary.remainingMinor).toBe(-1200000);
  });

  it("handles an empty shopping list", () => {
    const summary = calculateBudgetSummary([], 5000000);
    expect(summary.totalSpentMinor).toBe(0);
    expect(summary.remainingMinor).toBe(5000000);
  });
});

describe("cheapestItemToCutForBudget", () => {
  it("returns the most expensive item as the cut candidate", () => {
    const item = cheapestItemToCutForBudget([
      { name: "Sofa", priceMinor: 2999900 },
      { name: "Coffee Table", priceMinor: 3200000 },
      { name: "Lamp", priceMinor: 499900 },
    ]);
    expect(item?.name).toBe("Coffee Table");
  });

  it("returns null for an empty list", () => {
    expect(cheapestItemToCutForBudget([])).toBeNull();
  });
});
