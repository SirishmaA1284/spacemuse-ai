// Pure budget math — spec section 24. Deliberately has no I/O so it's
// trivially unit-testable and reusable once the Budget Agent (Phase 11)
// wires it to real shopping-list persistence.

export interface BudgetLineItem {
  name: string;
  priceMinor: number; // minor currency units (paise)
}

export interface BudgetSummary {
  totalSpentMinor: number;
  budgetMinor: number;
  remainingMinor: number;
  overBudget: boolean;
  items: BudgetLineItem[];
}

export function calculateBudgetSummary(
  items: BudgetLineItem[],
  budgetMinor: number
): BudgetSummary {
  const totalSpentMinor = items.reduce((sum, item) => sum + item.priceMinor, 0);
  const remainingMinor = budgetMinor - totalSpentMinor;

  return {
    totalSpentMinor,
    budgetMinor,
    remainingMinor,
    overBudget: remainingMinor < 0,
    items,
  };
}

// Suggests which single item to drop/downgrade to fit budget by removing
// the most expensive item first — a minimal, explainable starting
// heuristic. Real optimization (spec section 25/26 — combination search,
// user-set priorities) is not implemented; this only handles the simplest
// case of "over budget, what's the cheapest fix."
export function cheapestItemToCutForBudget(
  items: BudgetLineItem[]
): BudgetLineItem | null {
  if (items.length === 0) return null;
  return items.reduce((max, item) => (item.priceMinor > max.priceMinor ? item : max));
}
