import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import Swal from "sweetalert2";
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
    const result = await confirmUpdate(confirmMessage);
    if(!result.isConfirmed) return;
    try {
      await client.put(`/admin/users/${user.id}/system-admin`, { isSystemAdmin: nextValue });
      toast.success("Đã cập nhật quyền admin");
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || "Cập nhật thất bại");
    }
  }

  async function confirmUpdate(confirmMessage) {
    return Swal.fire({
        title: "Cập nhật quyền Admin?",
        text: confirmMessage,
        icon: "warning",
        showCancelButton: true,

        confirmButtonText: "Đồng ý",
        cancelButtonText: "Hủy",

        customClass: {
            popup: "custom-swal-popup",
            title: "custom-swal-title",
            htmlContainer: "custom-swal-text",
            confirmButton: "custom-swal-confirm",
            cancelButton: "custom-swal-cancel",
            icon: "custom-swal-icon",
        },

        buttonsStyling: false,
    });
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
                  <td data-label="Tên gia đình">{f.name}</td>
                  <td data-label="Chủ hộ">
                    {f.ownerDisplayName} ({f.ownerEmail})
                  </td>
                  <td data-label="Số thành viên">{f.memberCount}</td>
                  <td data-label="Ngày tạo">{formatDate(f.createdAt)}</td>
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
                  <td data-label="Email">{u.email}</td>
                  <td data-label="Tên hiển thị">{u.displayName}</td>
                  <td data-label="Gia đình">{u.familyName ?? `#${u.familyId}`}</td>
                  <td data-label="Vai trò">{u.role}</td>
                  <td data-label="Trạng thái">
                    <span className={`badge ${u.active ? "badge-income" : "badge-expense"}`}>
                      {u.active ? "Đã kích hoạt" : "Chưa kích hoạt"}
                    </span>
                  </td>
                  <td data-label="Admin hệ thống">{u.isSystemAdmin ? "Có" : "Không"}</td>
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
