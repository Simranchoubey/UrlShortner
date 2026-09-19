import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useParams } from "react-router-dom";
import { api } from "../lib/api";
import type { UrlAnalyticsResponse, UrlResponse } from "../lib/types";
import { useToast } from "../components/Toast";
import { Spinner } from "../components/Spinner";
import { formatDate, isExpired } from "../lib/urlUtil";
import { Icon } from "../components/Icon";

export function AnalyticsPage() {
  const { id } = useParams<{ id: string }>();
  const location = useLocation() as { state?: { url?: UrlResponse } };
  const { notify } = useToast();

  const initial = location.state?.url;
  const [url, setUrl] = useState<UrlResponse | null>(initial ?? null);
  const [analytics, setAnalytics] = useState<UrlAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(!initial);
  const [analyticsLoading, setAnalyticsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const isExpiredFlag = useMemo(() => isExpired(url?.expiresAt), [url?.expiresAt]);

  const load = useCallback(async () => {
    if (!id) return;
    setError(null);

    let current: UrlResponse | null = url;
    if (!current) {
      setLoading(true);
      try {
        current = await api.get<UrlResponse>(`/api/v1/urls/${id}`);
        setUrl(current);
      } catch (err) {
        setError((err as Error).message || "Could not load this URL");
        setLoading(false);
        return;
      } finally {
        setLoading(false);
      }
    }

    setAnalyticsLoading(true);
    try {
      const res = await api.get<UrlAnalyticsResponse>(`/api/v1/urls/${id}/analytics`);
      setAnalytics(res);
    } catch (err) {
      notify("error", (err as Error).message || "Could not load analytics");
    } finally {
      setAnalyticsLoading(false);
    }
  }, [id, url, notify]);

  useEffect(() => {
    load();
  }, [id]);

  if (loading) {
    return (
      <div className="page page--center">
        <Spinner label="Loading analytics…" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="page page--center">
        <div className="panel panel--error">
          <p role="alert">{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="page page--narrow">
      <p className="page__kicker">Analytics</p>
      <div className="analytics">
        <div className="analytics__head">
          <h1 className="code">{url?.shortCode ?? "--"}</h1>
          {url?.customAlias ? <span className="badge badge--alias">alias</span> : null}
        </div>

        {url ? (
          <>
            <div className="analytics__url">
              <div>
                <p className="analytics__url-label">Short link</p>
                <a
                  className="analytics__short"
                  href={url.shortUrl}
                  target="_blank"
                  rel="noreferrer noopener"
                  title={`${url.shortUrl} → ${url.originalUrl}`}
                >
                  <Icon name="external" /> {url.shortUrl}
                </a>
                <p className="analytics__original" title={url.originalUrl}>
                  redirects to {url.originalUrl}
                </p>
              </div>
              <div className="analytics__badges">
                {isExpiredFlag ? (
                  <span className="badge badge--expired">expired</span>
                ) : (
                  <span className="badge badge--active">active</span>
                )}
              </div>
            </div>

            <div className="analytics__card analytics__card--feature">
              {analyticsLoading ? (
                <div className="analytics__loading">
                  <Spinner />
                </div>
              ) : (
                <div>
                  <p className="analytics__metric-label">Total clicks</p>
                  <p className="analytics__value">{analytics?.totalClicks ?? 0}</p>
                  <p className="analytics__hint">
                    <span className="analytics__hint-icon">
                      <Icon name="chart" />
                    </span>
                    Every click on this short link, counted.
                  </p>
                </div>
              )}
            </div>

            <dl className="analytics__meta">
              <div className="analytics__meta-row">
                <dt>Short code</dt>
                <dd>{url.shortCode}</dd>
              </div>
              <div className="analytics__meta-row">
                <dt>Created</dt>
                <dd>{formatDate(url.createdAt)}</dd>
              </div>
              <div className="analytics__meta-row">
                <dt>Expires</dt>
                <dd>{formatDate(url.expiresAt)}</dd>
              </div>
            </dl>
          </>
        ) : null}
      </div>
    </div>
  );
}