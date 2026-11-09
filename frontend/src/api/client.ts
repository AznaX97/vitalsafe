// All backend calls go through here. The access token is kept in memory only.

export interface Session {
  token: string;
  email: string;
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  /** A value sent as JSON. */
  json?: unknown;
  /** Text sent as it is (for example a CSV file), together with its content type. */
  body?: string;
  contentType?: string;
}

let accessToken: string | null = null;
let onUnauthorized: (() => void) | null = null;
let refreshPromise: Promise<Session | null> | null = null;

export class ApiError extends Error {
  status: number;
  fields?: Record<string, string>;
  /** The error body the server sent, when it was JSON. Import errors keep their row details here. */
  body?: unknown;

  constructor(status: number, message: string, fields?: Record<string, string>, body?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fields = fields;
    this.body = body;
  }
}

export function setToken(token: string | null): void {
  accessToken = token;
}

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

/**
 * Asks the server for a new access token using the HttpOnly refresh cookie. Parallel callers share one request,
 * because a refresh token can only be used once and a second use would look like token theft.
 */
export function refreshSession(): Promise<Session | null> {
  if (!refreshPromise) {
    refreshPromise = fetch("/api/v1/auth/refresh", { method: "POST" })
      .then(async (response) => {
        if (!response.ok) {
          return null;
        }
        const session = (await response.json()) as Session;
        accessToken = session.token;
        return session;
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function serverLogout(): Promise<void> {
  try {
    await fetch("/api/v1/auth/logout", { method: "POST" });
  } catch {
    // The local session is cleared either way
  }
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {};
  let body: string | undefined;
  if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.json);
  } else if (options.body !== undefined) {
    headers["Content-Type"] = options.contentType ?? "text/plain";
    body = options.body;
  }
  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }
  try {
    return await fetch(`/api/v1${path}`, { method: options.method ?? "GET", headers, body });
  } catch {
    throw new ApiError(0, "Cannot reach the server. Is the backend running?");
  }
}

// Sends the request. When the access token has expired, refreshes once and retries once.
// A failed login has no token, so it never takes this path.
async function request(path: string, options: RequestOptions): Promise<Response> {
  let response = await send(path, options);
  if (response.status === 401 && accessToken) {
    const session = await refreshSession();
    if (session) {
      response = await send(path, options);
    } else {
      onUnauthorized?.();
    }
  }
  return response;
}

async function failure(response: Response): Promise<ApiError> {
  let data: unknown;
  try {
    data = await response.json();
  } catch {
    data = undefined;
  }
  const body = (data ?? {}) as { error?: string; fields?: Record<string, string> };
  return new ApiError(response.status, body.error ?? "Request failed", body.fields, data ?? undefined);
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await request(path, options);
  if (!response.ok) {
    throw await failure(response);
  }
  if (response.status === 204) {
    return null as T;
  }
  try {
    return (await response.json()) as T;
  } catch {
    return null as T;
  }
}

/** Fetches a file, with the same sign-in handling as every other call, and reads its suggested name. */
export async function apiDownload(path: string): Promise<{ blob: Blob; filename: string }> {
  const response = await request(path, {});
  if (!response.ok) {
    throw await failure(response);
  }
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  return { blob: await response.blob(), filename: match?.[1] ?? "download" };
}

/** A message that is safe to show: field problems when there are any, otherwise the server's message. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.fields && Object.keys(error.fields).length > 0) {
      return Object.values(error.fields).join(" ");
    }
    return error.message;
  }
  return "Something went wrong";
}