import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { EditIcon, TrashIcon } from "../components/AppIcons";
import AmountInput from "../components/AmountInput";
import Pagination from "../components/Pagination";
import { useAuth } from "../hooks/useAuth";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency } from "../utils/format";

const emptyForm = {
  walletId: "",
  categoryId: "",
  type: "EXPENSE",
  amount: "",
  note: "",
  frequency: "MONTHLY",
  dayOfMonth: "1",
  dayOfWeek: "1",
  monthOfYear: "1",
  startDate: "",
  endDate: "",
};

export default function RecurringTransactions() {
  const { t } = useTranslation(["common", "recurringTransactions"]);
  const { role, userId } = useAuth();
  const isOwner = role === "OWNER";
  const { pageData, setPage, reload } = usePagedList("/expenses/recurring-transactions");
  const rules = pageData.content;
  const [wallets, setWallets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
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
      frequency: rule.frequency ?? "MONTHLY",
      dayOfMonth: String(rule.dayOfMonth ?? 1),
      dayOfWeek: String(rule.dayOfWeek ?? 1),
      monthOfYear: String(rule.monthOfYear ?? 1),
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
      frequency: form.frequency,
      dayOfMonth: form.frequency === "WEEKLY" ? null : Number(form.dayOfMonth),
      dayOfWeek: form.frequency === "WEEKLY" ? Number(form.dayOfWeek) : null,
      monthOfYear: form.frequency === "YEARLY" ? Number(form.monthOfYear) : null,
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
      reload();
    } catch (err) {
      setError(err.response?.data?.message || t("recurringTransactions:saveFailed"));
    }
  }

  async function handleDelete(id) {
    if (!(await confirmDialog(t("recurringTransactions:deleteConfirm")))) return;
    setError("");
    try {
      await client.delete(`/expenses/recurring-transactions/${id}`);
      reload();
    } catch (err) {
      setError(err.response?.data?.message || t("recurringTransactions:deleteFailed"));
    }
  }

  async function toggleActive(rule) {
    setError("");
    try {
      await client.put(`/expenses/recurring-transactions/${rule.id}/active`, { active: !rule.active });
      reload();
    } catch (err) {
      setError(err.response?.data?.message || t("recurringTransactions:toggleFailed"));
    }
  }

  function canModify(rule) {
    return isOwner || String(rule.createdByUserId) === String(userId);
  }

  function scheduleText(rule) {
    if (rule.frequency === "WEEKLY") {
      return t("recurringTransactions:scheduleWeekly", {
        weekday: t(`recurringTransactions:weekday${rule.dayOfWeek}`),
      });
    }
    if (rule.frequency === "YEARLY") {
      return t("recurringTransactions:scheduleYearly", {
        day: String(rule.dayOfMonth).padStart(2, "0"),
        month: String(rule.monthOfYear).padStart(2, "0"),
      });
    }
    return t("recurringTransactions:scheduleMonthly", { day: rule.dayOfMonth });
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
          <h1>{t("recurringTransactions:title")}</h1>
          <p className="page-header-subtitle">{t("recurringTransactions:subtitle")}</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? t("recurringTransactions:editTitle") : t("recurringTransactions:newTitle")}</h2>
        {wallets.length === 0 || categories.length === 0 ? (
          <p className="empty-state">
            {wallets.length === 0 && categories.length === 0
              ? t("recurringTransactions:noWalletsAndCategories")
              : wallets.length === 0
                ? t("recurringTransactions:noWallets")
                : t("recurringTransactions:noCategories")}
          </p>
        ) : (
          <form className="inline-form" onSubmit={handleSubmit}>
            <label className="field">
              <span>
                {t("recurringTransactions:walletLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <select value={form.walletId} onChange={(e) => updateField("walletId", e.target.value)} required>
                {wallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>
                {t("recurringTransactions:categoryLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <select value={form.categoryId} onChange={(e) => updateField("categoryId", e.target.value)} required>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              {t("recurringTransactions:typeLabel")}
              <select value={form.type} onChange={(e) => updateField("type", e.target.value)}>
                <option value="EXPENSE">{t("recurringTransactions:typeExpense")}</option>
                <option value="INCOME">{t("recurringTransactions:typeIncome")}</option>
              </select>
            </label>
            <label className="field">
              <span>
                {t("recurringTransactions:amountLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <AmountInput placeholder="0" value={form.amount} onChange={(v) => updateField("amount", v)} required />
            </label>
            <label className="field">
              <span>
                {t("recurringTransactions:frequencyLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <select value={form.frequency} onChange={(e) => updateField("frequency", e.target.value)} required>
                <option value="MONTHLY">{t("recurringTransactions:frequencyMonthly")}</option>
                <option value="WEEKLY">{t("recurringTransactions:frequencyWeekly")}</option>
                <option value="YEARLY">{t("recurringTransactions:frequencyYearly")}</option>
              </select>
            </label>
            {form.frequency === "WEEKLY" && (
              <label className="field">
                <span>
                  {t("recurringTransactions:dayOfWeekLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <select value={form.dayOfWeek} onChange={(e) => updateField("dayOfWeek", e.target.value)} required>
                  {[1, 2, 3, 4, 5, 6, 7].map((d) => (
                    <option key={d} value={d}>
                      {t(`recurringTransactions:weekday${d}`)}
                    </option>
                  ))}
                </select>
              </label>
            )}
            {form.frequency === "YEARLY" && (
              <label className="field">
                <span>
                  {t("recurringTransactions:monthOfYearLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <select value={form.monthOfYear} onChange={(e) => updateField("monthOfYear", e.target.value)} required>
                  {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((m) => (
                    <option key={m} value={m}>
                      {t("recurringTransactions:monthOption", { month: m })}
                    </option>
                  ))}
                </select>
              </label>
            )}
            {form.frequency !== "WEEKLY" && (
              <label className="field">
                <span>
                  {t("recurringTransactions:dayOfMonthLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <input
                  type="number"
                  min="1"
                  max="31"
                  value={form.dayOfMonth}
                  onChange={(e) => updateField("dayOfMonth", e.target.value)}
                  required
                />
              </label>
            )}
            <label className="field">
              <span>
                {t("recurringTransactions:startDateLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <input
                type="date"
                value={form.startDate}
                onChange={(e) => updateField("startDate", e.target.value)}
                required
              />
            </label>
            <label className="field">
              {t("recurringTransactions:endDateLabel")}
              <input type="date" value={form.endDate} onChange={(e) => updateField("endDate", e.target.value)} />
            </label>
            <label className="field">
              {t("recurringTransactions:noteLabel")}
              <input
                placeholder={t("recurringTransactions:optionalPlaceholder")}
                value={form.note}
                onChange={(e) => updateField("note", e.target.value)}
              />
            </label>
            <button type="submit">
              {editingId ? t("recurringTransactions:updateButton") : t("recurringTransactions:addButton")}
            </button>
            {editingId && (
              <button type="button" className="btn-secondary" onClick={cancelEdit}>
                {t("common:cancel")}
              </button>
            )}
          </form>
        )}
        {error && <p className="error-text">{error}</p>}
      </div>

      <div className="section-card">
        <h2>{t("recurringTransactions:listTitle")}</h2>
        {rules.length === 0 ? (
          <p className="empty-state">{t("recurringTransactions:noRules")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("recurringTransactions:walletLabel")}</th>
                <th>{t("recurringTransactions:categoryLabel")}</th>
                <th>{t("recurringTransactions:typeLabel")}</th>
                <th>{t("recurringTransactions:amountLabel")}</th>
                <th>{t("recurringTransactions:scheduleLabel")}</th>
                <th>{t("recurringTransactions:nextRunLabel")}</th>
                <th>{t("recurringTransactions:executionStatusLabel")}</th>
                <th>{t("recurringTransactions:statusLabel")}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rules.map((r) => (
                <tr key={r.id}>
                  <td data-label={t("recurringTransactions:walletLabel")}>{walletName(r.walletId)}</td>
                  <td data-label={t("recurringTransactions:categoryLabel")}>{categoryName(r.categoryId)}</td>
                  <td data-label={t("recurringTransactions:typeLabel")}>
                    <span className={`badge ${r.type === "EXPENSE" ? "badge-expense" : "badge-income"}`}>
                      {r.type === "EXPENSE" ? t("recurringTransactions:typeExpense") : t("recurringTransactions:typeIncome")}
                    </span>
                  </td>
                  <td
                    data-label={t("recurringTransactions:amountLabel")}
                    className={r.type === "EXPENSE" ? "amount-expense" : "amount-income"}
                  >
                    {r.type === "EXPENSE" ? "-" : "+"}
                    {formatCurrency(r.amount)}
                  </td>
                  <td data-label={t("recurringTransactions:scheduleLabel")}>{scheduleText(r)}</td>
                  <td data-label={t("recurringTransactions:nextRunLabel")}>{r.active ? r.nextRunDate : "-"}</td>
                  <td data-label={t("recurringTransactions:executionStatusLabel")}>
                    <span className={`badge ${r.lastRunDate ? "badge-income" : "badge-neutral"}`}>
                      {r.lastRunDate
                        ? t("recurringTransactions:executionCompleted")
                        : t("recurringTransactions:executionPending")}
                    </span>
                  </td>
                  <td data-label={t("recurringTransactions:statusLabel")}>
                    <span className={`badge ${r.active ? "badge-income" : "badge-expense"}`}>
                      {r.active ? t("recurringTransactions:statusActive") : t("recurringTransactions:statusPaused")}
                    </span>
                  </td>
                  <td className="row-actions">
                    {canModify(r) && (
                      <>
                        <button type="button" className="btn-secondary" onClick={() => toggleActive(r)}>
                          {r.active ? t("recurringTransactions:pauseButton") : t("recurringTransactions:activateButton")}
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          onClick={() => startEdit(r)}
                          aria-label={t("common:edit")}
                        >
                          <EditIcon />
                        </button>
                        <button
                          type="button"
                          className="icon-btn icon-btn-danger"
                          onClick={() => handleDelete(r.id)}
                          aria-label={t("common:delete")}
                        >
                          <TrashIcon />
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination pageData={pageData} onPageChange={setPage} />
      </div>
    </div>
  );
}
