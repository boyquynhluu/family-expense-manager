import { useEffect, useState } from "react";
import client from "../api/client";
import { EditIcon, TrashIcon } from "../components/AppIcons";
import { useAuth } from "../hooks/useAuth";

export default function Categories() {
  const { role } = useAuth();
  const isOwner = role === "OWNER";
  const [categories, setCategories] = useState([]);
  const [name, setName] = useState("");
  const [type, setType] = useState("EXPENSE");
  const [icon, setIcon] = useState("");
  const [color, setColor] = useState("#4f46e5");
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/categories").then((res) => setCategories(res.data.data));
  }

  useEffect(load, []);

  function startEdit(category) {
    setEditingId(category.id);
    setName(category.name);
    setType(category.type);
    setIcon(category.icon ?? "");
    setColor(category.color ?? "#4f46e5");
  }

  function cancelEdit() {
    setEditingId(null);
    setName("");
    setType("EXPENSE");
    setIcon("");
    setColor("#4f46e5");
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    const payload = { name, type, icon: icon || null, color: color || null };
    try {
      if (editingId) {
        await client.put(`/expenses/categories/${editingId}`, payload);
      } else {
        await client.post("/expenses/categories", payload);
      }
      cancelEdit();
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Lưu danh mục thất bại");
    }
  }

  async function handleDelete(id) {
    if (!window.confirm("Xoá danh mục này? Chỉ xoá được khi chưa có giao dịch hoặc ngân sách nào dùng danh mục này."))
      return;
    setError("");
    try {
      await client.delete(`/expenses/categories/${id}`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Xoá danh mục thất bại");
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Danh mục</h1>
          <p className="page-header-subtitle">Nhóm các khoản thu/chi để dễ theo dõi</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? "Cập nhật danh mục" : "Thêm danh mục mới"}</h2>
        <form className="inline-form" onSubmit={handleSubmit}>
          <label className="field">
            Tên danh mục
            <input placeholder="VD: Ăn uống" value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label className="field">
            Loại
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="EXPENSE">Chi tiêu</option>
              <option value="INCOME">Thu nhập</option>
            </select>
          </label>
          <label className="field">
            Icon (tuỳ chọn)
            <input placeholder="VD: 🍔" value={icon} onChange={(e) => setIcon(e.target.value)} />
          </label>
          <label className="field">
            Màu
            <input type="color" value={color} onChange={(e) => setColor(e.target.value)} />
          </label>
          <button type="submit">{editingId ? "Cập nhật" : "Thêm danh mục"}</button>
          {editingId && (
            <button type="button" className="btn-secondary" onClick={cancelEdit}>
              Huỷ
            </button>
          )}
        </form>
        {error && <p className="error-text">{error}</p>}
      </div>

      <div className="section-card">
        <h2>Danh sách danh mục</h2>
        {categories.length === 0 ? (
          <p className="empty-state">Chưa có danh mục nào</p>
        ) : (
          <div className="category-chip-grid">
            {categories.map((c) => (
              <div className="category-chip" key={c.id}>
                <span className="category-chip-icon" style={{ backgroundColor: c.color || "#9ca3af" }}>
                  {c.icon || c.name.charAt(0).toUpperCase()}
                </span>
                <div className="category-chip-body">
                  <span className="category-chip-name">{c.name}</span>
                  <span className={`badge ${c.type === "EXPENSE" ? "badge-expense" : "badge-income"}`}>
                    {c.type === "EXPENSE" ? "Chi tiêu" : "Thu nhập"}
                  </span>
                </div>
                <div className="row-actions">
                  <button type="button" className="icon-btn" onClick={() => startEdit(c)} aria-label="Sửa">
                    <EditIcon />
                  </button>
                  {isOwner && (
                    <button
                      type="button"
                      className="icon-btn icon-btn-danger"
                      onClick={() => handleDelete(c.id)}
                      aria-label="Xoá"
                    >
                      <TrashIcon />
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
