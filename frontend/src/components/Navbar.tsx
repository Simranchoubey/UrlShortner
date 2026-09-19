import { useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { ThemeToggle } from "./ThemeToggle";
import { Icon } from "./Icon";

export function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  const handleLogout = () => {
    logout();
    setMenuOpen(false);
    navigate("/login");
  };

  const initials = user?.email
    ? user.email
        .split("@")[0]
        .slice(0, 2)
        .toUpperCase()
    : "?";

  return (
    <header className="navbar">
      <div className="navbar__inner">
        <Link to="/" className="navbar__brand" onClick={() => setMenuOpen(false)}>
          <span className="navbar__brand-mark" aria-hidden="true">
            /
          </span>
          <span>
            <span className="navbar__brand-name">SHORT&#47;&#47;LINK</span>
            <span className="navbar__brand-sub">URL Infrastructure</span>
          </span>
        </Link>

        <div className="navbar__right">
          <span className="navbar__status">
            <span className="status-dot" aria-hidden="true" />
            Online
          </span>

          <ThemeToggle />

          {user ? (
            <div className={`navbar__user navbar__desktop-user`}>
              <span className="navbar__email" title={user.email}>
                {user.email}
              </span>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                onClick={handleLogout}
                aria-label="Log out"
              >
                Log out
              </button>
            </div>
          ) : (
            <nav className={`navbar__nav navbar__desktop-nav`} aria-label="Main">
              <NavLink
                to="/login"
                className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}
              >
                Log in
              </NavLink>
              <NavLink to="/register" className="btn btn--primary btn--sm">
                Get started
              </NavLink>
            </nav>
          )}

          <button
            type="button"
            className="navbar__toggle"
            aria-label="Toggle menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((s) => !s)}
          >
            <Icon name={menuOpen ? "logout" : "menu"} />
          </button>
        </div>

        {/* Mobile dropdown nav */}
        <nav
          className={`navbar__nav navbar__dropdown${menuOpen ? " is-open" : ""}`}
          aria-label="Mobile navigation"
        >
          {user ? (
            <>
              <NavLink
                to="/dashboard"
                className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}
                onClick={() => setMenuOpen(false)}
              >
                <Icon name="link" /> Dashboard
              </NavLink>
              <div className="navbar__user">
                <span className="navbar__user-avatar" aria-hidden="true">
                  {initials}
                </span>
                <span className="navbar__email" title={user.email}>
                  {user.email}
                </span>
                <button
                  type="button"
                  className="btn btn--secondary btn--block-mobile"
                  onClick={handleLogout}
                >
                  <Icon name="logout" /> Log out
                </button>
              </div>
            </>
          ) : (
            <>
              <NavLink
                to="/login"
                className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}
                onClick={() => setMenuOpen(false)}
              >
                Log in
              </NavLink>
              <NavLink
                to="/register"
                className="btn btn--primary"
                onClick={() => setMenuOpen(false)}
              >
                Get started
              </NavLink>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}