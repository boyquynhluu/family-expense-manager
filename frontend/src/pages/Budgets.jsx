import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import AmountInput from "../components/AmountInput";
import { AlertIcon, CalendarIcon, EditIcon, HistoryIcon, TrashIcon } from "../components/AppIcons";
import EntityHistoryModal from "../components/EntityHistoryModal";
import Pagination from "../components/Pagination";
import { Button, IconButton } from "../components/ui/Button";
import { Field } from "../components/ui/Field";
import { Input, Select } from "../components/ui/Input";
import { Table, TBody, Td, Th, THead } from "../components/ui/Table";
import { useAuth } from "../hooks/useAuth";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency, formatYearMonth } from "../utils/format";
import { LIMITS } from "../utils/inputLimits";

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

// Same rule as the budget e-mails (expense-service BudgetMonitor): only spending strictly MORE than the limit is
// "over budget" — exactly the limit has reached it, not exceeded it. Compared in whole cents (DECIMAL(18, 2)).
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

function emptyForm() {
  return {
    categoryId: "",
    walletId: "",
    userId: "",
    periodType: "MONTH",
    periodMonth: currentYearMonth(),
    periodYear: String(new Date().getFullYear()),
    limitAmount: "",
    rollover: false,
  };
}

/**
 * Budgets (README mục 2, 18 and A6): monthly or yearly, for every category or one (a parent covers its
 * sub-categories), optionally limited to one wallet and/or one member, optionally rolling the unspent part over.
 * Progress comes from GET /expenses/budgets/status, computed by the backend exactly as the 80% / 100% e-mails are.
 */
export default function Budgets() {
  const { t } = useTranslation(["common", "budgets"]);
  const { role } = useAuth();
  const isOwner = role === "OWNER";
  const { pageData, setPage, reload } = usePagedList("/expenses/budgets");
  const budgets = pageData.content;
  const [categories, setCategories] = useState([]);
  const [wallets, setWallets] = useState([]);
  const [members, setMembers] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");
  const [copyFrom, setCopyFrom] = useState(previousYearMonth());
  const [copyTo, setCopyTo] = useState(currentYearMonth());
  const [copying, setCopying] = useState(false);
  const [statusMonth, setStatusMonth] = useState(currentYearMonth());
  const [statuses, setStatuses] = useState([]);
  const [historyEntity, setHistoryEntity] = useState(null);

  useEffect(() => {
    client.get("/expenses/categories").then((res) => setCategories(res.data.data.filter((c) => c.type === "EXPENSE")));
    client.get("/expenses/wallets").then((res) => setWallets(res.data.data));
    client
      .get("/auth/family/members", { params: { page: 0, size: 100 } })
      .then((res) => setMembers(res.data.data.content))
      .catch(() => {});
  }, []);

  function loadStatus() {
    client
      .get("/expenses/budgets/status", { params: { yearMonth: statusMonth } })
      .then((res) => setStatuses(res.data.data))
      .catch(() => setStatuses([]));
  }

  useEffect(loadStatus, [statusMonth]);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function startEdit(budget) {
    setEditingId(budget.id);
    const yearly = budget.periodType === "YEAR";
    setForm({
      categoryId: budget.categoryId == null ? "" : String(budget.categoryId),
      walletId: budget.walletId == null ? "" : String(budget.walletId),
      userId: budget.userId == null ? "" : String(budget.userId),
      periodType: budget.periodType ?? "MONTH",
      periodMonth: yearly ? currentYearMonth() : budget.periodMonth,
      periodYear: yearly ? String(budget.periodMonth).trim() : budget.periodMonth.slice(0, 4),
      limitAmount: String(budget.limitAmount),
      rollover: Boolean(budget.rollover),
    });
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(emptyForm());
  }

  function afterChange() {
    reload();
    loadStatus();
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const yearly = form.periodType === "YEAR";
    const period = yearly ? form.periodYear : form.periodMonth;
    const confirmKey = editingId ? "budgets:updateConfirm" : "budgets:addConfirm";
    const confirmVars = { amount: formatCurrency(Number(form.limitAmount)), month: yearly ? period : formatYearMonth(period) };
    if (!(await confirmDialog(t(confirmKey, confirmVars), { tone: "primary", icon: "question" }))) return;
    setError("");
    const payload = {
      categoryId: form.categoryId === "" ? null : Number(form.categoryId),
      walletId: form.walletId === "" ? null : Number(form.walletId),
      userId: form.userId === "" ? null : Number(form.userId),
      periodType: form.periodType,
      periodMonth: period,
      limitAmount: Number(form.limitAmount),
      rollover: form.rollover,
    };
    try {
      if (editingId) {
        await client.put(`/expenses/budgets/${editingId}`, payload);
      } else {
        await client.post("/expenses/budgets", payload);
      }
      cancelEdit();
      afterChange();
    } catch (err) {
      setError(err.response?.data?.message || t("budgets:saveFailed"));
    }
  }

  async function handleDelete(id) {
    if (!(await confirmDialog(t("budgets:deleteConfirm")))) return;
    setError("");
    try {
      await client.delete(`/expenses/budgets/${id}`);
      afterChange();
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
      afterChange();
    } catch (err) {
      toast.error(err.response?.data?.message || t("budgets:copyFailed"));
    } finally {
      setCopying(false);
    }
  }

  function categoryLabel(categoryId) {
    if (categoryId == null) return t("budgets:overallLabel");
    return categories.find((c) => c.id === categoryId)?.name ?? `#${categoryId}`;
  }

  function scopeLabel(b) {
    const parts = [];
    if (b.walletId != null) parts.push(t("budgets:scopeWallet", { name: wallets.find((w) => w.id === b.walletId)?.name ?? `#${b.walletId}` }));
    if (b.userId != null) parts.push(t("budgets:scopeMember", { name: members.find((m) => m.id === b.userId)?.displayName ?? `#${b.userId}` }));
    return parts.join(" · ");
  }

  function periodLabel(b) {
    return b.periodType === "YEAR" ? t("budgets:yearShort", { year: String(b.periodMonth ?? b.period).trim() }) : (b.periodMonth ?? b.period);
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
                <Select value={form.categoryId} onChange={(e) => update("categoryId", e.target.value)}>
                  <option value="">{t("budgets:overallOption")}</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.parentId ? `— ${c.name}` : c.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field>
                {t("budgets:walletLabel")}
                <Select value={form.walletId} onChange={(e) => update("walletId", e.target.value)}>
                  <option value="">{t("budgets:allWallets")}</option>
                  {wallets.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field>
                {t("budgets:memberLabel")}
                <Select value={form.userId} onChange={(e) => update("userId", e.target.value)}>
                  <option value="">{t("budgets:allMembers")}</option>
                  {members.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.displayName}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field>
                {t("budgets:periodTypeLabel")}
                <Select value={form.periodType} onChange={(e) => update("periodType", e.target.value)}>
                  <option value="MONTH">{t("budgets:periodMonth")}</option>
                  <option value="YEAR">{t("budgets:periodYear")}</option>
                </Select>
              </Field>
              {form.periodType === "MONTH" ? (
                <Field>
                  <span>
                    {t("budgets:monthLabel")}
                    <span className="required-mark" aria-hidden="true"> *</span>
                  </span>
                  <Input type="month" value={form.periodMonth} onChange={(e) => update("periodMonth", e.target.value)} required />
                </Field>
              ) : (
                <Field>
                  <span>
                    {t("budgets:yearLabel")}
                    <span className="required-mark" aria-hidden="true"> *</span>
                  </span>
                  <Input type="number" min={2000} max={2100} value={form.periodYear}
                    onChange={(e) => update("periodYear", e.target.value)} required />
                </Field>
              )}
              <Field>
                <span>
                  {t("budgets:limitLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <AmountInput
                  placeholder="0"
                  value={form.limitAmount}
                  onChange={(v) => update("limitAmount", v)}
                  required
                  positive
                  min={LIMITS.minTransactionAmount}
                  max={form.periodType === "YEAR" ? LIMITS.maxTransactionAmount * 12 : LIMITS.maxTransactionAmount}
                />
              </Field>
              <label className="inline-flex items-center gap-2 text-sm">
                <input type="checkbox" checked={form.rollover} onChange={(e) => update("rollover", e.target.checked)} />
                {t("budgets:rolloverLabel")}
              </label>
              <Button type="submit">{editingId ? t("budgets:updateButton") : t("budgets:createButton")}</Button>
              {editingId && (
                <Button variant="secondary" onClick={cancelEdit}>
                  {t("common:cancel")}
                </Button>
              )}
            </form>
            <p className="page-header-subtitle">{t("budgets:scopeHint")}</p>
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
        <div className="page-header">
          <h2>{t("budgets:statusTitle")}</h2>
          <label className="month-picker">
            <CalendarIcon />
            <input type="month" value={statusMonth} onChange={(e) => setStatusMonth(e.target.value)} />
          </label>
        </div>
        {statuses.length === 0 ? (
          <p className="empty-state">{t("budgets:noBudgets")}</p>
        ) : (
          <div className="category-breakdown">
            {statuses.map((s) => {
              const status = statusOf(s.spent, s.effectiveLimit);
              const scope = scopeLabel(s);
              return (
                <div className="category-row" key={s.budgetId}>
                  <div className="category-row-header">
                    <span className="category-row-name">
                      {categoryLabel(s.categoryId)}
                      <span className="budget-month">{periodLabel(s)}</span>
                      {scope && <span className="budget-month">{scope}</span>}
                    </span>
                    <span className="category-row-amount">
                      <span className={`badge budget-badge-${STATUS_STYLE[status]}`}>
                        {status === "danger" && <AlertIcon />}
                        {t(STATUS_LABEL_KEYS[status])}
                      </span>
                      {formatCurrency(s.spent)} / {formatCurrency(s.effectiveLimit)}
                      {Number(s.carriedOver) > 0 && (
                        <span className="text-xs text-slate-500">
                          {" "}
                          {t("budgets:carriedOver", { amount: formatCurrency(s.carriedOver) })}
                        </span>
                      )}
                    </span>
                  </div>
                  <div className="category-bar-track">
                    <div
                      className={`category-bar-fill budget-bar-${STATUS_STYLE[status]}`}
                      style={{ width: `${Math.min(s.percent, 100)}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="section-card">
        <h2>{t("budgets:listTitle")}</h2>
        {budgets.length === 0 ? (
          <p className="empty-state">{t("budgets:noBudgets")}</p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>{t("budgets:categoryLabel")}</Th>
                <Th>{t("budgets:colPeriod")}</Th>
                <Th>{t("budgets:colScope")}</Th>
                <Th align="right">{t("budgets:limitLabel")}</Th>
                <Th></Th>
              </tr>
            </THead>
            <TBody>
              {budgets.map((b) => (
                <tr key={b.id}>
                  <Td data-label={t("budgets:categoryLabel")}>{categoryLabel(b.categoryId)}</Td>
                  <Td data-label={t("budgets:colPeriod")}>
                    {periodLabel(b)}
                    {b.rollover && <span className="badge badge-neutral ml-1">{t("budgets:rolloverBadge")}</span>}
                  </Td>
                  <Td data-label={t("budgets:colScope")}>{scopeLabel(b) || t("budgets:wholeFamily")}</Td>
                  <Td data-label={t("budgets:limitLabel")} align="right">{formatCurrency(b.limitAmount)}</Td>
                  <Td actions>
                    <IconButton
                      onClick={() => setHistoryEntity({ type: "BUDGET", id: b.id, title: categoryLabel(b.categoryId) })}
                      aria-label={t("common:historyAria")}
                      title={t("common:historyAria")}
                    >
                      <HistoryIcon />
                    </IconButton>
                    {isOwner && (
                      <>
                        <IconButton onClick={() => startEdit(b)} aria-label={t("common:edit")}>
                          <EditIcon />
                        </IconButton>
                        <IconButton variant="danger" onClick={() => handleDelete(b.id)} aria-label={t("common:delete")}>
                          <TrashIcon />
                        </IconButton>
                      </>
                    )}
                  </Td>
                </tr>
              ))}
            </TBody>
          </Table>
        )}
        <Pagination pageData={pageData} onPageChange={setPage} />
      </div>

      <EntityHistoryModal entity={historyEntity} onClose={() => setHistoryEntity(null)} />
    </div>
  );
}
