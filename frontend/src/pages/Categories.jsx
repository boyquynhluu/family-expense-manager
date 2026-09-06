import { useEffect, useState } from "react";
import client from "../api/client";

export default function Categories() {
  const [categories, setCategories] = useState([]);
  const [name, setName] = useState("");
  const [type, setType] = useState("EXPENSE");
  const [icon, setIcon] = useState("");
  const [color, setColor] = useState("#4f46e5");
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/categories").then((res) => setCategories(res.data.data));
  }

  useEffect(load, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      await client.post("/expenses/categories", { name, type, icon: icon || null, color: color || null });
      setName("");
      setIcon("");
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Tạo danh mục thất bại");
    }
  }

  return (
    <div>
      <h1>Danh mục</h1>
      <form className="inline-form" onSubmit={handleSubmit}>
        <input placeholder="Tên danh mục" value={name} onChange={(e) => setName(e.target.value)} required />
        <select value={type} onChange={(e) => setType(e.target.value)}>
          <option value="EXPENSE">Chi tiêu</option>
          <option value="INCOME">Thu nhập</option>
        </select>
        <input placeholder="Icon (tuỳ chọn)" value={icon} onChange={(e) => setIcon(e.target.value)} />
        <input type="color" value={color} onChange={(e) => setColor(e.target.value)} />
        <button type="submit">Thêm danh mục</button>
      </form>
      {error && <p className="error-text">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Tên</th>
            <th>Loại</th>
            <th>Màu</th>
          </tr>
        </thead>
        <tbody>
          {categories.map((c) => (
            <tr key={c.id}>
              <td>{c.name}</td>
              <td>{c.type === "EXPENSE" ? "Chi tiêu" : "Thu nhập"}</td>
              <td>
                {c.color && <span className="color-dot" style={{ backgroundColor: c.color }} />}
                {c.color}
              </td>
            </tr>
          ))}
          {categories.length === 0 && (
            <tr>
              <td colSpan={3}>Chưa có danh mục nào</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
