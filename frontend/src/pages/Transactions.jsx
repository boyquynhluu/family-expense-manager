import { useEffect, useState } from "react";
import client from "../api/client";

const emptyForm = {
  walletId: "",
  categoryId: "",
  type: "EXPENSE",
  amount: "",
  occurredAt: "",
  note: "",
};

export default function Transactions() {
  const [transactions, setTransactions] = useState([]);
  const [wallets, setWallets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/transactions").then((res) => setTransactions(res.data.data));
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

  function startEdit(transaction) {
    setEditingId(transaction.id);
    setForm({
      walletId: String(transaction.walletId),
      categoryId: String(transaction.categoryId),
      type: transaction.type,
      amount: String(transaction.amount),
      occurredAt: transaction.occurredAt.slice(0, 16),
      note: transaction.note ?? "",
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
      occurredAt: form.occurredAt,
      note: form.note || null,
    };
    try {
      if (editingId) {
        await client.put(`/expenses/transactions/${editingId}`, payload);
      } else {
        await client.post("/expenses/transactions", payload);
      }
      cancelEdit();
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Lưu giao dịch thất bại");
    }
  }

  async function handleDelete(id) {
    setError("");
    try {
      await client.delete(`/expenses/transactions/${id}`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Xoá giao dịch thất bại");
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
      <h1>Giao dịch</h1>
      <form className="inline-form" onSubmit={handleSubmit}>
        <select value={form.walletId} onChange={(e) => updateField("walletId", e.target.value)} required>
          {wallets.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
        <select value={form.categoryId} onChange={(e) => updateField("categoryId", e.target.value)} required>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <select value={form.type} onChange={(e) => updateField("type", e.target.value)}>
          <option value="EXPENSE">Chi tiêu</option>
          <option value="INCOME">Thu nhập</option>
        </select>
        <input
          type="number"
          step="0.01"
          placeholder="Số tiền"
          value={form.amount}
          onChange={(e) => updateField("amount", e.target.value)}
          required
        />
        <input
          type="datetime-local"
          value={form.occurredAt}
          onChange={(e) => updateField("occurredAt", e.target.value)}
          required
        />
        <input placeholder="Ghi chú" value={form.note} onChange={(e) => updateField("note", e.target.value)} />
        <button type="submit">{editingId ? "Cập nhật" : "Thêm giao dịch"}</button>
        {editingId && (
          <button type="button" onClick={cancelEdit}>
            Huỷ
          </button>
        )}
      </form>
      {error && <p className="error-text">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Thời gian</th>
            <th>Ví</th>
            <th>Danh mục</th>
            <th>Loại</th>
            <th>Số tiền</th>
            <th>Ghi chú</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((t) => (
            <tr key={t.id}>
              <td>{t.occurredAt.replace("T", " ")}</td>
              <td>{walletName(t.walletId)}</td>
              <td>{categoryName(t.categoryId)}</td>
              <td>{t.type === "EXPENSE" ? "Chi tiêu" : "Thu nhập"}</td>
              <td>{t.amount}</td>
              <td>{t.note}</td>
              <td className="row-actions">
                <button type="button" onClick={() => startEdit(t)}>
                  Sửa
                </button>
                <button type="button" onClick={() => handleDelete(t.id)}>
                  Xoá
                </button>
              </td>
            </tr>
          ))}
          {transactions.length === 0 && (
            <tr>
              <td colSpan={7}>Chưa có giao dịch nào</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
