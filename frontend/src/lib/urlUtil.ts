/** URL display/format helpers used across the UI. */

/** True when an ISO expiry timestamp is in the past. */
export function isExpired(expiresAt?: string | null): boolean {
  if (!expiresAt) return false;
  const t = new Date(expiresAt).getTime();
  return Number.isFinite(t) && t <= Date.now();
}

/** Formats an ISO timestamp into a readable local date + time. */
export function formatDate(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return "—";
  return d.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** Truncates a long URL for table display, keeping the origin intact. */
export function displayUrl(url: string, max = 42): string {
  if (url.length <= max) return url;
  // Keep scheme://host and the first slashes, cut the tail.
  const slash = url.indexOf("/", url.indexOf("//") + 2);
  if (slash === -1 || slash >= max) return url.slice(0, max) + "…";
  const head = url.slice(0, slash);
  const tail = url.slice(slash);
  const room = max - head.length - 1;
  return head + "…" + (room > 3 ? tail.slice(0, room) : "");
}