import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { BellIcon } from "../components/AppIcons";

export default function Notifications() {
  const { t } = useTranslation("notifications");
  const [notifications, setNotifications] = useState([]);
  const [error, setError] = useState("");

  function load() {
    client
      .get("/notifications")
      .then((res) => setNotifications(res.data.data))
      .catch((err) => setError(err.response?.data?.message || t("loadFailed")));
  }

  useEffect(() => {
    load();
    // Polling instead of a persistent connection keeps this simple and matches the
    // rest of the app's request/response style — good enough at this app's scale.
    const interval = setInterval(load, 15_000);
    return () => clearInterval(interval);
  }, []);

  async function markAsRead(id) {
    setNotifications((list) => list.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
    try {
      await client.put(`/notifications/${id}/read`);
    } catch (err) {
      setError(err.response?.data?.message || t("markReadFailed"));
      load();
    }
  }

  async function markAllAsRead() {
    setNotifications((list) => list.map((n) => ({ ...n, isRead: true })));
    try {
      await client.put("/notifications/read-all");
    } catch (err) {
      setError(err.response?.data?.message || t("markAllReadFailed"));
      load();
    }
  }

  const hasUnread = notifications.some((n) => !n.isRead);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("title")}</h1>
          <p className="page-header-subtitle">{t("subtitle")}</p>
        </div>
        {hasUnread && (
          <button type="button" className="btn-secondary" onClick={markAllAsRead}>
            {t("markAllReadButton")}
          </button>
        )}
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
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
