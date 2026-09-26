import { useEffect, useState } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { BalanceIcon, CalendarIcon, TrendDownIcon, TrendUpIcon, WalletIcon } from "../components/AppIcons";
import { formatCurrency } from "../utils/format";

const CATEGORY_COLORS = ["#4f46e5", "#0ea5e9", "#f59e0b", "#16a34a", "#db2777", "#7c3aed", "#dc2626", "#0891b2"];

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export default function Dashboard() {
  const { t } = useTranslation("dashboard");
  const [yearMonth, setYearMonth] = useState(currentYearMonth());
  const [summary, setSummary] = useState(null);
  const [report, setReport] = useState([]);
  const [wallets, setWallets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [walletBreakdownRows, setWalletBreakdownRows] = useState([]);
  const [trend, setTrend] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setError("");
    Promise.all([
      client.get("/expenses/summary", { params: { yearMonth } }),
      client.get("/expenses/reports/category", { params: { yearMonth } }),
      client.get("/expenses/reports/wallet-category", { params: { yearMonth } }),
    ])
      .then(([summaryRes, reportRes, walletCategoryRes]) => {
        if (cancelled) return;
        setSummary(summaryRes.data.data);
        setReport(reportRes.data.data);
        setWalletBreakdownRows(walletCategoryRes.data.data);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err.response?.data?.message || t("summaryLoadFailed"));
      });
    return () => {
      cancelled = true;
    };
  }, [yearMonth, t]);

  // Wallets/categories aren't month-scoped on the backend, so they're loaded once.
  useEffect(() => {
    client.get("/expenses/wallets").then((res) => setWallets(res.data.data));
    client.get("/expenses/categories").then((res) => setCategories(res.data.data));
    client.get("/expenses/reports/trend", { params: { months: 6 } }).then((res) => setTrend(res.data.data));
  }, []);

  const trendChartData = trend.map((row) => ({
    yearMonth: row.yearMonth,
    [t("income")]: Number(row.totalIncome),
    [t("expense")]: Number(row.totalExpense),
  }));

  const sortedReport = [...report].sort((a, b) => b.total - a.total);
  const maxCategoryTotal = sortedReport.length > 0 ? Math.max(...sortedReport.map((r) => r.total)) : 0;

  function categoryName(id) {
    return categories.find((c) => c.id === id)?.name ?? `#${id}`;
  }

  // Assign each category a stable color (by first appearance) so the same category
  // shows the same color across every wallet's breakdown below.
  const categoryColors = new Map();
  for (const row of sortedReport) {
    if (!categoryColors.has(row.categoryId)) {
      categoryColors.set(row.categoryId, CATEGORY_COLORS[categoryColors.size % CATEGORY_COLORS.length]);
    }
  }

  // Aggregation now happens on the backend (GET /expenses/reports/wallet-category) —
  // see README "1. Phân trang/lọc chỉ làm ở frontend" — so this just regroups the
  // already-summed rows by wallet instead of reducing over every raw transaction.
  const walletBreakdowns = wallets.map((wallet) => {
    const rows = walletBreakdownRows
      .filter((row) => row.walletId === wallet.id)
      .map((row) => ({ categoryId: row.categoryId, total: Number(row.total) }))
      .sort((a, b) => b.total - a.total);
    const maxTotal = rows.length > 0 ? Math.max(...rows.map((r) => r.total)) : 0;
    return { wallet, rows, maxTotal };
  });

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("title")}</h1>
          <p className="page-header-subtitle">{t("subtitle")}</p>
        </div>
        <label className="month-picker">
          <CalendarIcon />
          <input type="month" value={yearMonth} onChange={(e) => setYearMonth(e.target.value)} />
        </label>
      </div>

      {error && <p className="error-text">{error}</p>}

      {summary && (
        <div className="summary-cards">
          <div className="card card-income">
            <span className="card-icon">
              <TrendUpIcon />
            </span>
            <div className="card-body">
              <span>{t("totalIncome")}</span>
              <strong>{formatCurrency(summary.totalIncome)}</strong>
            </div>
          </div>
          <div className="card card-expense">
            <span className="card-icon">
              <TrendDownIcon />
            </span>
            <div className="card-body">
              <span>{t("totalExpense")}</span>
              <strong>{formatCurrency(summary.totalExpense)}</strong>
            </div>
          </div>
          <div className="card card-balance">
            <span className="card-icon">
              <BalanceIcon />
            </span>
            <div className="card-body">
              <span>{t("balanceThisMonth")}</span>
              <strong>{formatCurrency(summary.balance)}</strong>
            </div>
          </div>
        </div>
      )}

      <div className="section-card">
        <h2>{t("trendTitle")}</h2>
        {trendChartData.length === 0 ? (
          <p className="empty-state">{t("noData")}</p>
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={trendChartData} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="yearMonth" />
              <YAxis tickFormatter={(v) => formatCurrency(v)} width={90} />
              <Tooltip formatter={(value) => formatCurrency(value)} />
              <Legend />
              <Line type="monotone" dataKey={t("income")} stroke="#16a34a" strokeWidth={2} />
              <Line type="monotone" dataKey={t("expense")} stroke="#dc2626" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="section-card">
        <h2>{t("walletBalanceTitle")}</h2>
        {wallets.length === 0 ? (
          <p className="empty-state">{t("noWallets")}</p>
        ) : (
          <div className="category-breakdown">
            {wallets.map((w) => (
              <div className="category-row" key={w.id}>
                <div className="category-row-header">
                  <span className="category-row-name">
                    <WalletIcon /> {w.name}
                  </span>
                  <span className="category-row-amount">
                    <strong>{formatCurrency(w.currentBalance, w.currency)}</strong>
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="section-card">
        <h2>{t("expenseByCategoryTitle")}</h2>
        {sortedReport.length === 0 ? (
          <p className="empty-state">{t("noExpenseData")}</p>
        ) : (
          <div className="category-breakdown">
            {sortedReport.map((row, index) => {
              const color = CATEGORY_COLORS[index % CATEGORY_COLORS.length];
              const widthPercent = maxCategoryTotal > 0 ? (row.total / maxCategoryTotal) * 100 : 0;
              return (
                <div className="category-row" key={row.categoryId}>
                  <div className="category-row-header">
                    <span className="category-row-name">
                      <span className="color-dot" style={{ backgroundColor: color }} />
                      {row.categoryName}
                    </span>
                    <span className="category-row-amount">{formatCurrency(row.total)}</span>
                  </div>
                  <div className="category-bar-track">
                    <div
                      className="category-bar-fill"
                      style={{ width: `${widthPercent}%`, backgroundColor: color }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="section-card">
        <h2>{t("expenseByCategoryByWalletTitle")}</h2>
        {walletBreakdowns.length === 0 ? (
          <p className="empty-state">{t("noWallets")}</p>
        ) : (
          <div className="wallet-breakdown-grid">
            {walletBreakdowns.map(({ wallet, rows, maxTotal }) => (
              <div className="wallet-breakdown-card" key={wallet.id}>
                <div className="wallet-breakdown-title">
                  <WalletIcon /> {wallet.name}
                </div>
                {rows.length === 0 ? (
                  <p className="empty-state">{t("noExpenseThisMonth")}</p>
                ) : (
                  <div className="category-breakdown">
                    {rows.map((row) => {
                      const color = categoryColors.get(row.categoryId) ?? CATEGORY_COLORS[0];
                      const widthPercent = maxTotal > 0 ? (row.total / maxTotal) * 100 : 0;
                      return (
                        <div className="category-row" key={row.categoryId}>
                          <div className="category-row-header">
                            <span className="category-row-name">
                              <span className="color-dot" style={{ backgroundColor: color }} />
                              {categoryName(row.categoryId)}
                            </span>
                            <span className="category-row-amount">{formatCurrency(row.total)}</span>
                          </div>
                          <div className="category-bar-track">
                            <div
                              className="category-bar-fill"
                              style={{ width: `${widthPercent}%`, backgroundColor: color }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
