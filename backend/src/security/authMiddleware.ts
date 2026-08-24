import type { NextFunction, Request, Response } from "express";

// STUB — pass-through only. No real authentication is implemented yet.
// See docs/security/security.md and docs/development/technical-debt.md.
// Do not deploy this API publicly before this is replaced with real
// Firebase Auth (or equivalent) token verification.
export function requireAuth(_req: Request, _res: Response, next: NextFunction): void {
  next();
}
