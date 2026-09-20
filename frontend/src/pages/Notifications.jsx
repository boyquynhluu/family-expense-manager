import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { BellIcon, TrashIcon } from "../components/AppIcons";
import Pagination from "../components/Pagination";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";

export default function Notifications() {
  const { t } = useTranslation("notifications");
  const { pageData, setPage, reload } = usePagedList("/notifications");
  const notifications = pageData.content;
  const [unreadCount, setUnreadCount] = useState(0);
  const [error, setError] = useState("");
  const [preferences, setPreferences] = useState([]);
  const [savingPreferences, setSavingPreferences] = useState(false);

  // "Mark all as read" must reflect unread items on every page, not just the visible
  // one, so the count comes from the dedicated unread-count endpoint.
  function loadUnreadCount() {
    client
      .get("/notifications/unread-count")
      .then((res) => setUnreadCount(res.data.data.count))
      .catch(() => {});
  }

  function refresh() {
    reload();
    loadUnreadCount();
  }

  useEffect(() => {
    loadUnreadCount();
  }, []);

  useEffect(() => {
    client
      .get("/notifications/preferences")
      .then((res) => setPreferences(res.data.data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    // Polling instead of a persistent connection keeps this simple and matches the
    // rest of the app's request/response style — good enough at this app's scale.
    const interval = setInterval(refresh, 15_000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reload]);

  async function markAsRead(id) {
    setError("");
    try {
      await client.put(`/notifications/${id}/read`);
    } catch (err) {
      setError(err.response?.data?.message || t("markReadFailed"));
    }
    refresh();
  }

  async function markAllAsRead() {
    setError("");
    try {
      await client.put("/notifications/read-all");
    } catch (err) {
      setError(err.response?.data?.message || t("markAllReadFailed"));
    }
    refresh();
  }

  async function handleDelete(event, id) {
    event.stopPropagation();
    if (!(await confirmDialog(t("deleteConfirm")))) return;
    try {
      await client.delete(`/notifications/${id}`);
      toast.success(t("deleted"));
    } catch (err) {
      toast.error(err.response?.data?.message || t("deleteFailed"));
    }
    refresh();
  }

  async function handleDeleteRead() {
    if (!(await confirmDialog(t("deleteReadConfirm")))) return;
    try {
      await client.delete("/notifications/read");
      toast.success(t("deletedRead"));
    } catch (err) {
      toast.error(err.response?.data?.message || t("deleteReadFailed"));
    }
    refresh();
  }

  function updatePreference(type, field, value) {
    setPreferences((prev) => prev.map((p) => (p.type === type ? { ...p, [field]: value } : p)));
  }

  async function savePreferences() {
    setSavingPreferences(true);
    try {
      const body = preferences.map(({ type, inAppEnabled, emailEnabled }) => ({
        type,
        inAppEnabled,
        emailEnabled,
      }));
      const res = await client.put("/notifications/preferences", body);
      setPreferences(res.data.data);
      toast.success(t("preferencesSaved"));
      refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || t("preferencesSaveFailed"));
    } finally {
      setSavingPreferences(false);
    }
  }

  const hasUnread = unreadCount > 0;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("title")}</h1>
          <p className="page-header-subtitle">{t("subtitle")}</p>
        </div>
        <div className="row-actions">
          {hasUnread && (
            <button type="button" className="btn-secondary" onClick={markAllAsRead}>
              {t("markAllReadButton")}
            </button>
          )}
          {pageData.totalElements > 0 && (
            <button type="button" className="btn-secondary" onClick={handleDeleteRead}>
              {t("deleteReadButton")}
            </button>
          )}
        </div>
      </div>

      {error && <p className="error-text">{error}</p>}

      {notifications.length === 0 ? (
        <div className="section-card">
          <p className="empty-state">{t("noNotifications")}</p>
        </div>
      ) : (
        <ul className="notification-list">
          {notifications.map((n) => (
            <li
              key={n.id}
              className={n.isRead ? "read" : "unread"}
              onClick={() => !n.isRead && markAsRead(n.id)}
            >
              <span className="notification-icon">
                <BellIcon />
              </span>
              <div className="notification-body">
                <div className="notification-title-row">
                  <strong>{n.title}</strong>
                  {!n.isRead && <span className="notification-dot" />}
                </div>
                <p>{n.message}</p>
              </div>
              <button
                type="button"
                className="icon-btn icon-btn-danger"
                onClick={(event) => handleDelete(event, n.id)}
                aria-label={t("common:delete")}
              >
                <TrashIcon />
              </button>
            </li>
          ))}
        </ul>
      )}
      <Pagination pageData={pageData} onPageChange={setPage} />

      {preferences.length > 0 && (
        <div className="section-card notification-preferences">
          <h2>{t("preferencesTitle")}</h2>
          <p className="page-header-subtitle">{t("preferencesSubtitle")}</p>
          <ul className="notification-preferences-list">
            {preferences.map((p) => (
              <li key={p.type}>
                <span className="notification-preferences-label">{t(`types.${p.type}`)}</span>
                <label>
                  <input
                    type="checkbox"
                    checked={p.inAppEnabled}
                    onChange={(e) => updatePreference(p.type, "inAppEnabled", e.target.checked)}
                  />
                  {t("preferenceInApp")}
                </label>
                {p.emailSupported && (
                  <label>
                    <input
                      type="checkbox"
                      checked={p.emailEnabled}
                      onChange={(e) => updatePreference(p.type, "emailEnabled", e.target.checked)}
                    />
                    {t("preferenceEmail")}
                  </label>
                )}
              </li>
            ))}
          </ul>
          <button type="button" onClick={savePreferences} disabled={savingPreferences}>
            {savingPreferences ? t("common:saving") : t("preferencesSave")}
          </button>
        </div>
      )}
    </div>
  );
}
