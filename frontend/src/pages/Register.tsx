import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../components/Toast";
import { ButtonSpinner } from "../components/Spinner";
import { Icon } from "../components/Icon";

const PASSWORD_MIN = 8;

export function Register() {
  const { register } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
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
    else if (password.length < PASSWORD_MIN) {
      fe.password = `Password must be at least ${PASSWORD_MIN} characters`;
    } else if (password.length > 72) {
      fe.password = "Password must not exceed 72 characters";
    }
    if (confirm !== password) fe.confirm = "Passwords do not match";
    if (Object.keys(fe).length > 0) {
      setFieldErrors(fe);
      return;
    }

    setSubmitting(true);
    try {
      const user = await register(email.trim(), password);
      notify("success", `Account created for ${user.email}. Please log in.`);
      navigate("/login", { replace: true });
    } catch (err) {
      setError((err as Error).message || "Registration failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <div className="auth-card__head">
          <p className="auth-card__sys">
            <Icon name="user" /> Create your account
          </p>
          <h1 className="auth-card__title">Get started</h1>
          <p className="auth-card__sub">Start creating shorter, smarter links.</p>
        </div>

        <form className="form" onSubmit={onSubmit} noValidate>
          <div className="form__field">
            <label htmlFor="reg-email" className="form__label">
              Email
            </label>
            <input
              id="reg-email"
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
            <label htmlFor="reg-password" className="form__label">
              Password
            </label>
            <div className="password-field">
              <input
                id="reg-password"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                className="input"
                data-invalid={fieldErrors.password ? "true" : undefined}
                placeholder={`At least ${PASSWORD_MIN} characters`}
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

          <div className="form__field">
            <label htmlFor="reg-confirm" className="form__label">
              Confirm password
            </label>
            <div className="password-field">
              <input
                id="reg-confirm"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                className="input"
                data-invalid={fieldErrors.confirm ? "true" : undefined}
                placeholder="Re-enter your password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
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
            {fieldErrors.confirm ? <p className="form__error">{fieldErrors.confirm}</p> : null}
          </div>

          {error ? (
            <div className="form__alert" role="alert">
              {error}
            </div>
          ) : null}

          <div className="form__actions">
            <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
              {submitting ? <ButtonSpinner /> : "Create account"}
            </button>
          </div>
        </form>

        <p className="auth-card__foot">
          Already have an account? <Link to="/login">Sign in</Link>.
        </p>
      </div>
    </div>
  );
}