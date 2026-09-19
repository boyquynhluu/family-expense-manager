import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { EditIcon, TrashIcon } from "../components/AppIcons";
import { formatCurrency } from "../utils/format";

const emptyForm = {
  walletId: "",
  categoryId: "",
  type: "EXPENSE",
  amount: "",
  note: "",
  dayOfMonth: "1",
  startDate: "",
  endDate: "",
};

export default function RecurringTransactions() {
  const { t } = useTranslation(["common", "recurringTransactions"]);
  const [rules, setRules] = useState([]);
  const [wallets, setWallets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/recurring-transactions").then((res) => setRules(res.data.data));
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

  function startEdit(rule) {
    setEditingId(rule.id);
    setForm({
      walletId: String(rule.walletId),
      categoryId: String(rule.categoryId),
      type: rule.type,
      amount: String(rule.amount),
      note: rule.note ?? "",
      dayOfMonth: String(rule.dayOfMonth),
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
      dayOfMonth: Number(form.dayOfMonth),
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
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("recurringTransactions:saveFailed"));
    }
  }

  async function handleDelete(id) {
    if (!window.confirm(t("recurringTransactions:deleteConfirm"))) return;
    setError("");
    try {
      await client.delete(`/expenses/recurring-transactions/${id}`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("recurringTransactions:deleteFailed"));
    }
  }

  async function toggleActive(rule) {
    setError("");
    try {
      await client.put(`/expenses/recurring-transactions/${rule.id}/active`, { active: !rule.active });
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("recurringTransactions:toggleFailed"));
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
              {t("recurringTransactions:walletLabel")}
              <select value={form.walletId} onChange={(e) => updateField("walletId", e.target.value)} required>
                {wallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              {t("recurringTransactions:categoryLabel")}
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
              {t("recurringTransactions:amountLabel")}
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
              {t("recurringTransactions:dayOfMonthLabel")}
              <input
                type="number"
                min="1"
                max="31"
                value={form.dayOfMonth}
                onChange={(e) => updateField("dayOfMonth", e.target.value)}
                required
              />
            </label>
            <label className="field">
              {t("recurringTransactions:startDateLabel")}
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
                <th>{t("recurringTransactions:dayOfMonthLabel")}</th>
                <th>{t("recurringTransactions:nextRunLabel")}</th>
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
                  <td data-label={t("recurringTransactions:dayOfMonthLabel")}>
                    {t("recurringTransactions:dayPrefix")} {r.dayOfMonth}
                  </td>
                  <td data-label={t("recurringTransactions:nextRunLabel")}>{r.active ? r.nextRunDate : "-"}</td>
                  <td data-label={t("recurringTransactions:statusLabel")}>
                    <span className={`badge ${r.active ? "badge-income" : "badge-expense"}`}>
                      {r.active ? t("recurringTransactions:statusActive") : t("recurringTransactions:statusPaused")}
                    </span>
                  </td>
                  <td className="row-actions">
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
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
