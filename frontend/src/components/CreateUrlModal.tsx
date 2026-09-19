import { useRef, useState } from "react";
import type { FormEvent } from "react";
import { api } from "../lib/api";
import type { CreateUrlResponse } from "../lib/types";
import { useToast } from "./Toast";
import { Modal } from "./Modal";
import { ButtonSpinner } from "./Spinner";
import { Icon } from "./Icon";

interface CreateUrlModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (url: CreateUrlResponse) => void;
}

export function CreateUrlModal({ open, onClose, onCreated }: CreateUrlModalProps) {
  const { notify } = useToast();
  const [originalUrl, setOriginalUrl] = useState("");
  const [customAlias, setCustomAlias] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState<CreateUrlResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const copyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const resetForm = () => {
    setOriginalUrl("");
    setCustomAlias("");
    setExpiresAt("");
    setErrors({});
    setSubmitting(false);
  };

  const handleClose = () => {
    setCreated(null);
    resetForm();
    if (copyTimer.current) clearTimeout(copyTimer.current);
    onClose();
  };

  const doCreate = async (e: FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    setErrors({});

    const localErrors: Record<string, string> = {};
    if (!originalUrl.trim()) localErrors.originalUrl = "Please enter a link to shorten";
    else if (!/^https?:\/\//i.test(originalUrl.trim())) {
      localErrors.originalUrl = "URL must start with http:// or https://";
    }
    if (customAlias.trim() && !/^[A-Za-z0-9_-]{3,12}$/.test(customAlias.trim())) {
      localErrors.customAlias = "Alias must be 3–12 letters, digits, - or _";
    }
    if (expiresAt && isNaN(new Date(expiresAt).getTime())) {
      localErrors.expiresAt = "Invalid expiration date";
    }
    if (Object.keys(localErrors).length > 0) {
      setErrors(localErrors);
      return;
    }

    setSubmitting(true);
    try {
      const res = await api.post<CreateUrlResponse>("/api/v1/urls", {
        originalUrl: originalUrl.trim(),
        customAlias: customAlias.trim() || null,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      });
      setCreated(res);
      onCreated(res);
      notify("success", "Redirect created successfully");
    } catch (err) {
      const message = (err as Error).message || "Failed to create URL";
      notify("error", message);
      setErrors({ form: message });
    } finally {
      setSubmitting(false);
    }
  };

  const copyShortUrl = async () => {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.shortUrl);
      setCopied(true);
      if (copyTimer.current) clearTimeout(copyTimer.current);
      copyTimer.current = setTimeout(() => setCopied(false), 2000);
      notify("success", "Short URL copied to clipboard");
    } catch {
      notify("error", "Could not copy — copy it manually");
    }
  };

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title={created ? "Link created" : "Create a short link"}
      width="md"
    >
      {created ? (
        <div className="success-card">
          <span className="success-card__check" aria-hidden="true">
            <Icon name="link" />
          </span>

          <p className="success-card__title">Link created</p>
          <p className="success-card__sub">
            Your new short link is ready. Copy it, share it, or track clicks from the dashboard.
          </p>

          <p className="success-card__label">Original URL</p>
          <a
            className="success-card__original"
            href={created.originalUrl}
            target="_blank"
            rel="noreferrer noopener"
            title={created.originalUrl}
          >
            {created.originalUrl}
          </a>

          <p className="success-card__label">Short link</p>
          <a
            className="success-card__url"
            href={created.shortUrl}
            target="_blank"
            rel="noreferrer noopener"
          >
            {created.shortUrl}
          </a>

          <span className="success-card__status">
            <span className="status-dot" aria-hidden="true" />
            Active
          </span>

          <div className="success-card__actions">
            <button type="button" className="btn btn--primary" onClick={copyShortUrl}>
              <Icon name="copy" /> {copied ? "Copied" : "Copy"}
            </button>
            <a
              className="btn btn--ghost"
              href={created.shortUrl}
              target="_blank"
              rel="noreferrer noopener"
            >
              <Icon name="external" /> Open
            </a>
          </div>
          <button type="button" className="btn btn--text" onClick={handleClose}>
            Done
          </button>
        </div>
      ) : (
<form className="form" onSubmit={doCreate} noValidate>
          <div className="form__field">
            <label htmlFor="originalUrl" className="form__label">
              Link to shorten <span className="req">*</span>
            </label>
            <input
              id="originalUrl"
              type="url"
              className="input"
              data-invalid={errors.originalUrl ? "true" : undefined}
              placeholder="https://example.com/very/long/path"
              value={originalUrl}
              onChange={(e) => setOriginalUrl(e.target.value)}
              autoFocus
            />
            {errors.originalUrl ? <p className="form__error">{errors.originalUrl}</p> : null}
          </div>

          <div className="form__field">
            <label htmlFor="customAlias" className="form__label">
              Custom alias <span className="muted">( optional )</span>
            </label>
            <input
              id="customAlias"
              type="text"
              className="input"
              data-invalid={errors.customAlias ? "true" : undefined}
              placeholder="my-link (3–12 letters/digits/-_)"
              value={customAlias}
              maxLength={12}
              onChange={(e) => setCustomAlias(e.target.value)}
            />
            {errors.customAlias ? <p className="form__error">{errors.customAlias}</p> : null}
          </div>

          <div className="form__field">
            <label htmlFor="expiresAt" className="form__label">
              Expiration <span className="muted">( optional )</span>
            </label>
            <input
              id="expiresAt"
              type="datetime-local"
              className="input"
              data-invalid={errors.expiresAt ? "true" : undefined}
              value={expiresAt}
              min={new Date(Date.now() + 60000).toISOString().slice(0, 16)}
              onChange={(e) => setExpiresAt(e.target.value)}
            />
            {errors.expiresAt ? <p className="form__error">{errors.expiresAt}</p> : null}
          </div>

          {errors.form ? (
            <div className="form__alert" role="alert">
              {errors.form}
            </div>
          ) : null}

          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={handleClose} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? <ButtonSpinner /> : <>Create link</>}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
