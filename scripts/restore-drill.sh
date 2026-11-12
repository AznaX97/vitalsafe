#!/usr/bin/env bash
# Proves a backup works: restores it into a scratch database and compares row counts with the live database.
# Usage: bash scripts/restore-drill.sh backups/vitalsafe-YYYYMMDD-HHMMSS.dump
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."

file="${1:?Give the backup file to test, for example backups/vitalsafe-20261101-020000.dump}"
[ -f "$file" ] || { echo "No such file: $file"; exit 1; }

COMPOSE=(docker compose -f docker-compose.prod.yml --env-file .env.prod)
psql_in() { "${COMPOSE[@]}" exec -T db psql -U vitalsafe_user -At "$@"; }
cleanup() {
  psql_in -d postgres -c "DROP DATABASE IF EXISTS vitalsafe_restore_test" >/dev/null 2>&1 || true
  "${COMPOSE[@]}" exec -T db rm -f /tmp/restore.dump >/dev/null 2>&1 || true
}
trap cleanup EXIT

psql_in -d postgres -c "DROP DATABASE IF EXISTS vitalsafe_restore_test" >/dev/null
psql_in -d postgres -c "CREATE DATABASE vitalsafe_restore_test" >/dev/null
"${COMPOSE[@]}" cp "$file" db:/tmp/restore.dump
"${COMPOSE[@]}" exec -T db pg_restore -U vitalsafe_user -d vitalsafe_restore_test --no-owner /tmp/restore.dump

failures=0
for table in users vital_readings target_ranges refresh_tokens flyway_schema_history; do
  live=$(psql_in -d vitalsafe_db -c "select count(*) from $table")
  restored=$(psql_in -d vitalsafe_restore_test -c "select count(*) from $table")
  if [ "$live" = "$restored" ]; then
    echo "PASS  $table: $restored rows"
  else
    echo "FAIL  $table: live has $live rows, the restore has $restored"
    failures=$((failures + 1))
  fi
done

plain=$(psql_in -d vitalsafe_restore_test -c "select count(*) from vital_readings where value_primary not like 'v1.%'")
if [ "$plain" = "0" ]; then echo "PASS  every restored reading value is still encrypted"; else echo "FAIL  $plain readings are not encrypted"; failures=$((failures + 1)); fi

echo
if [ "$failures" -eq 0 ]; then echo "The backup restores correctly."; else echo "$failures check(s) failed."; fi
[ "$failures" -eq 0 ]