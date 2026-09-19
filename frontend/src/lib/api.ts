/**
 * Central API client.
 *
 * - Reads the backend base URL from VITE_API_BASE_URL (see .env.example).
 * - Attaches the JWT as `Authorization: Bearer <token>` when present.
 * - Parses backend ApiError bodies into `ApiErrorError` (carries status + message
 *   + optional per-field errors).
 * - Lets callers opt out of the 401 auto-logout by passing `skipAuthRedirect`.
 *
 * Do not hard-code API URLs in pages/components/hooks — always go through here.
 */

import { getToken } from "./auth";
import type { ApiError } from "./types";

export const API_BASE_URL: string =
  (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/+$/, "") ??
  "http://localhost:8080";

/** Error thrown for any non-2xx response; wraps the backend ApiError body. */
export class ApiErrorError extends Error {
  status: number;
  apiError: ApiError | null;

  constructor(status: number, message: string, apiError: ApiError | null = null) {
    super(message);
    this.name = "ApiErrorError";
    this.status = status;
    this.apiError = apiError;
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "DELETE" | "PUT";
  body?: unknown;
  /** Not used for cors-with-credentials (we use the Authorization header). */
  useAuth?: boolean;
  /** When true, a 401 will NOT clear the token / caller handles it. */
  skipAuthRedirect?: boolean;
}

/** Called on 401 when the caller does not opt out of auto-logout. */
export function setOnUnauthorized(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}
let unauthorizedHandler: (() => void) | null = null;

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const {
    method = "GET",
    body,
    useAuth = true,
    skipAuthRedirect = false,
  } = options;

  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = getToken();
  if (useAuth && token) headers["Authorization"] = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (err) {
    throw new ApiErrorError(0, "Network error — is the backend running?", null);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let apiError: ApiError | null = null;
  if (text) {
    try {
      apiError = JSON.parse(text) as ApiError;
    } catch {
      apiError = null;
    }
  }

  if (!response.ok) {
    if (response.status === 401 && !skipAuthRedirect && unauthorizedHandler) {
      unauthorizedHandler();
    }
    const message =
      apiError?.message && apiError.message.length > 0
        ? apiError.message
        : `Request failed (${response.status})`;
    throw new ApiErrorError(response.status, message, apiError);
  }

  // 204 has no body.
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "POST", body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "DELETE" }),
  del: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "DELETE" }),
};