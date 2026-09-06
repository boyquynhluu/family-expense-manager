import { useEffect, useState } from "react";
import client from "../api/client";

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export default function Budgets() {
  const [budgets, setBudgets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [categoryId, setCategoryId] = useState("");
  const [periodMonth, setPeriodMonth] = useState(currentYearMonth());
  const [limitAmount, setLimitAmount] = useState("");
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

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      await client.post("/expenses/budgets", {
        categoryId: Number(categoryId),
        periodMonth,
        limitAmount: Number(limitAmount),
      });
      setLimitAmount("");
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Tạo ngân sách thất bại");
    }
  }

  function categoryName(id) {
    return categories.find((c) => c.id === id)?.name ?? `#${id}`;
  }

  return (
    <div>
      <h1>Ngân sách</h1>
      {categories.length === 0 ? (
        <p>Cần tạo ít nhất 1 danh mục chi tiêu trước khi đặt ngân sách.</p>
      ) : (
        <form className="inline-form" onSubmit={handleSubmit}>
          <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          <input type="month" value={periodMonth} onChange={(e) => setPeriodMonth(e.target.value)} required />
          <input
            type="number"
            step="0.01"
            placeholder="Hạn mức"
            value={limitAmount}
            onChange={(e) => setLimitAmount(e.target.value)}
            required
          />
          <button type="submit">Đặt ngân sách</button>
        </form>
      )}
      {error && <p className="error-text">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Danh mục</th>
            <th>Tháng</th>
            <th>Hạn mức</th>
          </tr>
        </thead>
        <tbody>
          {budgets.map((b) => (
            <tr key={b.id}>
              <td>{categoryName(b.categoryId)}</td>
              <td>{b.periodMonth}</td>
              <td>{b.limitAmount}</td>
            </tr>
          ))}
          {budgets.length === 0 && (
            <tr>
              <td colSpan={3}>Chưa có ngân sách nào</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
