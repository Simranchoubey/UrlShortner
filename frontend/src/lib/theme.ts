/**
 * Light / dark theme management.
 *
 * - The resolved theme is written to the <html data-theme="..."> attribute,
 *   which every CSS rule keys off (see index.css :root[data-theme=...]).
 * - The user's choice is persisted to localStorage so it survives reloads.
 * - On first visit (no saved preference) the system scheme is used. An inline
 *   script in index.html applies that initial theme before React mounts to
 *   avoid any flash, and this module stays in sync with the same key.
 * - When the user has not explicitly chosen, live changes to the OS scheme are
 *   followed automatically.
 */

import { useEffect, useState } from "react";

export type Theme = "light" | "dark";

const STORAGE_KEY = "shortlink.theme";

const media = window.matchMedia("(prefers-color-scheme: light)");

/** Single source of truth that reads the current DOM state. */
export function getTheme(): Theme {
  return document.documentElement.getAttribute("data-theme") === "light"
    ? "light"
    : "dark";
}

export function getSystemTheme(): Theme {
  return media.matches ? "light" : "dark";
}

function setMetaThemeColor(theme: Theme): void {
  const meta = document.getElementById("meta-theme");
  if (meta) meta.setAttribute("content", theme === "dark" ? "#0a0a0c" : "#f7f7f9");
}

export function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute("data-theme", theme);
  setMetaThemeColor(theme);
}

export function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    /* storage may be unavailable (private mode) — ignore */
  }
}

export function clearSavedTheme(): void {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore */
  }
}

export function hasSavedTheme(): boolean {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === "light" || v === "dark";
  } catch {
    return false;
  }
}

/** Subscribes to OS scheme changes; returns an unsubscribe function. */
export function listenToSystemTheme(handler: (theme: Theme) => void): () => void {
  const listener = () => handler(media.matches ? "light" : "dark");
  media.addEventListener("change", listener);
  return () => media.removeEventListener("change", listener);
}

/**
 * React hook giving the current theme plus a setter. Persists the choice; when
 * the user clears their preference it follows the system and stays in sync.
 */
export function useTheme(): { theme: Theme; toggleTheme: () => void } {
  const [theme, setTheme] = useState<Theme>(() => getTheme());

  useEffect(() => {
    applyTheme(theme);
    // If the user has no explicit preference, follow live system changes.
    if (!hasSavedTheme()) {
      return listenToSystemTheme((system) => {
        applyTheme(system);
        setTheme(system);
      });
    }
  }, [theme]);

  const toggleTheme = () => {
    const next = getTheme() === "light" ? "dark" : "light";
    saveTheme(next);
    setTheme(next);
  };

  return { theme, toggleTheme };
}