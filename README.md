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
| POST | `/api/v1/auth/forgot-password` | none | Ask for a password reset link by email (always answers 202) |
| POST | `/api/v1/auth/reset-password` | none | Choose a new password with the emailed token |
| POST | `/api/v1/account/password` | Bearer | Change your password (ends all sessions) |
| POST | `/api/v1/account/delete` | Bearer | Delete your account and all its data (needs your password) |
| POST | `/api/v1/vitals` | Bearer | Add a reading |
| GET | `/api/v1/vitals` | Bearer | List your readings, newest first (`type`, `from`, `to`, `page`, `size` up to 100) |
| GET | `/api/v1/vitals/{id}` | Bearer | One reading |
| PUT | `/api/v1/vitals/{id}` | Bearer | Replace a reading |
| DELETE | `/api/v1/vitals/{id}` | Bearer | Delete a reading |
| POST | `/api/v1/vitals/import` | Bearer | Import readings from a CSV file (`Content-Type: text/csv`, 512 KB and 1,000 rows maximum, all or nothing) |
| GET | `/api/v1/vitals/export` | Bearer | Export your readings as CSV (optionally `from` and `to`) |
| GET | `/api/v1/vitals/summary` | Bearer | Statistics and trends for one type (`type` required; `unit`, `from`, `to`, `zone`, `bucket`) |
| GET | `/api/v1/vitals/{id}/fhir` | Bearer | One reading as a FHIR R4 Observation |
| GET | `/api/v1/fhir/export` | Bearer | All your readings (optionally `from` and `to`) as a FHIR Bundle |
| POST | `/api/v1/vitals/fhir` | Bearer | Import a FHIR Observation as a reading |
| GET | `/api/v1/targets` | Bearer | Your target ranges |
| PUT | `/api/v1/targets/{type}` | Bearer | Set or replace your range for one kind of reading |
| DELETE | `/api/v1/targets/{type}` | Bearer | Remove a range (`unit` is needed for glucose) |
| GET | `/actuator/health` | No | Liveness and readiness |



## Decisions

- [0001 FHIR approach](docs/decisions/0001-fhir-approach.md)
- [0002 Authentication](docs/decisions/0002-authentication.md)
- [0003 Field encryption](docs/decisions/0003-field-encryption.md)
- [0004 Vitals model](docs/decisions/0004-vitals-model.md)
- [0005 FHIR mapping](docs/decisions/0005-fhir-mapping.md)
- [0006 Target ranges](docs/decisions/0006-target-ranges.md)
- [0007 Summaries in the application](docs/decisions/0007-summaries-in-the-application.md)
- [0008 CSV import and export](docs/decisions/0008-csv-import-and-export.md)

## Interactive API docs

Set `OPENAPI_ENABLED=true` in `.env` (never anywhere public) and start the backend, then open
http://localhost:8090/swagger-ui/index.html. Log in through `POST /api/v1/auth/login`, copy the `token`, press
**Authorize** and paste it. The raw description is at `/v3/api-docs`. With the switch off, both return 401.
- [0008 CSV import and export](docs/decisions/0008-csv-import-and-export.md)

## Metrics

`/actuator/prometheus` is guarded by `METRICS_TOKEN` (32 or more characters, from `.env`). A user's login token
does not open it, and with no token set it stays closed. See [docs/monitoring.md](docs/monitoring.md) for the
local Prometheus and Grafana dashboards.

## Frontend

React, TypeScript and Vite, in `frontend/`. Start the backend first, then:

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173 (the dev server proxies /api to the backend on port 8090)
npm test         # unit and component tests
npm run build    # type check and production build
```

The access token is kept in memory only, and the refresh cookie is HttpOnly. See
[docs/decisions/0009-frontend-sessions.md](docs/decisions/0009-frontend-sessions.md).
## End-to-end tests

`cd frontend && npm run e2e:infra && npm run e2e` starts its own database (port 5443), backend (8091) and frontend
(5174), so your development setup is not touched. Stop the test database with `npm run e2e:down`. They also run in CI.
## Production stack

`docker-compose.prod.yml` runs PostgreSQL, the backend and Caddy (automatic HTTPS) with only ports 80 and 443 open.
How to run it locally, put it on a server, update it and go back: [docs/deployment.md](docs/deployment.md).
## Backups and security scans

[docs/backups.md](docs/backups.md) explains making and testing database backups. Secret and vulnerability scans run
on every pull request and weekly ([.github/workflows/security.yml](.github/workflows/security.yml)).
Before real users: [docs/release-checklist.md](docs/release-checklist.md).
- [0011 Account lifecycle](docs/decisions/0011-account-lifecycle.md)
- [Email](docs/mail.md)
- [0011 Account lifecycle](docs/decisions/0011-account-lifecycle.md)
- [Email](docs/mail.md)
- [0011 Account lifecycle](docs/decisions/0011-account-lifecycle.md)
- [Email](docs/mail.md)
