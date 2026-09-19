import { Link } from "react-router-dom";

export function NotFound() {
  return (
    <div className="page page--center">
      <div className="not-found">
        <h1 className="not-found__code">404</h1>
        <p className="not-found__msg">We couldn’t find that page.</p>
        <Link to="/" className="btn btn--primary">
          Return home
        </Link>
      </div>
    </div>
  );
}