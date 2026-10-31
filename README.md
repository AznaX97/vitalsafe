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

```bash
cp .env.example .env     # then put a strong password in it
docker compose up -d
```

More instructions arrive with each phase.