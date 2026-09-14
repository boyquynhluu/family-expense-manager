import { useEffect, useState } from "react";
import client from "../api/client";
import { EditIcon, TrashIcon } from "../components/AppIcons";
import { formatCurrency } from "../utils/format";

const emptyForm = {
  walletId: "",
  categoryId: "",
  type: "EXPENSE",
  amount: "",
  occurredAt: "",
  note: "",
};

const emptyFilter = {
  walletId: "",
  categoryId: "",
  type: "",
  fromDate: "",
  toDate: "",
};

const PAGE_SIZE = 20;

export default function Transactions() {
  const [transactions, setTransactions] = useState([]);
  const [wallets, setWallets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [filter, setFilter] = useState(emptyFilter);
  const [page, setPage] = useState(0);
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
    if (!window.confirm("Xoá giao dịch này? Hành động này không thể hoàn tác.")) return;
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

  function updateFilter(field, value) {
    setFilter((f) => ({ ...f, [field]: value }));
    setPage(0);
  }

  function clearFilter() {
    setFilter(emptyFilter);
    setPage(0);
  }

  const filteredTransactions = transactions.filter((t) => {
    if (filter.walletId && String(t.walletId) !== filter.walletId) return false;
    if (filter.categoryId && String(t.categoryId) !== filter.categoryId) return false;
    if (filter.type && t.type !== filter.type) return false;
    const occurredDate = t.occurredAt.slice(0, 10);
    if (filter.fromDate && occurredDate < filter.fromDate) return false;
    if (filter.toDate && occurredDate > filter.toDate) return false;
    return true;
  });

  const totalPages = Math.max(1, Math.ceil(filteredTransactions.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages - 1);
  const pagedTransactions = filteredTransactions.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);
  const hasActiveFilter = Object.values(filter).some(Boolean);

  async function exportReport(format) {
    setError("");
    const params = { format };
    if (filter.walletId) params.walletId = filter.walletId;
    if (filter.categoryId) params.categoryId = filter.categoryId;
    if (filter.type) params.type = filter.type;
    if (filter.fromDate) params.fromDate = filter.fromDate;
    if (filter.toDate) params.toDate = filter.toDate;

    try {
      const res = await client.get("/expenses/transactions/export", { params, responseType: "blob" });
      const extension = format === "EXCEL" ? "xlsx" : "csv";
      const url = URL.createObjectURL(res.data);
      const link = document.createElement("a");
      link.href = url;
      link.download = `giao-dich-${new Date().toISOString().slice(0, 10)}.${extension}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err.response?.data?.message || "Xuất báo cáo thất bại");
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Giao dịch</h1>
          <p className="page-header-subtitle">Ghi lại các khoản thu/chi hằng ngày</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? "Cập nhật giao dịch" : "Thêm giao dịch mới"}</h2>
        {wallets.length === 0 || categories.length === 0 ? (
          <p className="empty-state">
            {wallets.length === 0 && categories.length === 0
              ? "Cần tạo ít nhất 1 ví và 1 danh mục trước khi ghi giao dịch."
              : wallets.length === 0
                ? "Cần tạo ít nhất 1 ví trước khi ghi giao dịch."
                : "Cần tạo ít nhất 1 danh mục trước khi ghi giao dịch."}
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
              Thời gian
              <input
                type="datetime-local"
                value={form.occurredAt}
                onChange={(e) => updateField("occurredAt", e.target.value)}
                required
              />
            </label>
            <label className="field">
              Ghi chú
              <input placeholder="Tuỳ chọn" value={form.note} onChange={(e) => updateField("note", e.target.value)} />
            </label>
            <button type="submit">{editingId ? "Cập nhật" : "Thêm giao dịch"}</button>
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
        <div className="page-header">
          <h2>Lịch sử giao dịch</h2>
          {filteredTransactions.length > 0 && (
            <div className="row-actions">
              <button type="button" className="btn-secondary" onClick={() => exportReport("CSV")}>
                Xuất CSV
              </button>
              <button type="button" className="btn-secondary" onClick={() => exportReport("EXCEL")}>
                Xuất Excel
              </button>
            </div>
          )}
        </div>

        {transactions.length > 0 && (
          <form className="inline-form filter-bar" onSubmit={(e) => e.preventDefault()}>
            <label className="field">
              Ví
              <select value={filter.walletId} onChange={(e) => updateFilter("walletId", e.target.value)}>
                <option value="">Tất cả</option>
                {wallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Danh mục
              <select value={filter.categoryId} onChange={(e) => updateFilter("categoryId", e.target.value)}>
                <option value="">Tất cả</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Loại
              <select value={filter.type} onChange={(e) => updateFilter("type", e.target.value)}>
                <option value="">Tất cả</option>
                <option value="EXPENSE">Chi tiêu</option>
                <option value="INCOME">Thu nhập</option>
              </select>
            </label>
            <label className="field">
              Từ ngày
              <input type="date" value={filter.fromDate} onChange={(e) => updateFilter("fromDate", e.target.value)} />
            </label>
            <label className="field">
              Đến ngày
              <input type="date" value={filter.toDate} onChange={(e) => updateFilter("toDate", e.target.value)} />
            </label>
            {hasActiveFilter && (
              <button type="button" className="btn-secondary" onClick={clearFilter}>
                Xoá lọc
              </button>
            )}
          </form>
        )}

        {filteredTransactions.length === 0 ? (
          <p className="empty-state">
            {hasActiveFilter ? "Không có giao dịch nào khớp bộ lọc" : "Chưa có giao dịch nào"}
          </p>
        ) : (
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
              {pagedTransactions.map((t) => (
                <tr key={t.id}>
                  <td data-label="Thời gian">{t.occurredAt.replace("T", " ")}</td>
                  <td data-label="Ví">{walletName(t.walletId)}</td>
                  <td data-label="Danh mục">{categoryName(t.categoryId)}</td>
                  <td data-label="Loại">
                    <span className={`badge ${t.type === "EXPENSE" ? "badge-expense" : "badge-income"}`}>
                      {t.type === "EXPENSE" ? "Chi tiêu" : "Thu nhập"}
                    </span>
                  </td>
                  <td
                    data-label="Số tiền"
                    className={t.type === "EXPENSE" ? "amount-expense" : "amount-income"}
                  >
                    {t.type === "EXPENSE" ? "-" : "+"}
                    {formatCurrency(t.amount)}
                  </td>
                  <td data-label="Ghi chú">{t.note}</td>
                  <td className="row-actions">
                    <button type="button" className="icon-btn" onClick={() => startEdit(t)} aria-label="Sửa">
                      <EditIcon />
                    </button>
                    <button
                      type="button"
                      className="icon-btn icon-btn-danger"
                      onClick={() => handleDelete(t.id)}
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

        {filteredTransactions.length > PAGE_SIZE && (
          <div className="pagination">
            <span className="pagination-info">
              {filteredTransactions.length} giao dịch — Trang {currentPage + 1}/{totalPages}
            </span>
            <div className="pagination-controls">
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={currentPage === 0}
              >
                Trước
              </button>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={currentPage >= totalPages - 1}
              >
                Sau
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
