# Before real people use VitalSafe

## Product gaps (Phase 6)

- [ ] Change password, password reset and deleting your own account exist (the privacy notice says deletion is by request today)
- [ ] Metrics are merged and guarded by a token
- [ ] Registering a taken email still reveals that the account exists. Decide whether that is acceptable.

## Legal and trust

- [ ] The privacy notice has been read by someone qualified for where your users live (health data is regulated)
- [ ] `frontend/src/config/legal.ts` holds real details, and the consent version in `application.yml` matches the notice date
- [ ] The notice names every provider that touches data (hosting, email) once you use them

## Operations

- [ ] `.env.prod` secrets are generated on the server, and saved in a password manager
- [ ] `ENCRYPTION_KEY` is backed up separately from database backups
- [ ] A backup was made and restored with `scripts/restore-drill.sh`
- [ ] Only ports 22, 80 and 443 are open, in the server firewall and in the provider's firewall
- [ ] `bash scripts/smoke-test.sh https://your.domain` passes
- [ ] HSTS starts at 300 and is raised after a week
- [ ] Someone gets told when the server is down (not built yet)

## Each release

- [ ] CI is green: tests, end-to-end tests, production stack smoke test
- [ ] Security scans reviewed (they are not required checks, so look at them)
- [ ] Tag it with `git tag -a vX.Y.Z -m "..."`, push the tag, and publish a release from it