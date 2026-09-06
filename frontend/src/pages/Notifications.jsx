import { useEffect, useState } from "react";
import client from "../api/client";

export default function Notifications() {
  const [notifications, setNotifications] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    client
      .get("/notifications")
      .then((res) => setNotifications(res.data.data))
      .catch((err) => setError(err.response?.data?.message || "Không tải được thông báo"));
  }, []);

  return (
    <div>
      <h1>Thông báo</h1>
      {error && <p className="error-text">{error}</p>}
      <ul className="notification-list">
        {notifications.map((n) => (
          <li key={n.id} className={n.isRead ? "read" : "unread"}>
            <strong>{n.title}</strong>
            <p>{n.message}</p>
          </li>
        ))}
        {notifications.length === 0 && <li>Chưa có thông báo nào</li>}
      </ul>
    </div>
  );
}
