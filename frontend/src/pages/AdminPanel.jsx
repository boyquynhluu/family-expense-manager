import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import client from "../api/client";
import { useAuth } from "../hooks/useAuth";

function formatDate(value) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}

export default function AdminPanel() {
  const { userId } = useAuth();
  const [families, setFamilies] = useState([]);
  const [users, setUsers] = useState([]);
  const [error, setError] = useState("");

  function load() {
    client
      .get("/admin/families")
      .then((res) => setFamilies(res.data.data))
      .catch((err) => setError(err.response?.data?.message || "Không tải được danh sách gia đình"));
    client
      .get("/admin/users")
      .then((res) => setUsers(res.data.data))
      .catch((err) => setError(err.response?.data?.message || "Không tải được danh sách người dùng"));
  }

  useEffect(load, []);

  async function toggleSystemAdmin(user) {
    const nextValue = !user.isSystemAdmin;
    const confirmMessage = nextValue
      ? `Cấp quyền admin toàn hệ thống cho ${user.email}?`
      : `Gỡ quyền admin toàn hệ thống của ${user.email}?`;
    if (!window.confirm(confirmMessage)) return;
    try {
      await client.put(`/admin/users/${user.id}/system-admin`, { isSystemAdmin: nextValue });
      toast.success("Đã cập nhật quyền admin");
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || "Cập nhật thất bại");
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Quản trị hệ thống</h1>
          <p className="page-header-subtitle">Xem toàn bộ gia đình và người dùng, cấp quyền admin</p>
        </div>
      </div>

      {error && <p className="error-text">{error}</p>}

      <div className="section-card">
        <h2>Danh sách gia đình ({families.length})</h2>
        {families.length === 0 ? (
          <p className="empty-state">Chưa có gia đình nào</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Tên gia đình</th>
                <th>Chủ hộ</th>
                <th>Số thành viên</th>
                <th>Ngày tạo</th>
              </tr>
            </thead>
            <tbody>
              {families.map((f) => (
                <tr key={f.id}>
                  <td>{f.name}</td>
                  <td>
                    {f.ownerDisplayName} ({f.ownerEmail})
                  </td>
                  <td>{f.memberCount}</td>
                  <td>{formatDate(f.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="section-card">
        <h2>Danh sách người dùng ({users.length})</h2>
        {users.length === 0 ? (
          <p className="empty-state">Chưa có người dùng nào</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Email</th>
                <th>Tên hiển thị</th>
                <th>Gia đình</th>
                <th>Vai trò</th>
                <th>Trạng thái</th>
                <th>Admin hệ thống</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.email}</td>
                  <td>{u.displayName}</td>
                  <td>{u.familyName ?? `#${u.familyId}`}</td>
                  <td>{u.role}</td>
                  <td>
                    <span className={`badge ${u.active ? "badge-income" : "badge-expense"}`}>
                      {u.active ? "Đã kích hoạt" : "Chưa kích hoạt"}
                    </span>
                  </td>
                  <td>{u.isSystemAdmin ? "Có" : "Không"}</td>
                  <td className="row-actions">
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => toggleSystemAdmin(u)}
                      disabled={String(u.id) === String(userId) && u.isSystemAdmin}
                    >
                      {u.isSystemAdmin ? "Gỡ quyền admin" : "Cấp quyền admin"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
