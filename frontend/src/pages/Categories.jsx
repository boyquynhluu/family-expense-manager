import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { EditIcon, TrashIcon } from "../components/AppIcons";
import SeedDefaultsButton from "../components/SeedDefaultsButton";
import { Button, IconButton } from "../components/ui/Button";
import { Field } from "../components/ui/Field";
import { Input, Select } from "../components/ui/Input";
import { useAuth } from "../hooks/useAuth";
import { confirmDialog } from "../utils/confirm";
import { LIMITS } from "../utils/inputLimits";
import { hasInvalidIconChars, hasInvalidNameChars } from "../utils/namePatterns";
import { useCleanText } from "../utils/textQuality";
import { notifyTrashChanged } from "../utils/trashEvents";

export default function Categories() {
  const { t } = useTranslation(["common", "categories"]);
  const cleanText = useCleanText();
  // Icon = short code/emoji ("AI", "DX"): profanity only, like the backend's @CleanText(junk = false).
  const cleanIcon = useCleanText({ junk: false });
  // Character rule first (it names what is wrong), then profanity/junk.
  const validateName = (value) =>
    hasInvalidNameChars(value) ? t("categories:nameInvalidChars") : cleanText(value);
  const validateIcon = (value) =>
    hasInvalidIconChars(value) ? t("categories:iconInvalidChars") : cleanIcon(value);
  const { role } = useAuth();
  const isOwner = role === "OWNER";
  const [categories, setCategories] = useState([]);
  const [name, setName] = useState("");
  const [type, setType] = useState("EXPENSE");
  const [icon, setIcon] = useState("");
  const [color, setColor] = useState("#4f46e5");
  // README C6: "" = top-level; otherwise the parent category id (one level only, same type).
  const [parentId, setParentId] = useState("");
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
    setParentId(category.parentId == null ? "" : String(category.parentId));
  }

  function cancelEdit() {
    setEditingId(null);
    setName("");
    setType("EXPENSE");
    setIcon("");
    setColor("#4f46e5");
    setParentId("");
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    const payload = { name, type, icon: icon || null, color: color || null, parentId: parentId ? Number(parentId) : null };
    try {
      if (editingId) {
        if (!(await confirmDialog(t(`categories:submitUpdateConfirm`, {cateName: name})))) return;
        await client.put(`/expenses/categories/${editingId}`, payload);
      } else {
        if (!(await confirmDialog(t(`categories:submidAddConfirm`, {cateName: name})))) return;
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
      notifyTrashChanged();
      toast.success(t("categories:deleteSuccess"));
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("categories:deleteFailed"));
    }
  }

  // Each parent followed by its children (README C6).
  const orderedCategories = categories
    .filter((c) => c.parentId == null || !categories.some((p) => p.id === c.parentId))
    .flatMap((p) => [p, ...categories.filter((c) => c.parentId === p.id)]);

  function parentName(id) {
    return categories.find((c) => c.id === id)?.name ?? `#${id}`;
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("categories:title")}</h1>
          <p className="page-header-subtitle">{t("categories:subtitle")}</p>
        </div>
      </div>

      {isOwner ? (
        <div className="section-card">
          <h2>{editingId ? t("categories:editTitle") : t("categories:addTitle")}</h2>
          <form className="inline-form" onSubmit={handleSubmit}>
            <Field>
              <span>
                {t("categories:nameLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Input
                placeholder={t("categories:namePlaceholder")}
                value={name} validate={validateName}
                maxLength={LIMITS.categoryName}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </Field>
            <Field>
              {t("categories:typeLabel")}
              <Select value={type} onChange={(e) => setType(e.target.value)}>
                <option value="EXPENSE">{t("categories:typeExpense")}</option>
                <option value="INCOME">{t("categories:typeIncome")}</option>
              </Select>
            </Field>
            <Field>
              {t("categories:parentLabel")}
              <Select value={parentId} onChange={(e) => setParentId(e.target.value)}>
                <option value="">{t("categories:noParent")}</option>
                {categories
                  .filter((c) => c.parentId == null && c.type === type && c.id !== editingId)
                  .map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
              </Select>
            </Field>
            <Field>
              {t("categories:iconLabel")}
              <Input
                placeholder={t("categories:iconPlaceholder")}
                value={icon}
                validate={validateIcon}
                maxLength={LIMITS.categoryIcon}
                onChange={(e) => setIcon(e.target.value)}
              />
            </Field>
            <Field>
              {t("categories:colorLabel")}
              <Input type="color" value={color} onChange={(e) => setColor(e.target.value)} />
            </Field>
            <Button type="submit">{editingId ? t("categories:submitUpdate") : t("categories:submitAdd")}</Button>
            {editingId && (
              <Button variant="secondary" onClick={cancelEdit}>
                {t("common:cancel")}
              </Button>
            )}
          </form>
          {error && <p className="error-text">{error}</p>}
        </div>
      ) : (
        <div className="section-card">
          <p className="page-header-subtitle">{t("categories:ownerOnlyManage")}</p>
        </div>
      )}

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
            {orderedCategories.map((c) => (
              <div className="category-chip" key={c.id} style={c.parentId ? { marginLeft: 24 } : undefined}>
                <span className="category-chip-icon" style={{ backgroundColor: c.color || "#9ca3af" }}>
                  {c.icon || c.name.charAt(0).toUpperCase()}
                </span>
                <div className="category-chip-body">
                  <span className="category-chip-name">
                    {c.name}
                    {c.parentId != null && (
                      <span className="text-xs text-slate-500"> · {t("categories:childOf", { name: parentName(c.parentId) })}</span>
                    )}
                  </span>
                  <span className={`badge ${c.type === "EXPENSE" ? "badge-expense" : "badge-income"}`}>
                    {c.type === "EXPENSE" ? t("categories:typeExpense") : t("categories:typeIncome")}
                  </span>
                </div>
                {isOwner && (
                  <div className="row-actions">
                    <IconButton onClick={() => startEdit(c)} aria-label={t("common:edit")}>
                      <EditIcon />
                    </IconButton>
                    <IconButton variant="danger"
                      onClick={() => handleDelete(c.id)}
                      aria-label={t("common:delete")}
                    >
                      <TrashIcon />
                    </IconButton>
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
