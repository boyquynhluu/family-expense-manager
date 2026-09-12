import { useEffect, useState } from "react";
import client from "../api/client";
import { AlertIcon, EditIcon, TrashIcon } from "../components/AppIcons";
import { formatCurrency } from "../utils/format";
import { useAuth } from "../hooks/useAuth";

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function statusOf(spent, limit) {
  if (limit <= 0) return "safe";
  const percent = (spent / limit) * 100;
  if (percent >= 100) return "danger";
  if (percent >= 80) return "warning";
  return "safe";
}

export default function Budgets() {
  const { role } = useAuth();
  const isOwner = role === "OWNER";
  const [budgets, setBudgets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [categoryId, setCategoryId] = useState("");
  const [periodMonth, setPeriodMonth] = useState(currentYearMonth());
  const [limitAmount, setLimitAmount] = useState("");
  const [editingId, setEditingId] = useState(null);
  const [spentByMonth, setSpentByMonth] = useState({});
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/budgets").then((res) => setBudgets(res.data.data));
    client.get("/expenses/categories").then((res) => {
      const expenseCategories = res.data.data.filter((c) => c.type === "EXPENSE");
      setCategories(expenseCategories);
      if (expenseCategories.length > 0) {
        setCategoryId((prev) => prev || String(expenseCategories[0].id));
      }
    });
  }

  useEffect(load, []);

  // Budgets can span different months, so fetch the actual-spend report once per
  // distinct month that appears in the budget list (reuses the Dashboard's endpoint).
  useEffect(() => {
    const months = [...new Set(budgets.map((b) => b.periodMonth))];
    months
      .filter((month) => !(month in spentByMonth))
      .forEach((month) => {
        client.get("/expenses/reports/category", { params: { yearMonth: month } }).then((res) => {
          const byCategory = {};
          res.data.data.forEach((row) => {
            byCategory[row.categoryId] = row.total;
          });
          setSpentByMonth((prev) => ({ ...prev, [month]: byCategory }));
        });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [budgets]);

  function startEdit(budget) {
    setEditingId(budget.id);
    setCategoryId(String(budget.categoryId));
    setPeriodMonth(budget.periodMonth);
    setLimitAmount(String(budget.limitAmount));
  }

  function cancelEdit() {
    setEditingId(null);
    setPeriodMonth(currentYearMonth());
    setLimitAmount("");
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    const payload = {
      categoryId: Number(categoryId),
      periodMonth,
      limitAmount: Number(limitAmount),
    };
    try {
      if (editingId) {
        await client.put(`/expenses/budgets/${editingId}`, payload);
      } else {
        await client.post("/expenses/budgets", payload);
      }
      cancelEdit();
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Lưu ngân sách thất bại");
    }
  }

  async function handleDelete(id) {
    if (!window.confirm("Xoá ngân sách này?")) return;
    setError("");
    try {
      await client.delete(`/expenses/budgets/${id}`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Xoá ngân sách thất bại");
    }
  }

  function categoryName(id) {
    return categories.find((c) => c.id === id)?.name ?? `#${id}`;
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Ngân sách</h1>
          <p className="page-header-subtitle">Đặt hạn mức chi tiêu theo danh mục cho từng tháng</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? "Cập nhật ngân sách" : "Đặt ngân sách mới"}</h2>
        {categories.length === 0 ? (
          <p className="empty-state">Cần tạo ít nhất 1 danh mục chi tiêu trước khi đặt ngân sách.</p>
        ) : (
          <form className="inline-form" onSubmit={handleSubmit}>
            <label className="field">
              Danh mục
              <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Tháng
              <input type="month" value={periodMonth} onChange={(e) => setPeriodMonth(e.target.value)} required />
            </label>
            <label className="field">
              Hạn mức
              <input
                type="number"
                step="0.01"
                placeholder="0"
                value={limitAmount}
                onChange={(e) => setLimitAmount(e.target.value)}
                required
              />
            </label>
            <button type="submit">{editingId ? "Cập nhật" : "Đặt ngân sách"}</button>
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
        <h2>Danh sách ngân sách</h2>
        {budgets.length === 0 ? (
          <p className="empty-state">Chưa có ngân sách nào</p>
        ) : (
          <div className="category-breakdown">
            {budgets.map((b) => {
              const spent = spentByMonth[b.periodMonth]?.[b.categoryId] ?? 0;
              const status = statusOf(spent, b.limitAmount);
              const percent = b.limitAmount > 0 ? (spent / b.limitAmount) * 100 : 0;
              return (
                <div className="category-row" key={b.id}>
                  <div className="category-row-header">
                    <span className="category-row-name">
                      {categoryName(b.categoryId)}
                      <span className="budget-month">{b.periodMonth}</span>
                    </span>
                    <span className="category-row-amount">
                      <span className={`badge budget-badge-${status}`}>
                        {status === "danger" && <AlertIcon />}
                        {status === "danger" ? "Vượt ngân sách" : status === "warning" ? "Gần đạt hạn mức" : "An toàn"}
                      </span>
                      {formatCurrency(spent)} / {formatCurrency(b.limitAmount)}
                      <span className="row-actions">
                        <button type="button" className="icon-btn" onClick={() => startEdit(b)} aria-label="Sửa">
                          <EditIcon />
                        </button>
                        {isOwner && (
                          <button
                            type="button"
                            className="icon-btn icon-btn-danger"
                            onClick={() => handleDelete(b.id)}
                            aria-label="Xoá"
                          >
                            <TrashIcon />
                          </button>
                        )}
                      </span>
                    </span>
                  </div>
                  <div className="category-bar-track">
                    <div
                      className={`category-bar-fill budget-bar-${status}`}
                      style={{ width: `${Math.min(percent, 100)}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
