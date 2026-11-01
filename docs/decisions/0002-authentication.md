# 0002: Short-lived access tokens and rotating refresh tokens

Status: accepted

## Context

VitalSafe holds health data. A stolen token should do as little damage as possible, and a leaked database
should not let anyone log in.

## Decision

- Access tokens are signed JWTs that last 15 minutes. The subject is the user's UUID, with a `role` claim.
  Clients keep them in memory only.
- Refresh tokens are random 32-byte values. Only their SHA-256 hash is stored. They live in an HttpOnly,
  SameSite=Strict cookie that is scoped to `/api/v1/auth`, and the Secure flag is on in production.
- Every refresh retires the used token and issues a new one. If a retired token is presented again,
  every session of that user is revoked.
- Passwords are hashed with BCrypt (strength 12). Login does one hash comparison whether or not the
  account exists, so response time does not reveal which emails are registered.
- Login and registration are rate limited per client address.

## Consequences

- Reloading the page keeps the user signed in without exposing a long-lived token to scripts.
- A copied refresh token stops working after its first use, and replaying it ends all sessions.
- Registering with a taken email still returns 409, which reveals that an account exists. Fixing that
  properly needs email verification, which is a later phase.
- Rate limits are per address and held in memory, so they reset when the server restarts.