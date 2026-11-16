# Backups

## What a backup holds

Everything in the database: email addresses, password hashes, reading types and times, and the encrypted values and
notes. Treat the file as sensitive. Store it encrypted, off the server, and keep `ENCRYPTION_KEY` in a different
place from the backups. **A backup without the key cannot be read, and the key without a backup is useless.**

## Make one

    bash scripts/backup-db.sh

The file lands in `backups/` (ignored by Git). On Windows run it with Git Bash:
`& "C:\Program Files\Git\bin\bash.exe" scripts/backup-db.sh`.

## Test one (do this, a backup you never restored is only a hope)

    bash scripts/restore-drill.sh backups/vitalsafe-YYYYMMDD-HHMMSS.dump

It restores into a scratch database, compares every table with the live one, checks that readings are still
encrypted, and removes the scratch database. The same drill runs in CI on every pull request.

## Automatically, on a server

With cron, daily at 02:00, keeping 14 days: `0 2 * * * cd /home/deploy/vitalsafe && bash scripts/backup-db.sh && find backups -name '*.dump' -mtime +14 -delete`.
Then copy the files somewhere else, because a backup that lives only on the server is lost with the server.

## Restore for real

Stop the stack, restore into the live database with `pg_restore` (see the drill script for the commands), start the
stack again, and check the app. Practise the drill first, and never discover the process during an emergency.