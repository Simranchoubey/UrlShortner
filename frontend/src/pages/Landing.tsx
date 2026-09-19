import { useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { Icon } from "../components/Icon";

const FEATURES = [
  { icon: "link" as const, title: "Custom aliases", desc: "Claim a clean, memorable short link for every URL you shorten." },
  { icon: "clock" as const, title: "Expiring links", desc: "Set an expiry date and your link stops resolving automatically." },
  { icon: "chart" as const, title: "Click analytics", desc: "Track total clicks per link and see what resonates." },
  { icon: "key" as const, title: "Your account", desc: "Manage every link from one private dashboard." },
];

const CAPABILITIES = [
  { icon: "link" as const, label: "Custom aliases" },
  { icon: "clock" as const, label: "Expiring links" },
  { icon: "chart" as const, label: "Click analytics" },
  { icon: "key" as const, label: "Free accounts" },
];

// Local-only preview code, so the hero demo never touches the API/auth.
function makePreviewCode(input: string): string {
  const clean = input
    .toLowerCase()
    .replace(/^https?:\/\//, "")
    .replace(/[^a-z0-9]/g, "")
    .slice(0, 5);
  const rand = Math.random().toString(36).slice(2, 6);
  return (clean || "link") + rand;
}

export function Landing() {
  const { isAuthenticated } = useAuth();
  const primaryHref = isAuthenticated ? "/dashboard" : "/register";

  const [demoUrl, setDemoUrl] = useState("");
  const [demoResult, setDemoResult] = useState<string | null>(null);
  const [demoEmpty, setDemoEmpty] = useState(false);
  const demoInput = useRef<HTMLInputElement>(null);

  const runDemo = (e: FormEvent) => {
    e.preventDefault();
    const value = demoUrl.trim();
    if (!value) {
      setDemoEmpty(true);
      setDemoResult(null);
      return;
    }
    setDemoEmpty(false);
    setDemoResult(makePreviewCode(value));
  };

  return (
    <div className="landing">
      <section className="hero">
        <div className="hero__glow" aria-hidden="true" />
        <div className="hero__grid" aria-hidden="true" />

        <div className="hero__inner">
          <p className="hero__eyebrow">
            <span className="status-dot" aria-hidden="true" />
            Simple, fast URL shortener
          </p>

          <h1 className="hero__title">
            Shorten links.
            <br />
            <span className="accent">Share them. Track them.</span>
          </h1>
          <p className="hero__subtitle">
            Turn long URLs into short, shareable links with instant analytics.
          </p>
          <p className="hero__tagline">
            Paste a link below to see how it works — no sign-up needed.
          </p>

          <form className="hero__tool" onSubmit={runDemo} noValidate>
            <div className="hero__tool-field">
              <span className="hero__tool-icon" aria-hidden="true">
                <Icon name="link" />
              </span>
              <input
                ref={demoInput}
                type="url"
                className={`hero__tool-input${demoEmpty ? " shake" : ""}`}
                placeholder="https://example.com/very/long/path"
                value={demoUrl}
                aria-label="Paste a link to preview shortening"
                onChange={(e) => {
                  setDemoUrl(e.target.value);
                  if (demoEmpty) setDemoEmpty(false);
                }}
              />
              <button type="submit" className="btn hero__tool-btn">
                Shorten
              </button>
            </div>
            {demoEmpty ? (
              <p className="hero__tool-hint" role="alert">
                Paste a link to preview how shortening works.
              </p>
            ) : demoResult ? (
              <div className="hero__tool-result">
                <span className="hero__tool-result-label">Your short link</span>
                <span className="hero__tool-result-link">
                  <Link to={isAuthenticated ? "/dashboard" : "/register"}>{demoResult}</Link>
                  <span className="hero__tool-result-domain">short.link/</span>
                  <Icon name="external" />
                </span>
              </div>
            ) : (
              <p className="hero__tool-hint">Free forever. Links you create live on your dashboard.</p>
            )}
          </form>

          <div className="hero__actions">
            <Link to={primaryHref} className="btn btn--primary btn--lg">
              {isAuthenticated ? "Open dashboard" : "Start shortening"} <Icon name="link" />
            </Link>
            {!isAuthenticated ? (
              <Link to="/login" className="btn btn--secondary btn--lg">
                Log in
              </Link>
            ) : null}
          </div>

          <div className="hero__capability">
            {CAPABILITIES.map((c) => (
              <span className="cap-chip" key={c.label}>
                <Icon name={c.icon} /> {c.label}
              </span>
            ))}
          </div>
        </div>
      </section>

      <section className="features" aria-label="Features">
        {FEATURES.map((f) => (
          <div className="feature-card" key={f.title}>
            <span className="feature-card__icon">
              <Icon name={f.icon} />
            </span>
            <h3>{f.title}</h3>
            <p>{f.desc}</p>
          </div>
        ))}
      </section>
    </div>
  );
}