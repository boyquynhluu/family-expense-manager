import { useEffect, useState } from "react";
import client from "../api/client";
import { BellIcon } from "../components/AppIcons";

export default function Notifications() {
  const [notifications, setNotifications] = useState([]);
  const [error, setError] = useState("");

  function load() {
    client
      .get("/notifications")
      .then((res) => setNotifications(res.data.data))
      .catch((err) => setError(err.response?.data?.message || "Không tải được thông báo"));
  }

  useEffect(load, []);

  async function markAsRead(id) {
    setNotifications((list) => list.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
    try {
      await client.put(`/notifications/${id}/read`);
    } catch (err) {
      setError(err.response?.data?.message || "Không đánh dấu được thông báo đã đọc");
      load();
    }
  }

  async function markAllAsRead() {
    setNotifications((list) => list.map((n) => ({ ...n, isRead: true })));
    try {
      await client.put("/notifications/read-all");
    } catch (err) {
      setError(err.response?.data?.message || "Không đánh dấu được tất cả đã đọc");
      load();
    }
  }

  const hasUnread = notifications.some((n) => !n.isRead);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Thông báo</h1>
          <p className="page-header-subtitle">Cảnh báo vượt ngân sách và các cập nhật khác</p>
        </div>
        {hasUnread && (
          <button type="button" className="btn-secondary" onClick={markAllAsRead}>
            Đánh dấu tất cả đã đọc
          </button>
        )}
      </div>

      {error && <p className="error-text">{error}</p>}

      {notifications.length === 0 ? (
        <div className="section-card">
          <p className="empty-state">Chưa có thông báo nào</p>
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
