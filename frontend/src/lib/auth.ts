/**
 * JWT storage + decode helpers.
 *
 * The access token is kept out of the UI (never rendered) and stored in
 * localStorage under a single namespaced key. A 401 from the backend clears it
 * and the router returns the user to /login.
 */

const TOKEN_KEY = "urlshortener.accessToken";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export function isAuthenticated(): boolean {
  const token = getToken();
  if (!token) return false;
  const exp = getTokenExpiry(token);
  if (exp === null) return true; // no exp claim (shouldn't happen) → assume valid
  // Treat as expired slightly early to avoid racing the exact boundary.
  return exp * 1000 > Date.now() + 5000;
}

/**
 * Decodes the `exp` claim from a JWT payload without verification.
 * Only used for local UX decisions (e.g. pre-emptively clearing a token
 * known to be expired); the backend is always the authority.
 */
export function getTokenExpiry(token: string): number | null {
  try {
    const payload = token.split(".")[1];
    const json = JSON.parse(atob(payload));
    return typeof json?.exp === "number" ? json.exp : null;
  } catch {
    return null;
  }
}

/**
 * Best-effort email from the JWT `email` claim (the backend sets
 * `claim("email", user.getEmail())` — sub is the numeric user id). Never throws.
 */
export function getTokenEmail(token: string): string | null {
  try {
    const payload = token.split(".")[1];
    const json = JSON.parse(atob(payload));
    const email = json?.email;
    return typeof email === "string" && email.length > 0 ? email : null;
  } catch {
    return null;
  }
}

/** Best-effort numeric user id from the JWT `sub` claim. */
export function getTokenUserId(token: string): number | null {
  try {
    const payload = token.split(".")[1];
    const json = JSON.parse(atob(payload));
    const sub = json?.sub;
    if (typeof sub !== "string" || sub.length === 0) return null;
    const id = Number(sub);
    return Number.isFinite(id) ? id : null;
  } catch {
    return null;
  }
}