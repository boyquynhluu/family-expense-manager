import { useEffect, useState } from "react";
import client from "../api/client";
import { EditIcon, TrashIcon } from "../components/AppIcons";
import { formatCurrency } from "../utils/format";

const emptyForm = {
  walletId: "",
  categoryId: "",
  type: "EXPENSE",
  amount: "",
  note: "",
  dayOfMonth: "1",
  startDate: "",
  endDate: "",
};

export default function RecurringTransactions() {
  const [rules, setRules] = useState([]);
  const [wallets, setWallets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/recurring-transactions").then((res) => setRules(res.data.data));
  }

  useEffect(() => {
    load();
    client.get("/expenses/wallets").then((res) => {
      setWallets(res.data.data);
      setForm((f) => ({ ...f, walletId: f.walletId || String(res.data.data[0]?.id ?? "") }));
    });
    client.get("/expenses/categories").then((res) => {
      setCategories(res.data.data);
      setForm((f) => ({ ...f, categoryId: f.categoryId || String(res.data.data[0]?.id ?? "") }));
    });
  }, []);

  function updateField(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function startEdit(rule) {
    setEditingId(rule.id);
    setForm({
      walletId: String(rule.walletId),
      categoryId: String(rule.categoryId),
      type: rule.type,
      amount: String(rule.amount),
      note: rule.note ?? "",
      dayOfMonth: String(rule.dayOfMonth),
      startDate: rule.startDate,
      endDate: rule.endDate ?? "",
    });
  }

  function cancelEdit() {
    setEditingId(null);
    setForm((f) => ({ ...emptyForm, walletId: f.walletId, categoryId: f.categoryId }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    const payload = {
      walletId: Number(form.walletId),
      categoryId: Number(form.categoryId),
      type: form.type,
      amount: Number(form.amount),
      note: form.note || null,
      dayOfMonth: Number(form.dayOfMonth),
      startDate: form.startDate,
      endDate: form.endDate || null,
    };
    try {
      if (editingId) {
        await client.put(`/expenses/recurring-transactions/${editingId}`, payload);
      } else {
        await client.post("/expenses/recurring-transactions", payload);
      }
      cancelEdit();
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Lưu giao dịch định kỳ thất bại");
    }
  }

  async function handleDelete(id) {
    if (!window.confirm("Xoá giao dịch định kỳ này? Các giao dịch đã tạo trước đó sẽ không bị xoá.")) return;
    setError("");
    try {
      await client.delete(`/expenses/recurring-transactions/${id}`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Xoá giao dịch định kỳ thất bại");
    }
  }

  async function toggleActive(rule) {
    setError("");
    try {
      await client.put(`/expenses/recurring-transactions/${rule.id}/active`, { active: !rule.active });
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Cập nhật thất bại");
    }
  }

  function walletName(id) {
    return wallets.find((w) => w.id === id)?.name ?? `#${id}`;
  }

  function categoryName(id) {
    return categories.find((c) => c.id === id)?.name ?? `#${id}`;
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Giao dịch định kỳ</h1>
          <p className="page-header-subtitle">
            Tiền nhà, internet, subscription... tự động tạo giao dịch hàng tháng, không cần nhập tay lại
          </p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? "Cập nhật giao dịch định kỳ" : "Thêm giao dịch định kỳ mới"}</h2>
        {wallets.length === 0 || categories.length === 0 ? (
          <p className="empty-state">
            {wallets.length === 0 && categories.length === 0
              ? "Cần tạo ít nhất 1 ví và 1 danh mục trước."
              : wallets.length === 0
                ? "Cần tạo ít nhất 1 ví trước."
                : "Cần tạo ít nhất 1 danh mục trước."}
          </p>
        ) : (
          <form className="inline-form" onSubmit={handleSubmit}>
            <label className="field">
              Ví
              <select value={form.walletId} onChange={(e) => updateField("walletId", e.target.value)} required>
                {wallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Danh mục
              <select value={form.categoryId} onChange={(e) => updateField("categoryId", e.target.value)} required>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Loại
              <select value={form.type} onChange={(e) => updateField("type", e.target.value)}>
                <option value="EXPENSE">Chi tiêu</option>
                <option value="INCOME">Thu nhập</option>
              </select>
            </label>
            <label className="field">
              Số tiền
              <input
                type="number"
                step="0.01"
                placeholder="0"
                value={form.amount}
                onChange={(e) => updateField("amount", e.target.value)}
                required
              />
            </label>
            <label className="field">
              Ngày trong tháng
              <input
                type="number"
                min="1"
                max="31"
                value={form.dayOfMonth}
                onChange={(e) => updateField("dayOfMonth", e.target.value)}
                required
              />
            </label>
            <label className="field">
              Bắt đầu từ
              <input
                type="date"
                value={form.startDate}
                onChange={(e) => updateField("startDate", e.target.value)}
                required
              />
            </label>
            <label className="field">
              Kết thúc (tuỳ chọn)
              <input type="date" value={form.endDate} onChange={(e) => updateField("endDate", e.target.value)} />
            </label>
            <label className="field">
              Ghi chú
              <input placeholder="Tuỳ chọn" value={form.note} onChange={(e) => updateField("note", e.target.value)} />
            </label>
            <button type="submit">{editingId ? "Cập nhật" : "Thêm"}</button>
            {editingId && (
              <button type="button" className="btn-secondary" onClick={cancelEdit}>
                Huỷ
              </button>
            )}
          </form>
        )}
        {error && <p className="error-text">{error}</p>}
      </div>

      <div className="section-card">
        <h2>Danh sách giao dịch định kỳ</h2>
        {rules.length === 0 ? (
          <p className="empty-state">Chưa có giao dịch định kỳ nào</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Ví</th>
                <th>Danh mục</th>
                <th>Loại</th>
                <th>Số tiền</th>
                <th>Ngày trong tháng</th>
                <th>Lần kế tiếp</th>
                <th>Trạng thái</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rules.map((r) => (
                <tr key={r.id}>
                  <td data-label="Ví">{walletName(r.walletId)}</td>
                  <td data-label="Danh mục">{categoryName(r.categoryId)}</td>
                  <td data-label="Loại">
                    <span className={`badge ${r.type === "EXPENSE" ? "badge-expense" : "badge-income"}`}>
                      {r.type === "EXPENSE" ? "Chi tiêu" : "Thu nhập"}
                    </span>
                  </td>
                  <td data-label="Số tiền" className={r.type === "EXPENSE" ? "amount-expense" : "amount-income"}>
                    {r.type === "EXPENSE" ? "-" : "+"}
                    {formatCurrency(r.amount)}
                  </td>
                  <td data-label="Ngày trong tháng">Ngày {r.dayOfMonth}</td>
                  <td data-label="Lần kế tiếp">{r.active ? r.nextRunDate : "-"}</td>
                  <td data-label="Trạng thái">
                    <span className={`badge ${r.active ? "badge-income" : "badge-expense"}`}>
                      {r.active ? "Đang chạy" : "Tạm dừng"}
                    </span>
                  </td>
                  <td className="row-actions">
                    <button type="button" className="btn-secondary" onClick={() => toggleActive(r)}>
                      {r.active ? "Tạm dừng" : "Kích hoạt"}
                    </button>
                    <button type="button" className="icon-btn" onClick={() => startEdit(r)} aria-label="Sửa">
                      <EditIcon />
                    </button>
                    <button
                      type="button"
                      className="icon-btn icon-btn-danger"
                      onClick={() => handleDelete(r.id)}
                      aria-label="Xoá"
                    >
                      <TrashIcon />
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
