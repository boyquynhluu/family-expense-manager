import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { AlertIcon, EditIcon, TrashIcon } from "../components/AppIcons";
import AmountInput from "../components/AmountInput";
import Pagination from "../components/Pagination";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency, formatYearMonth } from "../utils/format";
import { useAuth } from "../hooks/useAuth";
import { usePagedList } from "../hooks/usePagedList";
import { Button, IconButton } from "../components/ui/Button";
import { Input, Select } from "../components/ui/Input";
import { Field } from "../components/ui/Field";

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function previousYearMonth() {
  const date = new Date();
  date.setDate(1);
  date.setMonth(date.getMonth() - 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

// Same rule as the budget e-mails (expense-service TransactionService#publishBudgetCrossing): only
// spending strictly MORE than the limit is "over budget" — exactly the limit has reached it, not exceeded it.
// Compared in whole cents (amounts are DECIMAL(18, 2)): the overall budget's spend is a client-side sum of
// per-category totals, and float addition can land a hair above the limit (0.1 + 0.2 = 0.30000000000000004).
function toCents(amount) {
  return Math.round(Number(amount) * 100);
}

function statusOf(spentAmount, limitAmount) {
  const spent = toCents(spentAmount);
  const limit = toCents(limitAmount);
  if (limit <= 0) return "safe";
  if (spent > limit) return "danger";
  if (spent === limit) return "reached";
  if (spent * 10 >= limit * 8) return "warning";
  return "safe";
}

const STATUS_LABEL_KEYS = {
  danger: "budgets:statusDanger",
  reached: "budgets:statusReached",
  warning: "budgets:statusWarning",
  safe: "budgets:statusSafe",
};

// "Reached" shares the amber warning look; only an actual overspend turns red.
const STATUS_STYLE = { danger: "danger", reached: "warning", warning: "warning", safe: "safe" };

export default function Budgets() {
  const { t } = useTranslation(["common", "budgets"]);
  const { role } = useAuth();
  const isOwner = role === "OWNER";
  const { pageData, setPage, reload } = usePagedList("/expenses/budgets");
  const budgets = pageData.content;
  const [categories, setCategories] = useState([]);
  const [categoryId, setCategoryId] = useState("");
  const [periodMonth, setPeriodMonth] = useState(currentYearMonth());
  const [limitAmount, setLimitAmount] = useState("");
  const [editingId, setEditingId] = useState(null);
  const [spentByMonth, setSpentByMonth] = useState({});
  const [error, setError] = useState("");
  const [copyFrom, setCopyFrom] = useState(previousYearMonth());
  const [copyTo, setCopyTo] = useState(currentYearMonth());
  const [copying, setCopying] = useState(false);

  useEffect(() => {
    client.get("/expenses/categories").then((res) => {
      const expenseCategories = res.data.data.filter((c) => c.type === "EXPENSE");
      setCategories(expenseCategories);
      if (expenseCategories.length > 0) {
        setCategoryId((prev) => prev || String(expenseCategories[0].id));
      }
    });
  }, []);

  // Budgets can span different months, so fetch the actual-spend report once per
  // distinct month that appears in the budget list (reuses the Dashboard's endpoint).
  useEffect(() => {
    const months = [...new Set(budgets.map((b) => b.periodMonth))];
    months
      .filter((month) => !(month in spentByMonth))
      .forEach((month) => {
        client.get("/expenses/reports/category", { params: { yearMonth: month } }).then((res) => {
          const byCategory = {};
          let total = 0;
          res.data.data.forEach((row) => {
            byCategory[row.categoryId] = row.total;
            total += Number(row.total ?? 0);
          });
          setSpentByMonth((prev) => ({ ...prev, [month]: { byCategory, total } }));
        });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [budgets]);

  function startEdit(budget) {
    setEditingId(budget.id);
    setCategoryId(budget.categoryId == null ? "" : String(budget.categoryId));
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
    const confirmKey = editingId ? "budgets:updateConfirm" : "budgets:addConfirm";
    const confirmVars = { amount: formatCurrency(Number(limitAmount)), month: formatYearMonth(periodMonth) };
    if (!(await confirmDialog(t(confirmKey, confirmVars), { tone: "primary", icon: "question" }))) return;
    setError("");
    const payload = {
      categoryId: categoryId === "" ? null : Number(categoryId),
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
      reload();
    } catch (err) {
      setError(err.response?.data?.message || t("budgets:saveFailed"));
    }
  }

  async function handleDelete(id) {
    if (!(await confirmDialog(t("budgets:deleteConfirm")))) return;
    setError("");
    try {
      await client.delete(`/expenses/budgets/${id}`);
      reload();
    } catch (err) {
      setError(err.response?.data?.message || t("budgets:deleteFailed"));
    }
  }

  async function handleCopy(e) {
    e.preventDefault();
    if (copyFrom === copyTo) {
      toast.error(t("budgets:copySameMonth"));
      return;
    }
    if (!(await confirmDialog(t("budgets:copyConfirm", { from: formatYearMonth(copyFrom), to: formatYearMonth(copyTo) }), { tone: "primary", icon: "question" }))) return;
    setCopying(true);
    try {
      const res = await client.post("/expenses/budgets/copy", { fromMonth: copyFrom, toMonth: copyTo });
      const { copied, skipped } = res.data.data;
      toast.success(t("budgets:copyResult", { copied, skipped }));
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("budgets:copyFailed"));
    } finally {
      setCopying(false);
    }
  }

  function budgetLabel(budget) {
    if (budget.categoryId == null) return t("budgets:overallLabel");
    return categories.find((c) => c.id === budget.categoryId)?.name ?? `#${budget.categoryId}`;
  }

  function spentOf(budget) {
    const month = spentByMonth[budget.periodMonth];
    if (!month) return 0;
    return budget.categoryId == null ? month.total : (month.byCategory[budget.categoryId] ?? 0);
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("budgets:title")}</h1>
          <p className="page-header-subtitle">{t("budgets:subtitle")}</p>
        </div>
      </div>

      {isOwner ? (
        <>
          <div className="section-card">
            <h2>{editingId ? t("budgets:editTitle") : t("budgets:newTitle")}</h2>
            <form className="inline-form" onSubmit={handleSubmit}>
              <Field>
                {t("budgets:categoryLabel")}
                <Select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                  <option value="">{t("budgets:overallOption")}</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field>
                <span>
                  {t("budgets:monthLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <Input type="month" value={periodMonth} onChange={(e) => setPeriodMonth(e.target.value)} required />
              </Field>
              <Field>
                <span>
                  {t("budgets:limitLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <AmountInput placeholder="0" value={limitAmount} onChange={setLimitAmount} required positive />
              </Field>
              <Button type="submit">{editingId ? t("budgets:updateButton") : t("budgets:createButton")}</Button>
              {editingId && (
                <Button variant="secondary" onClick={cancelEdit}>
                  {t("common:cancel")}
                </Button>
              )}
            </form>
            {error && <p className="error-text">{error}</p>}
          </div>

          <div className="section-card">
            <h2>{t("budgets:copyTitle")}</h2>
            <p className="page-header-subtitle">{t("budgets:copyHint")}</p>
            <form className="inline-form" onSubmit={handleCopy}>
              <Field>
                <span>
                  {t("budgets:copyFromLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <Input type="month" value={copyFrom} onChange={(e) => setCopyFrom(e.target.value)} required />
              </Field>
              <Field>
                <span>
                  {t("budgets:copyToLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <Input
                  type="month"
                  value={copyTo}
                  onChange={(e) => setCopyTo(e.target.value)}
                  required
                  validate={(v) => (v && v === copyFrom ? t("validation:monthsMustDiffer") : "")}
                />
              </Field>
              <Button type="submit" disabled={copying}>
                {t("budgets:copyButton")}
              </Button>
            </form>
          </div>
        </>
      ) : (
        <div className="section-card">
          <p className="page-header-subtitle">{t("budgets:ownerOnlyManage")}</p>
        </div>
      )}

      <div className="section-card">
        <h2>{t("budgets:listTitle")}</h2>
        {budgets.length === 0 ? (
          <p className="empty-state">{t("budgets:noBudgets")}</p>
        ) : (
          <div className="category-breakdown">
            {budgets.map((b) => {
              const spent = spentOf(b);
              const status = statusOf(spent, b.limitAmount);
              const percent = b.limitAmount > 0 ? (spent / b.limitAmount) * 100 : 0;
              return (
                <div className="category-row" key={b.id}>
                  <div className="category-row-header">
                    <span className="category-row-name">
                      {budgetLabel(b)}
                      <span className="budget-month">{b.periodMonth}</span>
                    </span>
                    <span className="category-row-amount">
                      <span className={`badge budget-badge-${STATUS_STYLE[status]}`}>
                        {status === "danger" && <AlertIcon />}
                        {t(STATUS_LABEL_KEYS[status])}
                      </span>
                      {formatCurrency(spent)} / {formatCurrency(b.limitAmount)}
                      {isOwner && (
                        <span className="row-actions">
                          <IconButton
                            onClick={() => startEdit(b)}
                            aria-label={t("common:edit")}
                          >
                            <EditIcon />
                          </IconButton>
                          <IconButton variant="danger"
                            onClick={() => handleDelete(b.id)}
                            aria-label={t("common:delete")}
                          >
                            <TrashIcon />
                          </IconButton>
                        </span>
                      )}
                    </span>
                  </div>
                  <div className="category-bar-track">
                    <div
                      className={`category-bar-fill budget-bar-${STATUS_STYLE[status]}`}
                      style={{ width: `${Math.min(percent, 100)}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}
        <Pagination pageData={pageData} onPageChange={setPage} />
      </div>
    </div>
  );
}
