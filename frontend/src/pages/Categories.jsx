import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { EditIcon, TrashIcon } from "../components/AppIcons";
import SeedDefaultsButton from "../components/SeedDefaultsButton";
import { confirmDialog } from "../utils/confirm";
import { useAuth } from "../hooks/useAuth";

export default function Categories() {
  const { t } = useTranslation(["common", "categories"]);
  const { role } = useAuth();
  const isOwner = role === "OWNER";
  const [categories, setCategories] = useState([]);
  const [name, setName] = useState("");
  const [type, setType] = useState("EXPENSE");
  const [icon, setIcon] = useState("");
  const [color, setColor] = useState("#4f46e5");
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/categories").then((res) => setCategories(res.data.data));
  }

  useEffect(load, []);

  function startEdit(category) {
    setEditingId(category.id);
    setName(category.name);
    setType(category.type);
    setIcon(category.icon ?? "");
    setColor(category.color ?? "#4f46e5");
  }

  function cancelEdit() {
    setEditingId(null);
    setName("");
    setType("EXPENSE");
    setIcon("");
    setColor("#4f46e5");
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    const payload = { name, type, icon: icon || null, color: color || null };
    try {
      if (editingId) {
        await client.put(`/expenses/categories/${editingId}`, payload);
      } else {
        await client.post("/expenses/categories", payload);
      }
      cancelEdit();
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("categories:saveFailed"));
    }
  }

  async function handleDelete(id) {
    if (!(await confirmDialog(t("categories:deleteConfirm")))) return;
    setError("");
    try {
      await client.delete(`/expenses/categories/${id}`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("categories:deleteFailed"));
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("categories:title")}</h1>
          <p className="page-header-subtitle">{t("categories:subtitle")}</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? t("categories:editTitle") : t("categories:addTitle")}</h2>
        <form className="inline-form" onSubmit={handleSubmit}>
          <label className="field">
            <span>
              {t("categories:nameLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <input
              placeholder={t("categories:namePlaceholder")}
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </label>
          <label className="field">
            {t("categories:typeLabel")}
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="EXPENSE">{t("categories:typeExpense")}</option>
              <option value="INCOME">{t("categories:typeIncome")}</option>
            </select>
          </label>
          <label className="field">
            {t("categories:iconLabel")}
            <input placeholder={t("categories:iconPlaceholder")} value={icon} onChange={(e) => setIcon(e.target.value)} />
          </label>
          <label className="field">
            {t("categories:colorLabel")}
            <input type="color" value={color} onChange={(e) => setColor(e.target.value)} />
          </label>
          <button type="submit">{editingId ? t("categories:submitUpdate") : t("categories:submitAdd")}</button>
          {editingId && (
            <button type="button" className="btn-secondary" onClick={cancelEdit}>
              {t("common:cancel")}
            </button>
          )}
        </form>
        {error && <p className="error-text">{error}</p>}
      </div>

      <div className="section-card">
        <h2>{t("categories:listTitle")}</h2>
        {categories.length === 0 ? (
          <div>
            <p className="empty-state">{t("categories:emptyState")}</p>
            <p className="page-header-subtitle">{t("categories:seedDefaultsHint")}</p>
            <SeedDefaultsButton onDone={load} />
          </div>
        ) : (
          <div className="category-chip-grid">
            {categories.map((c) => (
              <div className="category-chip" key={c.id}>
                <span className="category-chip-icon" style={{ backgroundColor: c.color || "#9ca3af" }}>
                  {c.icon || c.name.charAt(0).toUpperCase()}
                </span>
                <div className="category-chip-body">
                  <span className="category-chip-name">{c.name}</span>
                  <span className={`badge ${c.type === "EXPENSE" ? "badge-expense" : "badge-income"}`}>
                    {c.type === "EXPENSE" ? t("categories:typeExpense") : t("categories:typeIncome")}
                  </span>
                </div>
                <div className="row-actions">
                  <button type="button" className="icon-btn" onClick={() => startEdit(c)} aria-label={t("common:edit")}>
                    <EditIcon />
                  </button>
                  {isOwner && (
                    <button
                      type="button"
                      className="icon-btn icon-btn-danger"
                      onClick={() => handleDelete(c.id)}
                      aria-label={t("common:delete")}
                    >
                      <TrashIcon />
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
