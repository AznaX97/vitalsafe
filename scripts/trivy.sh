#!/usr/bin/env bash
# Runs Trivy in Docker (CI only) with a cache, a long timeout, and retries when the failure is a download problem.
# A real finding is not retried: it ends the run straight away.
# Usage: bash scripts/trivy.sh <trivy arguments>
set -u
cache="${TRIVY_CACHE_DIR:-$HOME/.cache/trivy}"
mkdir -p "$cache"
ignore=()
[ -f .trivyignore.yaml ] && ignore=(--ignorefile /work/.trivyignore.yaml)
log=$(mktemp)

for attempt in 1 2 3; do
  docker run --rm \
    -v /var/run/docker.sock:/var/run/docker.sock -v "$cache:/root/.cache/" -v "$PWD:/work" -w /work \
    aquasec/trivy:latest "$@" --timeout 15m "${ignore[@]}" 2>&1 | tee "$log"
  code=${PIPESTATUS[0]}
  [ "$code" -eq 0 ] && exit 0
  if grep -q "FATAL" "$log"; then
    echo "Trivy could not run (attempt $attempt of 3). Waiting before trying again."
    sleep 20
  else
    exit "$code"
  fi
done
exit 1