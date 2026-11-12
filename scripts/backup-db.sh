#!/usr/bin/env bash
# Makes a compressed PostgreSQL backup of the production stack into ./backups.
# The dump is made inside the container and copied out, because shell redirection can corrupt a binary file on Windows.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # stops Git Bash on Windows from rewriting /tmp paths
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -f docker-compose.prod.yml --env-file .env.prod)
mkdir -p backups
chmod 700 backups
file="backups/vitalsafe-$(date +%Y%m%d-%H%M%S).dump"

"${COMPOSE[@]}" exec -T db pg_dump -U vitalsafe_user -d vitalsafe_db -Fc -f /tmp/backup.dump
"${COMPOSE[@]}" cp db:/tmp/backup.dump "$file"
"${COMPOSE[@]}" exec -T db rm -f /tmp/backup.dump
chmod 600 "$file"

echo "Backup written to $file ($(wc -c < "$file") bytes)"
echo "It holds email addresses and password hashes, so keep it private. The readings in it are encrypted."