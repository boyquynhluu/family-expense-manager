import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import AmountInput from "../components/AmountInput";
import { EditIcon, TrashIcon } from "../components/AppIcons";
import { Button, IconButton } from "../components/ui/Button";
import { Field } from "../components/ui/Field";
import { Input, Select } from "../components/ui/Input";
import { useAuth } from "../hooks/useAuth";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency } from "../utils/format";
import { useCleanText } from "../utils/textQuality";

function emptyForm() {
  return { name: "", targetAmount: "", deadline: "", walletId: "", archived: false };
}

/**
 * README C1 "Mục tiêu tiết kiệm": a target, an optional deadline and the wallet the money is kept in — progress is
 * that wallet's balance. The family is told (in app + email) when a goal reaches 50, 80 and 100%.
 */
export default function Goals() {
  const { t } = useTranslation(["goals", "common"]);
  const cleanText = useCleanText();
  const { role, userId } = useAuth();
  const isOwner = role === "OWNER";
  const [goals, setGoals] = useState([]);
  const [wallets, setWallets] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/savings-goals").then((res) => setGoals(res.data.data));
  }

  useEffect(() => {
    load();
    client.get("/expenses/wallets").then((res) => {
      setWallets(res.data.data);
      const preferred = res.data.data.find((w) => w.walletType === "SAVINGS") ?? res.data.data[0];
      if (preferred) setForm((f) => (f.walletId ? f : { ...f, walletId: String(preferred.id) }));
    });
  }, []);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function startEdit(goal) {
    setEditingId(goal.id);
    setForm({
      name: goal.name,
      targetAmount: String(goal.targetAmount),
      deadline: goal.deadline ?? "",
      walletId: String(goal.walletId),
      archived: Boolean(goal.archived),
    });
  }

  function cancelEdit() {
    setEditingId(null);
    setForm((f) => ({ ...emptyForm(), walletId: f.walletId }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const question = t(editingId ? "goals:updateConfirm" : "goals:addConfirm", {
      name: form.name,
      amount: formatCurrency(Number(form.targetAmount)),
    });
    if (!(await confirmDialog(question, { tone: "primary", icon: "question" }))) return;
    setError("");
    const payload = {
      name: form.name,
      targetAmount: Number(form.targetAmount),
      deadline: form.deadline || null,
      walletId: Number(form.walletId),
      archived: form.archived,
    };
    try {
      if (editingId) {
        await client.put(`/expenses/savings-goals/${editingId}`, payload);
      } else {
        await client.post("/expenses/savings-goals", payload);
      }
      toast.success(t("goals:saved"));
      cancelEdit();
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("goals:saveFailed"));
    }
  }

  async function handleDelete(goal) {
    if (!(await confirmDialog(t("goals:deleteConfirm", { name: goal.name })))) return;
    try {
      await client.delete(`/expenses/savings-goals/${goal.id}`);
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || t("goals:saveFailed"));
    }
  }

  function walletName(id) {
    return wallets.find((w) => w.id === id)?.name ?? `#${id}`;
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("goals:title")}</h1>
          <p className="page-header-subtitle">{t("goals:subtitle")}</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? t("goals:editTitle") : t("goals:addTitle")}</h2>
        <form className="inline-form" onSubmit={handleSubmit}>
          <Field>
            <span>
              {t("goals:nameLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <Input value={form.name} validate={cleanText} maxLength={100} placeholder={t("goals:namePlaceholder")}
              onChange={(e) => update("name", e.target.value)} required />
          </Field>
          <Field>
            <span>
              {t("goals:targetLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <AmountInput value={form.targetAmount} onChange={(v) => update("targetAmount", v)} required positive />
          </Field>
          <Field>
            {t("goals:deadlineLabel")}
            <Input type="date" value={form.deadline} onChange={(e) => update("deadline", e.target.value)} />
          </Field>
          <Field>
            {t("goals:walletLabel")}
            <Select value={form.walletId} onChange={(e) => update("walletId", e.target.value)} required>
              {wallets.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </Select>
          </Field>
          {editingId && (
            <label className="inline-flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.archived} onChange={(e) => update("archived", e.target.checked)} />
              {t("goals:archivedLabel")}
            </label>
          )}
          <Button type="submit">{editingId ? t("common:save") : t("goals:addButton")}</Button>
          {editingId && (
            <Button variant="secondary" onClick={cancelEdit}>
              {t("common:cancel")}
            </Button>
          )}
        </form>
        <p className="page-header-subtitle">{t("goals:hint")}</p>
        {error && <p className="error-text">{error}</p>}
      </div>

      <div className="section-card">
        <h2>{t("goals:listTitle")}</h2>
        {goals.length === 0 ? (
          <p className="empty-state">{t("goals:empty")}</p>
        ) : (
          <div className="category-breakdown">
            {goals.map((g) => (
              <div className="category-row" key={g.id} style={g.archived ? { opacity: 0.6 } : undefined}>
                <div className="category-row-header">
                  <span className="category-row-name">
                    {g.name}
                    <span className="budget-month">{walletName(g.walletId)}</span>
                    {g.deadline && <span className="budget-month">{t("goals:deadlineShort", { date: g.deadline })}</span>}
                    {g.archived && <span className="badge badge-neutral ml-1">{t("goals:archivedBadge")}</span>}
                  </span>
                  <span className="category-row-amount">
                    {g.percent >= 100 && <span className="badge budget-badge-safe">{t("goals:reached")}</span>}
                    {formatCurrency(g.saved)} / {formatCurrency(g.targetAmount)} ({g.percent}%)
                    {(isOwner || String(g.createdByUserId) === String(userId)) && (
                      <span className="row-actions">
                        <IconButton onClick={() => startEdit(g)} aria-label={t("common:edit")}>
                          <EditIcon />
                        </IconButton>
                        <IconButton variant="danger" onClick={() => handleDelete(g)} aria-label={t("common:delete")}>
                          <TrashIcon />
                        </IconButton>
                      </span>
                    )}
                  </span>
                </div>
                <div className="category-bar-track">
                  <div className="category-bar-fill budget-bar-safe" style={{ width: `${Math.min(g.percent, 100)}%` }} />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
