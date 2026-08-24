# User Flows

## Flow: First scan

```
Home → "Scan My Space" → Camera permission → Camera/Scanning screen
  → live feedback ("Detecting walls... Detecting furniture...")
  → POST /api/v1/rooms/analyze → RoomAnalysis returned
  → Room Analysis screen (structured summary) → Intent Selection / free chat
```

## Flow: Budget-constrained shopping (planned, not yet wired end-to-end)

```
Design Studio → "Find me real sofas under ₹30,000"
  → Intent: SHOP_FOR_PRODUCT + budget constraint
  → Shopping Agent → Product Providers (ranked, budget-filtered)
  → Product list with score breakdown → "Try in My Space" (planned)
  → Add to Shopping List → Budget screen updates total/remaining
```

## Flow: Version compare (planned)

```
Saved Designs → select design → Version History → pick two versions
  → side-by-side diff (what changed, why)
```

Only the first flow above is implemented end-to-end in this pass (backend
route + Android screen stub); the rest are documented targets for the
corresponding roadmap phases.
