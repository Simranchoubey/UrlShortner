import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { ButtonSpinner } from "../components/Spinner";
import { Icon } from "../components/Icon";

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation() as { state?: { from?: string } };

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    setError(null);
    setFieldErrors({});

    const fe: Record<string, string> = {};
    if (!email.trim()) fe.email = "Email is required";
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) fe.email = "Enter a valid email";
    if (!password) fe.password = "Password is required";
    if (Object.keys(fe).length > 0) {
      setFieldErrors(fe);
      return;
    }

    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate(location.state?.from ?? "/dashboard", { replace: true });
    } catch (err) {
      setError((err as Error).message || "Login failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <div className="auth-card__head">
          <p className="auth-card__sys">
            <Icon name="lock" /> Welcome back
          </p>
          <h1 className="auth-card__title">Sign in</h1>
          <p className="auth-card__sub">Sign in to manage your short links.</p>
        </div>

        <form className="form" onSubmit={onSubmit} noValidate>
          <div className="form__field">
            <label htmlFor="login-email" className="form__label">
              Email
            </label>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              className="input"
              data-invalid={fieldErrors.email ? "true" : undefined}
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoFocus
            />
            {fieldErrors.email ? <p className="form__error">{fieldErrors.email}</p> : null}
          </div>

          <div className="form__field">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <label htmlFor="login-password" className="form__label">
                Password
              </label>
            </div>
            <div className="password-field">
              <input
                id="login-password"
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                className="input"
                data-invalid={fieldErrors.password ? "true" : undefined}
                placeholder="Your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword((s) => !s)}
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? "Hide" : "Show"}
              </button>
            </div>
            {fieldErrors.password ? <p className="form__error">{fieldErrors.password}</p> : null}
          </div>

          {error ? (
            <div className="form__alert" role="alert">
              {error}
            </div>
          ) : null}

          <div className="form__actions">
            <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
              {submitting ? <ButtonSpinner /> : "Sign in"}
            </button>
          </div>
        </form>

        <p className="auth-card__foot">
          Don’t have an account? <Link to="/register">Create one</Link>.
        </p>
        <p className="auth-card__note">
          <Icon name="shield" />
          Your session is protected. You’ll stay signed in until you log out.
        </p>
      </div>
    </div>
  );
}