# VitalSafe

**A privacy-first vitals tracker that stores your readings encrypted and exchanges them as standard FHIR.**

> **Status:** in development.
>
> **Disclaimer:** VitalSafe records and charts the readings you enter. It does not diagnose, treat or give medical advice, and it is not for emergencies. Talk to a health professional about your readings.

## Planned features

- Log blood pressure, heart rate, weight, blood glucose, temperature and oxygen saturation
- Trends and charts, with target ranges you set yourself
- CSV import and FHIR R4 export
- Encrypted health data, rotating sessions, rate limiting, consent record

## Tech

Java 21, Spring Boot, PostgreSQL, HAPI FHIR, React, TypeScript, Docker. All free and open source.

## Working on this repo

Changes go through pull requests from short-lived branches into `main`. Commit messages follow
[conventional commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).

## Local setup

1. `cp .env.example .env` (PowerShell: `Copy-Item .env.example .env`) and fill in `DB_PASSWORD`, `JWT_SECRET` and `ENCRYPTION_KEY` (how to generate a key: `docs/decisions/0003-field-encryption.md`)
2. `docker compose up -d --wait` starts PostgreSQL on port 5442
3. Start the API on port 8090:
   - Windows: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-backend.ps1`
   - Other: export the variables from `.env`, then `cd backend && ./mvnw spring-boot:run`
4. Health check: http://localhost:8090/actuator/health

## Tests

`cd backend && ./mvnw verify` (Docker must be running: the tests start a real PostgreSQL with Testcontainers).git 
## Security notes

- Passwords are hashed with BCrypt and never stored or logged.
- Access tokens last 15 minutes. Refresh tokens are rotated on every use and stored only as hashes.
- Login and registration are rate limited per client address.
- The API must only be reachable through the reverse proxy in production, because the client address is
  taken from the forwarded header.
`cd backend && ./mvnw verify` (Docker must be running: the tests start a real PostgreSQL with Testcontainers).
## API (so far)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | No | Create an account. Requires `"consent": true`, which is recorded with a timestamp and notice version |
| POST | `/api/v1/auth/login` | No | Log in. Returns an access token and sets the refresh cookie |
| POST | `/api/v1/auth/refresh` | Cookie | Rotate the refresh token and get a new access token |
| POST | `/api/v1/auth/logout` | Cookie | Revoke the refresh token and clear the cookie |
| GET | `/api/v1/me` | Bearer | The signed-in user |
| GET | `/actuator/health` | No | Liveness and readiness |


