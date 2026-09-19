import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { Spinner } from "./Spinner";

/**
 * Wraps routes that require an authenticated user. If the user is not logged in
 * they are redirected to /login, preserving the intended destination so they can
 * be sent back after authenticating.
 */
export function ProtectedRoute() {
  const { isAuthenticated } = useAuth();

  // A tiny delayed render avoids a redirect flash on initial auth-state settle.
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}

/** Full-screen loading state shown while auth is initialised. */
export function FullScreenLoading() {
  return (
    <div className="full-screen-loading">
      <Spinner label="Loading…" />
    </div>
  );
}