#!/usr/bin/env bash
# Checks a running production stack from the outside.
# Usage: bash scripts/smoke-test.sh [base-url] [--register]
# --register also creates a throwaway account, so only use it on a stack that holds no real data.
set -u
BASE="${1:-https://localhost}"
REGISTER="${2:-}"
HOST="${BASE#https://}"
failures=0

check() {   # name, exit status of the test before it, detail
  if [ "$2" -eq 0 ]; then
    echo "PASS  $1"
  else
    echo "FAIL  $1   ($3)"
    failures=$((failures + 1))
  fi
}

# -k accepts the local certificate that Caddy makes for localhost
code() { curl -ksS -o /dev/null -w '%{http_code}' --max-time 15 "$@"; }

echo "Waiting for the stack at $BASE ..."
for _ in $(seq 1 60); do
  [ "$(code -X POST "$BASE/api/v1/auth/refresh")" = "401" ] && break
  sleep 3
done

status=$(code "$BASE/");                       [ "$status" = "200" ]; check "the home page is served" $? "status $status"
body=$(curl -ksS --max-time 15 "$BASE/login"); echo "$body" | grep -q 'id="root"'; check "a deep link falls back to the app" $? "no app root found"

headers=$(curl -ksS -D - -o /dev/null --max-time 15 "$BASE/")
echo "$headers" | grep -qi "^content-security-policy:.*default-src 'self'";  check "the page sets a content security policy" $? "header missing"
echo "$headers" | grep -qi "^x-content-type-options: nosniff";               check "nosniff is set" $? "header missing"
echo "$headers" | grep -qi "^x-frame-options: DENY";                         check "framing is refused" $? "header missing"
echo "$headers" | grep -qi "^referrer-policy: no-referrer";                  check "no referrer is sent" $? "header missing"
echo "$headers" | grep -qi "^cache-control: no-cache";                       check "the home page is never cached" $? "header missing"
echo "$headers" | grep -qi "^server:";                                       [ $? -ne 0 ]; check "the server name is hidden" $? "a Server header is present"

redirect=$(curl -ksS -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 15 "http://$HOST/")
case "$redirect" in 30[78]\ https://*) ok=0 ;; *) ok=1 ;; esac;             check "plain HTTP is redirected to HTTPS" $ok "got: $redirect"

status=$(code "$BASE/api/v1/me");              [ "$status" = "401" ]; check "the API needs a login" $? "status $status"
status=$(code "$BASE/api/v1/vitals");          [ "$status" = "401" ]; check "readings need a login" $? "status $status"

for path in /actuator/health /actuator/prometheus /v3/api-docs /swagger-ui/index.html; do
  status=$(code "$BASE$path"); [ "$status" = "404" ]; check "$path is not reachable from outside" $? "status $status"
done

if [ "$REGISTER" = "--register" ]; then
  email="smoke-$(date +%s)-$RANDOM@example.com"
  password="Smoke-$(date +%s)-Passw0rd"
  jar=$(mktemp)
  headers=$(curl -ksS -D - -o /dev/null -c "$jar" --max-time 30 -X POST -H 'Content-Type: application/json' \
    --data "{\"email\":\"$email\",\"password\":\"$password\",\"consent\":true}" "$BASE/api/v1/auth/register")
  echo "$headers" | head -n 1 | grep -q ' 201';                                           check "registering works" $? "$(echo "$headers" | head -n 1)"
  cookie=$(echo "$headers" | grep -i '^set-cookie: refresh_token=')
  echo "$cookie" | grep -qi 'httponly';                                                   check "the session cookie is HttpOnly" $? "flag missing"
  echo "$cookie" | grep -qi 'secure';                                                     check "the session cookie is Secure" $? "flag missing"
  echo "$cookie" | grep -qi 'samesite=strict';                                            check "the session cookie is SameSite=Strict" $? "flag missing"
  echo "$cookie" | grep -qi 'path=/api/v1/auth';                                          check "the session cookie is limited to the auth path" $? "path wrong"
  status=$(code -b "$jar" -X POST "$BASE/api/v1/auth/refresh");                           [ "$status" = "200" ]; check "the session can be refreshed" $? "status $status"
  rm -f "$jar"
fi

echo
if [ "$failures" -eq 0 ]; then echo "All checks passed."; else echo "$failures check(s) failed."; fi
[ "$failures" -eq 0 ]