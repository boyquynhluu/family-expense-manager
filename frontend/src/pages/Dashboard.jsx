import { useEffect, useState } from "react";
import client from "../api/client";

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export default function Dashboard() {
  const [yearMonth, setYearMonth] = useState(currentYearMonth());
  const [summary, setSummary] = useState(null);
  const [report, setReport] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setError("");
    Promise.all([
      client.get("/expenses/summary", { params: { yearMonth } }),
      client.get("/expenses/reports/category", { params: { yearMonth } }),
    ])
      .then(([summaryRes, reportRes]) => {
        if (cancelled) return;
        setSummary(summaryRes.data.data);
        setReport(reportRes.data.data);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err.response?.data?.message || "Không tải được dữ liệu tổng hợp");
      });
    return () => {
      cancelled = true;
    };
  }, [yearMonth]);

  return (
    <div>
      <h1>Dashboard</h1>
      <label className="inline-label">
        Tháng
        <input type="month" value={yearMonth} onChange={(e) => setYearMonth(e.target.value)} />
      </label>

      {error && <p className="error-text">{error}</p>}

      {summary && (
        <div className="summary-cards">
          <div className="card">
            <span>Tổng thu</span>
            <strong>{summary.totalIncome}</strong>
          </div>
          <div className="card">
            <span>Tổng chi</span>
            <strong>{summary.totalExpense}</strong>
          </div>
          <div className="card">
            <span>Số dư</span>
            <strong>{summary.balance}</strong>
          </div>
        </div>
      )}

      <h2>Chi theo danh mục</h2>
      <table>
        <thead>
          <tr>
            <th>Danh mục</th>
            <th>Tổng chi</th>
          </tr>
        </thead>
        <tbody>
          {report.map((row) => (
            <tr key={row.categoryId}>
              <td>{row.categoryName}</td>
              <td>{row.total}</td>
            </tr>
          ))}
          {report.length === 0 && (
            <tr>
              <td colSpan={2}>Chưa có dữ liệu</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
