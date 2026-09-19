import { useTheme } from "../lib/theme";
import { Icon } from "./Icon";

/** Sun/moon icon toggle wired to the persisted light/dark theme. */
export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === "dark";

  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={toggleTheme}
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      title={isDark ? "Switch to light mode" : "Switch to dark mode"}
    >
      {isDark ? <Icon name="sun" /> : <Icon name="moon" />}
    </button>
  );
}