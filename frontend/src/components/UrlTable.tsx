import { useRef, useState } from "react";
import type { UrlResponse } from "../lib/types";
import { useToast } from "./Toast";
import { displayUrl, formatDate, isExpired } from "../lib/urlUtil";
import { Icon } from "./Icon";

interface UrlTableProps {
  items: UrlResponse[];
  onDelete: (url: UrlResponse) => void;
  onAnalytics: (url: UrlResponse) => void;
}

export function UrlTable({ items, onDelete, onAnalytics }: UrlTableProps) {
  const { notify } = useToast();
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const copy = async (shortUrl: string, id: number) => {
    try {
      await navigator.clipboard.writeText(shortUrl);
      setCopiedId(id);
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => setCopiedId(null), 2000);
      notify("success", "Copied to clipboard");
    } catch {
      notify("error", "Could not copy");
    }
  };

  if (items.length === 0) {
    return (
      <div className="empty-state">
        <span className="empty-state__icon" aria-hidden="true">
          <Icon name="link" />
        </span>
        <p className="empty-state__title">No links yet</p>
        <p className="empty-state__sub">
          Create your first short link and start tracking clicks. It takes seconds.
        </p>
      </div>
    );
  }

  return (
    <div className="table-scroll">
      <table className="url-table">
        <thead>
          <tr>
            <th>Short link</th>
            <th>Target URL</th>
            <th>Status</th>
            <th>Created</th>
            <th>Expires</th>
            <th className="url-table__actions-col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {items.map((u, idx) => {
            const expired = isExpired(u.expiresAt);
            const code = u.shortUrl.replace(/^https?:\/\/[^/]+/, "");
            return (
              <tr key={u.id} className={expired ? "url-table__row--expired" : ""}>
                <td>
                  <div className="cell-short">
                    <span className="cell-short__code">
                      <span aria-hidden="true" style={{ color: "var(--text-4)" }}>
                        {String(idx + 1).padStart(2, "0")}
                      </span>
                      <a
                        href={u.shortUrl}
                        target="_blank"
                        rel="noreferrer noopener"
                        title={`${u.shortUrl} → ${u.originalUrl}`}
                      >
                        {code}
                      </a>
                    </span>
                  </div>
                </td>
                <td className="cell-original" title={u.originalUrl}>
                  {displayUrl(u.originalUrl)}
                </td>
                <td className="cell-status">
                  <span className={`badge ${expired ? "badge--danger" : "badge--active"}`}>
                    {expired ? "Expired" : "Active"}
                  </span>
                  {u.customAlias ? (
                    <span style={{ marginLeft: 6 }}>
                      <span className="badge badge--alias">alias</span>
                    </span>
                  ) : null}
                </td>
                <td className="cell-date">{formatDate(u.createdAt)}</td>
                <td className="cell-date">{formatDate(u.expiresAt)}</td>
                <td>
                  <div className="row-actions">
                    <button
                      type="button"
                      className={`icon-btn icon-btn--copy${copiedId === u.id ? " copied" : ""}`}
                      onClick={() => copy(u.shortUrl, u.id)}
                      title="Copy short URL"
                      aria-label={`Copy short URL ${u.shortCode}`}
                    >
                      {copiedId === u.id ? <Icon name="link" /> : <Icon name="copy" />}
                    </button>
                    <button
                      type="button"
                      className="icon-btn"
                      onClick={() => onAnalytics(u)}
                      title="View click analytics"
                      aria-label={`View analytics for ${u.shortCode}`}
                    >
                      <Icon name="chart" />
                    </button>
                    <button
                      type="button"
                      className="icon-btn icon-btn--danger"
                      onClick={() => onDelete(u)}
                      title="Delete this short URL"
                      aria-label={`Delete ${u.shortCode}`}
                    >
                      <Icon name="trash" />
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}