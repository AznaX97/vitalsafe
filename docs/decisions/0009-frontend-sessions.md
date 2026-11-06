# 0009: How the frontend handles sessions and cached data

Status: accepted

## Decision

- The access token is held in a module variable. It is never written to `localStorage`, `sessionStorage` or a
  cookie that scripts can read, and it is lost on a page reload.
- After a reload, the app asks `POST /api/v1/auth/refresh`. The browser sends the HttpOnly refresh cookie, so
  the person stays signed in without any long-lived secret being readable from page scripts.
- When a request answers 401 while signed in, the client refreshes once and retries once. Parallel refreshes
  share one request, because refresh tokens are single use and a second use would look like token theft.
- Logging out, or a failed refresh, clears the query cache. Health data held in memory must not survive for
  the next person using the same browser.
- All requests go to `/api/v1/...` on the same origin. In development Vite proxies them to the backend.

## Consequences

- A reload briefly shows a loading state while the session is restored.
- Several tabs share the refresh cookie. A refresh in one tab retires the cookie value another tab holds, so
  a second tab can be signed out when it next refreshes.
- Production needs a reverse proxy that serves the app and `/api` from one origin.