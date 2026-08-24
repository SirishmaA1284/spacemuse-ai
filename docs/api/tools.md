# Tool Call Registry

Per product spec section 53/80: the model never mutates application state
directly. `backend/src/tools/toolRegistry.ts` defines every allowed
state-mutating operation as a name + Zod input schema + handler function.

## Implemented

- `detectIntent(message: string): IntentResult` — read-only, wraps the
  Intent Agent.

## Specified (schema defined, handler not implemented)

```
scanSpace()            analyzeRoom()           getDimensions()
identifyFurniture()     identifyMaterials()      analyzeLighting()
getPreferences()          updatePreferences()       createDesignPlan()
rearrangeObjects()          moveObject()              addObject()
removeObject()                replaceObject()           changeColor()
changeStyle()                   searchProducts()          getProduct()
compareProducts()                  tryProductInRoom()        calculateBudget()
optimizeBudget()                      validateLayout()          runDesignHealthCheck()
generateVisualization()                  saveDesign()               loadDesign()
compareVersions()
```

Each will follow the same pattern as `detectIntent`: Zod schema for
input, handler calls the relevant agent/service, output validated before
being applied to `DesignState`. Adding a handler without a schema is not
acceptable — see `docs/architecture/backend-architecture.md`.
