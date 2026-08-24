# Privacy

## What stays on device

- Raw camera preview frames not explicitly submitted for analysis
- Local preference/personalization data (Gemma memory) unless the user
  opts into cross-device sync
- Local design cache for offline viewing

## What is sent to the cloud

- Images explicitly submitted via "Scan My Space" / room analysis — sent to
  the backend, which forwards them to Gemini for that single request
- Text messages sent to the AI conversation
- Saved designs, if the user chooses to save (persisted server-side so they
  sync across devices)

## Retention

Not yet finalized in code — this pass does not persist uploaded room images
server-side beyond the lifetime of the request that needs them (no image
storage table exists in `schema.prisma`). If image persistence is added
later (e.g. to re-render a saved design), it must ship with an explicit
retention policy and a delete endpoint before it's considered complete —
tracked in `docs/development/technical-debt.md`.

## User controls (planned)

- **Delete My Data** — removes all backend-stored designs/preferences for
  the user
- **Clear Local AI Memory** — wipes on-device Gemma personalization store

Neither is implemented yet (no auth/user system exists in this pass) —
listed here as a hard requirement before any real user data is stored.
