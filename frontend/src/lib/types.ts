/**
 * TypeScript contracts mirroring the backend DTOs exactly.
 * Source of truth: com.example.urlshortener.dto.*
 * See README + the backend OpenAPI (GET /v3/api-docs).
 */

/* ---------- Auth ---------- */

export interface RegisterRequest {
  email: string;
  password: string;
}

/** Backend: RegisterResponse { id, email } */
export interface RegisterResponse {
  id: number;
  email: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/** Backend: LoginResponse { accessToken, tokenType } */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
}

/* ---------- URLs ---------- */

export interface CreateUrlRequest {
  originalUrl: string;
  /** Optional, 3–12 URL-safe chars [A-Za-z0-9_-]. */
  customAlias?: string | null;
  /** Optional future expiry timestamp (ISO-8601). */
  expiresAt?: string | null;
}

/** Backend: CreateUrlResponse */
export interface CreateUrlResponse {
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  customAlias: string | null;
  expiresAt: string | null;
}

/** Backend: UrlResponse (single/list item) */
export interface UrlResponse {
  id: number;
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  customAlias: string | null;
  expiresAt: string | null;
  createdAt: string | null;
}

/** Backend: UrlListResponse (paginated) */
export interface UrlListResponse {
  items: UrlResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Backend: UrlAnalyticsResponse */
export interface UrlAnalyticsResponse {
  totalClicks: number;
}

/* ---------- Errors ---------- */

/**
 * Backend: ApiError { timestamp, status, error, message, errors }.
 * `errors` carries per-field validation messages (key = field, value = problem).
 */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  errors: Record<string, string> | null;
}