import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../lib/api";
import type { CreateUrlResponse, UrlListResponse, UrlResponse } from "../lib/types";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../components/Toast";
import { Spinner } from "../components/Spinner";
import { CreateUrlModal } from "../components/CreateUrlModal";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { UrlTable } from "../components/UrlTable";
import { Icon } from "../components/Icon";
import { isExpired } from "../lib/urlUtil";

const DEFAULT_SIZE = 10;

export function Dashboard() {
  const { user } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();

  const [data, setData] = useState<UrlListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [deleting, setDeleting] = useState<UrlResponse | null>(null);
  const [deletingBusy, setDeletingBusy] = useState(false);

  const loadPage = useCallback(async (pageIndex: number) => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<UrlListResponse>(
        `/api/v1/urls?page=${pageIndex}&size=${DEFAULT_SIZE}`,
      );
      setData(res);
      setPage(res.page);
    } catch (err) {
      setError((err as Error).message || "Failed to load your URLs");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPage(0);
  }, [loadPage]);

  const handleCreated = useCallback(
    (_created: CreateUrlResponse) => {
      setCreateOpen(false);
      loadPage(0);
    },
    [loadPage],
  );

  const confirmDelete = async () => {
    if (!deleting) return;
    setDeletingBusy(true);
    try {
      await api.delete(`/api/v1/urls/${deleting.id}`);
      notify("success", "Short link deleted");
      setDeleting(null);
      const count = data?.totalElements ?? 0;
      const nextPage = count > 1 && page > 0 && data?.items.length === 1 ? page - 1 : page;
      loadPage(nextPage);
    } catch (err) {
      notify("error", (err as Error).message || "Failed to delete URL");
    } finally {
      setDeletingBusy(false);
    }
  };

  // All stats are derived strictly from the real paginated list + expiry dates.
  const totalPages = data?.totalPages ?? 0;
  const totalLinks = data?.totalElements ?? 0;
  const rowsOnPage = data?.items.length ?? 0;
  const activeOnPage = useMemo(
    () => (data?.items ?? []).filter((u) => !isExpired(u.expiresAt)).length,
    [data],
  );

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <p className="page__kicker">Dashboard</p>
          <h1 className="page__title">
            {user ? <>Welcome back, {user.email.split("@")[0]}</> : "Your links"}
          </h1>
          <p className="page__sub">Manage your links and track their performance.</p>
        </div>
        <button type="button" className="btn btn--primary" onClick={() => setCreateOpen(true)}>
          <Icon name="plus" /> Create link
        </button>
      </div>
      <div className="dash__stats">
        <div className="stat-card">
          <div>
            <span className="stat-card__label">Total links</span>
            <div className="stat-card__value">{totalLinks}</div>
            <span className="stat-card__sub">across your account</span>
          </div>
          <span className="stat-flag" aria-hidden="true" />
        </div>
        <div className="stat-card">
          <div>
            <span className="stat-card__label">Active on this page</span>
            <div className="stat-card__value">{activeOnPage}</div>
            <span className="stat-card__sub">not expired</span>
          </div>
          <span className="stat-flag stat-flag--success" aria-hidden="true" />
        </div>
        <div className="stat-card">
          <div>
            <span className="stat-card__label">Showing</span>
            <div className="stat-card__value">{rowsOnPage}</div>
            <span className="stat-card__sub">{totalPages > 1 ? `${totalPages} pages` : "one page"}</span>
          </div>
          <span className="stat-flag" aria-hidden="true" />
        </div>
      </div>

      {loading && !data ? (
        <div className="panel panel--loading">
          <Spinner label="Loading your links…" />
        </div>
      ) : error && !data ? (
        <div className="panel panel--error">
          <p role="alert">{error}</p>
          <button type="button" className="btn btn--secondary" onClick={() => loadPage(page)}>
            Retry
          </button>
        </div>
      ) : data ? (
        <>
          <div className="panel">
            <div className="panel__header">
              <span className="panel__title">
                <Icon name="link" /> Your links
              </span>
              <span className="panel__meta">
                {totalPages > 1 ? `Page ${page + 1} of ${totalPages}` : `${totalLinks} total`}
              </span>
            </div>
            <UrlTable
              items={data.items}
              onDelete={(u) => setDeleting(u)}
              onAnalytics={(u) => navigate(`/urls/${u.id}/analytics`, { state: { url: u } })}
            />
          </div>

          {totalPages > 1 ? (
            <div className="pagination">
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={data.first || loading}
                onClick={() => loadPage(page - 1)}
              >
                ‹ Prev
              </button>
              <span className="pagination__info">
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={data.last || loading}
                onClick={() => loadPage(page + 1)}
              >
                Next ›
              </button>
            </div>
          ) : null}
        </>
      ) : null}

      <CreateUrlModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={handleCreated}
      />

      <ConfirmDialog
        open={deleting !== null}
        title="Delete this link?"
        message={
          deleting ? (
            <>
              Are you sure you want to delete{" "}
              <strong className="confirm__code">{deleting.shortCode}</strong>? This action cannot
              be undone. The original URL is not affected.
            </>
          ) : null
        }
        confirmLabel="Delete link"
        busy={deletingBusy}
        onConfirm={confirmDelete}
        onClose={() => {
          if (!deletingBusy) setDeleting(null);
        }}
      />
    </div>
  );
}
