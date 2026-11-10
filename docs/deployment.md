# Deployment

## Run the production stack on your own PC (free)

1. Free ports 80 and 443 (stop IIS, Skype or anything else using them).
2. Create the secrets once: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\new-prod-env.ps1`
   and save the new `.env.prod` in a password manager. The script refuses to overwrite an existing file.
3. Start it: `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build`
4. Open https://localhost. The browser warns about Caddy's local certificate. Continue anyway, it is your own PC.
5. Check it from outside: `"C:\Program Files\Git\bin\bash.exe" scripts/smoke-test.sh` (add `--register` on a stack with no real data).
6. Stop it with `docker compose -f docker-compose.prod.yml --env-file .env.prod down`. Add `-v` only to delete all data.

## Put it on a server

You need a Linux server (Ubuntu 24.04, 2 GB of RAM or more) and a domain name that points at it. What is free and
what costs money changes, so check each provider's current terms.

1. Open only ports 22, 80 and 443 in the server's firewall and in the provider's firewall. Port 80 is needed for
   the free HTTPS certificate.
2. Install Docker, then clone this private repository with a read-only deploy key (GitHub, Settings, Deploy keys).
3. Create `.env.prod` there with `DOMAIN=your.domain`, `HSTS_MAX_AGE=300`, and freshly generated secrets.
   Never copy the secrets from your PC. Raise HSTS to 31536000 after a week of working fine.
4. `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build`, then
   `bash scripts/smoke-test.sh https://your.domain`.

## Update, and go back

- Update: `git pull`, then the same `up -d --build` command.
- Go back to an earlier release: `git checkout v0.7.0`, then `up -d --build`. Database migrations only move
  forward, so before an update that changes the database, make a backup (see docs/backups.md).

## Known limits

- One server, no automatic failover, and no alerts if it goes down.
- The images use floating major version tags such as `postgres:16-alpine`. Dependabot proposes updates, and
  you decide when to apply them.
- Password reset, changing the password, and deleting your own account do not exist yet.
- Metrics and dashboards are not part of this stack.