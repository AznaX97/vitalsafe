// All backend calls go through here. The access token is kept in memory only.

export interface Session {
  token: string;
  email: string;
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  json?: unknown;
}

let accessToken: string | null = null;
let onUnauthorized: (() => void) | null = null;
let refreshPromise: Promise<Session | null> | null = null;

export class ApiError extends Error {
  status: number;
  fields?: Record<string, string>;

  constructor(status: number, message: string, fields?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fields = fields;
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
  if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }
  try {
    return await fetch(`/api/v1${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.json !== undefined ? JSON.stringify(options.json) : undefined,
    });
  } catch {
    throw new ApiError(0, "Cannot reach the server. Is the backend running?");
  }
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await send(path, options);

  // The access token expired: refresh once, then retry once. A failed login has no token, so it never gets here.
  if (response.status === 401 && accessToken) {
    const session = await refreshSession();
    if (session) {
      response = await send(path, options);
    } else {
      onUnauthorized?.();
    }
  }

  let data: unknown = null;
  if (response.status !== 204) {
    try {
      data = await response.json();
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    const body = (data ?? {}) as { error?: string; fields?: Record<string, string> };
    throw new ApiError(response.status, body.error ?? "Request failed", body.fields);
  }
  return data as T;
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