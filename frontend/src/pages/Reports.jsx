import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { BalanceIcon, TrendDownIcon, TrendUpIcon } from "../components/AppIcons";
import { formatCurrency } from "../utils/format";

const INCOME_COLOR = "#16a34a";
const EXPENSE_COLOR = "#dc2626";
const CATEGORY_COLORS = ["#4f46e5", "#0ea5e9", "#f59e0b", "#16a34a", "#db2777", "#7c3aed", "#dc2626", "#0891b2"];
const TABS = ["range", "year", "member", "compare"];

function pad(n) {
  return String(n).padStart(2, "0");
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function formatMonth(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
}

function quickRange(kind) {
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth();
  switch (kind) {
    case "lastMonth":
      return [formatDate(new Date(y, m - 1, 1)), formatDate(new Date(y, m, 0))];
    case "last3Months":
      return [formatDate(new Date(y, m - 2, 1)), formatDate(new Date(y, m + 1, 0))];
    case "thisYear":
      return [formatDate(new Date(y, 0, 1)), formatDate(new Date(y, 11, 31))];
    default:
      return [formatDate(new Date(y, m, 1)), formatDate(new Date(y, m + 1, 0))];
  }
}

function bucketLabel(bucket) {
  const parts = bucket.split("-");
  return parts.length === 3 ? `${parts[2]}/${parts[1]}` : `${parts[1]}/${parts[0]}`;
}

function signedCurrency(value) {
  return `${value > 0 ? "+" : ""}${formatCurrency(value)}`;
}

// Fetches `url` (skipped when null); loading is derived by comparing the stored result's url with the current one.
function useReport(url) {
  const [result, setResult] = useState({ url: null, data: null, failed: false, message: "" });

  useEffect(() => {
    if (!url) return undefined;
    let cancelled = false;
    client
      .get(url)
      .then((res) => {
        if (!cancelled) setResult({ url, data: res.data.data, failed: false, message: "" });
      })
      .catch((err) => {
        if (!cancelled) {
          setResult({ url, data: null, failed: true, message: err.response?.data?.message || "" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [url]);

  const current = url !== null && result.url === url;
  return {
    data: current ? result.data : null,
    failed: current && result.failed,
    message: current ? result.message : "",
    loading: url !== null && !current,
  };
}

function ErrorText({ report }) {
  const { t } = useTranslation("reports");
  return report.failed ? <p className="error-text">{report.message || t("loadFailed")}</p> : null;
}

function SummaryCards({ income, expense, net }) {
  const { t } = useTranslation("reports");
  return (
    <div className="summary-cards">
      <div className="card card-income">
        <span className="card-icon">
          <TrendUpIcon />
        </span>
        <div className="card-body">
          <span>{t("totalIncome")}</span>
          <strong>{formatCurrency(income)}</strong>
        </div>
      </div>
      <div className="card card-expense">
        <span className="card-icon">
          <TrendDownIcon />
        </span>
        <div className="card-body">
          <span>{t("totalExpense")}</span>
          <strong>{formatCurrency(expense)}</strong>
        </div>
      </div>
      <div className="card card-balance">
        <span className="card-icon">
          <BalanceIcon />
        </span>
        <div className="card-body">
          <span>{t("net")}</span>
          <strong>{formatCurrency(net)}</strong>
        </div>
      </div>
    </div>
  );
}

function IncomeExpenseChart({ data }) {
  const { t } = useTranslation("reports");
  const chartData = data.map((row) => ({
    label: row.label,
    [t("income")]: Number(row.income),
    [t("expense")]: Number(row.expense),
  }));
  return (
    <ResponsiveContainer width="100%" height={280}>
      <BarChart data={chartData} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="label" interval="preserveStartEnd" />
        <YAxis tickFormatter={(v) => formatCurrency(v)} width={90} />
        <Tooltip formatter={(value) => formatCurrency(value)} />
        <Legend />
        <Bar dataKey={t("income")} fill={INCOME_COLOR} isAnimationActive={false} />
        <Bar dataKey={t("expense")} fill={EXPENSE_COLOR} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  );
}

function CategoryBars({ rows, categoryName, emptyText }) {
  if (rows.length === 0) return <p className="empty-state">{emptyText}</p>;
  const max = Math.max(...rows.map((r) => Number(r.total)));
  return (
    <div className="category-breakdown">
      {rows.map((row, index) => {
        const color = CATEGORY_COLORS[index % CATEGORY_COLORS.length];
        const widthPercent = max > 0 ? (Number(row.total) / max) * 100 : 0;
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
              <div className="category-bar-fill" style={{ width: `${widthPercent}%`, backgroundColor: color }} />
            </div>
          </div>
        );
      })}
    </div>
  );
}

function RangeFilter({ from, to, onChange }) {
  const { t } = useTranslation("reports");
  const quickButtons = [
    ["thisMonth", t("quick.thisMonth")],
    ["lastMonth", t("quick.lastMonth")],
    ["last3Months", t("quick.last3Months")],
    ["thisYear", t("quick.thisYear")],
  ];
  return (
    <div className="section-card report-controls">
      <div className="inline-form">
        <label className="field">
          <span>
            {t("fromLabel")}
            <span className="required-mark" aria-hidden="true"> *</span>
          </span>
          <input type="date" value={from} onChange={(e) => onChange(e.target.value, to)} required />
        </label>
        <label className="field">
          <span>
            {t("toLabel")}
            <span className="required-mark" aria-hidden="true"> *</span>
          </span>
          <input type="date" value={to} onChange={(e) => onChange(from, e.target.value)} required />
        </label>
        {quickButtons.map(([kind, label]) => (
          <button key={kind} type="button" className="btn-secondary" onClick={() => onChange(...quickRange(kind))}>
            {label}
          </button>
        ))}
      </div>
      {from && to && from > to && <p className="error-text">{t("invalidRange")}</p>}
    </div>
  );
}

function rangeUrl(path, from, to) {
  return from && to && from <= to ? `${path}?from=${from}&to=${to}` : null;
}

function RangeTab({ from, to, onChange, categoryName }) {
  const { t } = useTranslation("reports");
  const report = useReport(rangeUrl("/expenses/reports/range", from, to));
  const data = report.data;
  const income = data ? data.byCategory.filter((c) => c.type === "INCOME") : [];
  const expense = data ? data.byCategory.filter((c) => c.type === "EXPENSE") : [];
  const chartRows = data
    ? data.buckets.map((b) => ({ label: bucketLabel(b.bucket), income: b.income, expense: b.expense }))
    : [];

  return (
    <>
      <RangeFilter from={from} to={to} onChange={onChange} />
      <ErrorText report={report} />
      {report.loading && <p className="empty-state">{t("loading")}</p>}
      {data && (
        <>
          <p className="report-period">{t("periodLabel", { from: data.from, to: data.to })}</p>
          <SummaryCards income={data.totalIncome} expense={data.totalExpense} net={data.net} />
          <div className="section-card">
            <h2>{data.bucketType === "DAY" ? t("chartByDay") : t("chartByMonth")}</h2>
            <IncomeExpenseChart data={chartRows} />
          </div>
          <div className="section-card">
            <h2>{t("expenseByCategory")}</h2>
            <CategoryBars rows={expense} categoryName={categoryName} emptyText={t("noExpense")} />
          </div>
          <div className="section-card">
            <h2>{t("incomeByCategory")}</h2>
            <CategoryBars rows={income} categoryName={categoryName} emptyText={t("noIncome")} />
          </div>
        </>
      )}
    </>
  );
}

function YearTab() {
  const { t } = useTranslation("reports");
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(currentYear);
  const report = useReport(`/expenses/reports/year?year=${year}`);
  const data = report.data;
  const years = Array.from({ length: 8 }, (_, i) => currentYear + 1 - i);
  const chartRows = data
    ? data.months.map((m) => ({
        label: t("monthShort", { month: Number(m.yearMonth.slice(5)) }),
        income: m.income,
        expense: m.expense,
      }))
    : [];

  return (
    <>
      <div className="section-card report-controls">
        <div className="inline-form">
          <label className="field">
            <span>
              {t("yearLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <select value={year} onChange={(e) => setYear(Number(e.target.value))} required>
              {years.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>
      <ErrorText report={report} />
      {report.loading && <p className="empty-state">{t("loading")}</p>}
      {data && (
        <>
          <p className="report-period">{t("yearPeriodLabel", { year: data.year })}</p>
          <SummaryCards income={data.totalIncome} expense={data.totalExpense} net={data.net} />
          <div className="section-card">
            <h2>{t("chartByMonth")}</h2>
            <IncomeExpenseChart data={chartRows} />
          </div>
          <div className="section-card">
            <table>
              <thead>
                <tr>
                  <th>{t("month")}</th>
                  <th>{t("income")}</th>
                  <th>{t("expense")}</th>
                  <th>{t("net")}</th>
                </tr>
              </thead>
              <tbody>
                {data.months.map((m) => {
                  const net = Number(m.income) - Number(m.expense);
                  return (
                    <tr key={m.yearMonth}>
                      <td>{m.yearMonth}</td>
                      <td className="amount-income">{formatCurrency(m.income)}</td>
                      <td className="amount-expense">{formatCurrency(m.expense)}</td>
                      <td className={net < 0 ? "amount-expense" : "amount-income"}>{formatCurrency(net)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  );
}

function MemberTab({ from, to, onChange }) {
  const { t } = useTranslation("reports");
  const report = useReport(rangeUrl("/expenses/reports/by-member", from, to));
  const rows = report.data;

  return (
    <>
      <RangeFilter from={from} to={to} onChange={onChange} />
      <ErrorText report={report} />
      {report.loading && <p className="empty-state">{t("loading")}</p>}
      {rows && (
        <div className="section-card">
          <p className="report-period">{t("periodLabel", { from, to })}</p>
          {rows.length === 0 ? (
            <p className="empty-state">{t("noData")}</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>{t("member")}</th>
                  <th>{t("income")}</th>
                  <th>{t("expense")}</th>
                  <th>{t("net")}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const net = Number(row.income) - Number(row.expense);
                  return (
                    <tr key={row.userId}>
                      <td>{row.displayName || t("formerMember")}</td>
                      <td className="amount-income">{formatCurrency(row.income)}</td>
                      <td className="amount-expense">{formatCurrency(row.expense)}</td>
                      <td className={net < 0 ? "amount-expense" : "amount-income"}>{formatCurrency(net)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      )}
    </>
  );
}

// For expenses a rise is bad (red) and a fall is good (green); income is the reverse.
function deltaClass(delta, isExpense) {
  if (delta === 0) return "";
  const good = isExpense ? delta < 0 : delta > 0;
  return good ? "amount-income" : "amount-expense";
}

function CompareTab({ categoryName }) {
  const { t } = useTranslation("reports");
  const [month, setMonth] = useState(() => formatMonth(new Date()));
  const [withMonth, setWithMonth] = useState(() => {
    const now = new Date();
    return formatMonth(new Date(now.getFullYear(), now.getMonth() - 1, 1));
  });
  const report = useReport(
    month && withMonth ? `/expenses/reports/compare?month=${month}&withMonth=${withMonth}` : null,
  );
  const data = report.data;

  return (
    <>
      <div className="section-card report-controls">
        <div className="inline-form">
          <label className="field">
            <span>
              {t("monthLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} required />
          </label>
          <label className="field">
            <span>
              {t("withMonthLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <input type="month" value={withMonth} onChange={(e) => setWithMonth(e.target.value)} required />
          </label>
        </div>
      </div>
      <ErrorText report={report} />
      {report.loading && <p className="empty-state">{t("loading")}</p>}
      {data && (
        <>
          <div className="summary-cards">
            <div className="card card-income">
              <span className="card-icon">
                <TrendUpIcon />
              </span>
              <div className="card-body">
                <span>{t("totalIncome")}</span>
                <strong>{formatCurrency(data.current.income)}</strong>
                <span className={deltaClass(Number(data.incomeDelta), false)}>
                  {t("vsMonth", { month: data.previous.yearMonth })}: {signedCurrency(Number(data.incomeDelta))}
                </span>
              </div>
            </div>
            <div className="card card-expense">
              <span className="card-icon">
                <TrendDownIcon />
              </span>
              <div className="card-body">
                <span>{t("totalExpense")}</span>
                <strong>{formatCurrency(data.current.expense)}</strong>
                <span className={deltaClass(Number(data.expenseDelta), true)}>
                  {t("vsMonth", { month: data.previous.yearMonth })}: {signedCurrency(Number(data.expenseDelta))}
                </span>
              </div>
            </div>
          </div>
          <div className="section-card">
            <h2>{t("expenseByCategory")}</h2>
            {data.categories.length === 0 ? (
              <p className="empty-state">{t("noExpense")}</p>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>{t("category")}</th>
                    <th>{data.current.yearMonth}</th>
                    <th>{data.previous.yearMonth}</th>
                    <th>{t("delta")}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.categories.map((c) => {
                    const delta = Number(c.delta);
                    return (
                      <tr key={c.categoryId}>
                        <td>{categoryName(c.categoryId)}</td>
                        <td>{formatCurrency(c.current)}</td>
                        <td>{formatCurrency(c.previous)}</td>
                        <td className={deltaClass(delta, true)}>{signedCurrency(delta)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </>
  );
}

export default function Reports() {
  const { t } = useTranslation("reports");
  const [tab, setTab] = useState("range");
  const [[from, to], setRange] = useState(() => quickRange("thisMonth"));
  const [categories, setCategories] = useState([]);

  useEffect(() => {
    client.get("/expenses/categories").then((res) => setCategories(res.data.data));
  }, []);

  function categoryName(id) {
    return categories.find((c) => c.id === id)?.name ?? `#${id}`;
  }

  return (
    <div className="reports-page">
      <div className="page-header">
        <div>
          <h1>{t("title")}</h1>
          <p className="page-header-subtitle">{t("subtitle")}</p>
        </div>
        <button type="button" className="btn-secondary no-print" onClick={() => window.print()}>
          {t("printButton")}
        </button>
      </div>

      <div className="report-tabs no-print" role="tablist">
        {TABS.map((key) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={tab === key}
            className={`report-tab ${tab === key ? "is-active" : ""}`}
            onClick={() => setTab(key)}
          >
            {t(`tabs.${key}`)}
          </button>
        ))}
      </div>

      {tab === "range" && (
        <RangeTab from={from} to={to} onChange={(f, tt) => setRange([f, tt])} categoryName={categoryName} />
      )}
      {tab === "year" && <YearTab />}
      {tab === "member" && <MemberTab from={from} to={to} onChange={(f, tt) => setRange([f, tt])} />}
      {tab === "compare" && <CompareTab categoryName={categoryName} />}
    </div>
  );
}
