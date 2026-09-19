import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { CloseIcon, EditIcon, ImageIcon, TrashIcon } from "../components/AppIcons";
import { formatCurrency } from "../utils/format";

const emptyForm = {
  walletId: "",
  categoryId: "",
  type: "EXPENSE",
  amount: "",
  occurredAt: "",
  note: "",
};

const emptyFilter = {
  walletId: "",
  categoryId: "",
  type: "",
  fromDate: "",
  toDate: "",
};

const PAGE_SIZE = 20;

const emptyPage = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

export default function Transactions() {
  const { t } = useTranslation(["common", "transactions"]);
  const [pageData, setPageData] = useState(emptyPage);
  const [wallets, setWallets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [filter, setFilter] = useState(emptyFilter);
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");
  const fileInputRef = useRef(null);
  const [uploadTargetId, setUploadTargetId] = useState(null);
  const importInputRef = useRef(null);
  const [importing, setImporting] = useState(false);

  // Filtering/paging happens on the backend now (see README "1. Phân trang/lọc chỉ làm
  // ở frontend") — the client only ever holds the current page's rows.
  function load() {
    const params = { page, size: PAGE_SIZE };
    if (filter.walletId) params.walletId = filter.walletId;
    if (filter.categoryId) params.categoryId = filter.categoryId;
    if (filter.type) params.type = filter.type;
    if (filter.fromDate) params.fromDate = filter.fromDate;
    if (filter.toDate) params.toDate = filter.toDate;

    client.get("/expenses/transactions", { params }).then((res) => {
      const data = res.data.data;
      // Deleting the last row on a page beyond the first leaves it empty — step back
      // one page rather than showing a stranded "no results" screen.
      if (data.content.length === 0 && data.page > 0 && data.totalElements > 0) {
        setPage(data.page - 1);
      } else {
        setPageData(data);
      }
    });
  }

  useEffect(load, [filter, page]);

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

  function startEdit(transaction) {
    setEditingId(transaction.id);
    setForm({
      walletId: String(transaction.walletId),
      categoryId: String(transaction.categoryId),
      type: transaction.type,
      amount: String(transaction.amount),
      occurredAt: transaction.occurredAt.slice(0, 16),
      note: transaction.note ?? "",
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
      occurredAt: form.occurredAt,
      note: form.note || null,
    };
    try {
      if (editingId) {
        await client.put(`/expenses/transactions/${editingId}`, payload);
      } else {
        await client.post("/expenses/transactions", payload);
      }
      cancelEdit();
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("transactions:saveFailed"));
    }
  }

  async function handleDelete(id) {
    if (!window.confirm(t("transactions:deleteConfirm"))) return;
    setError("");
    try {
      await client.delete(`/expenses/transactions/${id}`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("transactions:deleteFailed"));
    }
  }

  function triggerUpload(id) {
    setUploadTargetId(id);
    fileInputRef.current?.click();
  }

  async function handleFileSelected(e) {
    const file = e.target.files?.[0];
    e.target.value = ""; // allow re-selecting the same file (e.g. after a failed upload)
    if (!file || !uploadTargetId) return;
    setError("");
    const formData = new FormData();
    formData.append("file", file);
    try {
      await client.post(`/expenses/transactions/${uploadTargetId}/receipt`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("transactions:uploadReceiptFailed"));
    }
  }

  async function viewReceipt(id) {
    setError("");
    try {
      const res = await client.get(`/expenses/transactions/${id}/receipt`, { responseType: "blob" });
      const url = URL.createObjectURL(res.data);
      window.open(url, "_blank");
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(err.response?.data?.message || t("transactions:viewReceiptFailed"));
    }
  }

  async function handleDeleteReceipt(id) {
    if (!window.confirm(t("transactions:deleteReceiptConfirm"))) return;
    setError("");
    try {
      await client.delete(`/expenses/transactions/${id}/receipt`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("transactions:deleteReceiptFailed"));
    }
  }

  function walletName(id) {
    return wallets.find((w) => w.id === id)?.name ?? `#${id}`;
  }

  function categoryName(id) {
    return categories.find((c) => c.id === id)?.name ?? `#${id}`;
  }

  function updateFilter(field, value) {
    setFilter((f) => ({ ...f, [field]: value }));
    setPage(0);
  }

  function clearFilter() {
    setFilter(emptyFilter);
    setPage(0);
  }

  const hasActiveFilter = Object.values(filter).some(Boolean);

  async function exportReport(format) {
    setError("");
    const params = { format };
    if (filter.walletId) params.walletId = filter.walletId;
    if (filter.categoryId) params.categoryId = filter.categoryId;
    if (filter.type) params.type = filter.type;
    if (filter.fromDate) params.fromDate = filter.fromDate;
    if (filter.toDate) params.toDate = filter.toDate;

    try {
      const res = await client.get("/expenses/transactions/export", { params, responseType: "blob" });
      const extension = format === "EXCEL" ? "xlsx" : "csv";
      const url = URL.createObjectURL(res.data);
      const link = document.createElement("a");
      link.href = url;
      link.download = `giao-dich-${new Date().toISOString().slice(0, 10)}.${extension}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err.response?.data?.message || t("transactions:exportFailed"));
    }
  }

  function triggerImport() {
    importInputRef.current?.click();
  }

  async function handleImportFile(e) {
    const file = e.target.files?.[0];
    e.target.value = ""; // allow re-selecting the same file (e.g. after fixing errors)
    if (!file) return;
    setError("");
    setImporting(true);
    const formData = new FormData();
    formData.append("file", file);
    try {
      const res = await client.post("/expenses/transactions/import", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      const result = res.data.data;
      if (result.importedCount > 0) {
        toast.success(t("transactions:importSuccess", { imported: result.importedCount, total: result.totalRows }));
      }
      if (result.errors.length > 0) {
        const preview = result.errors
          .slice(0, 5)
          .map((e) => t("transactions:importRowError", { row: e.rowNumber, message: e.message }))
          .join("\n");
        const more =
          result.errors.length > 5
            ? `\n${t("transactions:importMoreErrors", { count: result.errors.length - 5 })}`
            : "";
        setError(`${t("transactions:importErrorsHeader", { count: result.errors.length })}\n${preview}${more}`);
      }
      if (result.importedCount > 0) {
        setPage(0);
        load();
      }
    } catch (err) {
      setError(err.response?.data?.message || t("transactions:importFailed"));
    } finally {
      setImporting(false);
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("transactions:title")}</h1>
          <p className="page-header-subtitle">{t("transactions:subtitle")}</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? t("transactions:editFormTitle") : t("transactions:addFormTitle")}</h2>
        {wallets.length === 0 || categories.length === 0 ? (
          <p className="empty-state">
            {wallets.length === 0 && categories.length === 0
              ? t("transactions:needWalletAndCategory")
              : wallets.length === 0
                ? t("transactions:needWallet")
                : t("transactions:needCategory")}
          </p>
        ) : (
          <form className="inline-form" onSubmit={handleSubmit}>
            <label className="field">
              {t("transactions:walletLabel")}
              <select value={form.walletId} onChange={(e) => updateField("walletId", e.target.value)} required>
                {wallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              {t("transactions:categoryLabel")}
              <select value={form.categoryId} onChange={(e) => updateField("categoryId", e.target.value)} required>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              {t("transactions:typeLabel")}
              <select value={form.type} onChange={(e) => updateField("type", e.target.value)}>
                <option value="EXPENSE">{t("transactions:typeExpense")}</option>
                <option value="INCOME">{t("transactions:typeIncome")}</option>
              </select>
            </label>
            <label className="field">
              {t("transactions:amountLabel")}
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
              {t("transactions:timeLabel")}
              <input
                type="datetime-local"
                value={form.occurredAt}
                onChange={(e) => updateField("occurredAt", e.target.value)}
                required
              />
            </label>
            <label className="field">
              {t("transactions:noteLabel")}
              <input
                placeholder={t("transactions:notePlaceholder")}
                value={form.note}
                onChange={(e) => updateField("note", e.target.value)}
              />
            </label>
            <button type="submit">{editingId ? t("transactions:submitUpdate") : t("transactions:submitAdd")}</button>
            {editingId && (
              <button type="button" className="btn-secondary" onClick={cancelEdit}>
                {t("common:cancel")}
              </button>
            )}
          </form>
        )}
        {error && (
          <p className="error-text" style={{ whiteSpace: "pre-line" }}>
            {error}
          </p>
        )}
      </div>

      <input
        type="file"
        ref={fileInputRef}
        accept="image/jpeg,image/png,image/webp"
        style={{ display: "none" }}
        onChange={handleFileSelected}
      />
      <input
        type="file"
        ref={importInputRef}
        accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        style={{ display: "none" }}
        onChange={handleImportFile}
      />

      <div className="section-card">
        <div className="page-header">
          <h2>{t("transactions:historyTitle")}</h2>
          <div className="row-actions">
            <button type="button" className="btn-secondary" onClick={triggerImport} disabled={importing}>
              {importing ? t("transactions:importing") : t("transactions:importButton")}
            </button>
            {pageData.totalElements > 0 && (
              <>
                <button type="button" className="btn-secondary" onClick={() => exportReport("CSV")}>
                  {t("transactions:exportCsv")}
                </button>
                <button type="button" className="btn-secondary" onClick={() => exportReport("EXCEL")}>
                  {t("transactions:exportExcel")}
                </button>
              </>
            )}
          </div>
        </div>
        <p className="page-header-subtitle">{t("transactions:importHint")}</p>

        <form className="inline-form filter-bar" onSubmit={(e) => e.preventDefault()}>
          <label className="field">
            {t("transactions:walletLabel")}
            <select value={filter.walletId} onChange={(e) => updateFilter("walletId", e.target.value)}>
              <option value="">{t("transactions:allOption")}</option>
              {wallets.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            {t("transactions:categoryLabel")}
            <select value={filter.categoryId} onChange={(e) => updateFilter("categoryId", e.target.value)}>
              <option value="">{t("transactions:allOption")}</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            {t("transactions:typeLabel")}
            <select value={filter.type} onChange={(e) => updateFilter("type", e.target.value)}>
              <option value="">{t("transactions:allOption")}</option>
              <option value="EXPENSE">{t("transactions:typeExpense")}</option>
              <option value="INCOME">{t("transactions:typeIncome")}</option>
            </select>
          </label>
          <label className="field">
            {t("transactions:fromDateLabel")}
            <input type="date" value={filter.fromDate} onChange={(e) => updateFilter("fromDate", e.target.value)} />
          </label>
          <label className="field">
            {t("transactions:toDateLabel")}
            <input type="date" value={filter.toDate} onChange={(e) => updateFilter("toDate", e.target.value)} />
          </label>
          {hasActiveFilter && (
            <button type="button" className="btn-secondary" onClick={clearFilter}>
              {t("transactions:clearFilter")}
            </button>
          )}
        </form>

        {pageData.content.length === 0 ? (
          <p className="empty-state">
            {hasActiveFilter ? t("transactions:noMatchFilter") : t("transactions:emptyState")}
          </p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("transactions:timeLabel")}</th>
                <th>{t("transactions:walletLabel")}</th>
                <th>{t("transactions:categoryLabel")}</th>
                <th>{t("transactions:typeLabel")}</th>
                <th>{t("transactions:amountLabel")}</th>
                <th>{t("transactions:noteLabel")}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {pageData.content.map((row) => (
                <tr key={row.id}>
                  <td data-label={t("transactions:timeLabel")}>{row.occurredAt.replace("T", " ")}</td>
                  <td data-label={t("transactions:walletLabel")}>{walletName(row.walletId)}</td>
                  <td data-label={t("transactions:categoryLabel")}>{categoryName(row.categoryId)}</td>
                  <td data-label={t("transactions:typeLabel")}>
                    <span className={`badge ${row.type === "EXPENSE" ? "badge-expense" : "badge-income"}`}>
                      {row.type === "EXPENSE" ? t("transactions:typeExpense") : t("transactions:typeIncome")}
                    </span>
                  </td>
                  <td
                    data-label={t("transactions:amountLabel")}
                    className={row.type === "EXPENSE" ? "amount-expense" : "amount-income"}
                  >
                    {row.type === "EXPENSE" ? "-" : "+"}
                    {formatCurrency(row.amount)}
                  </td>
                  <td data-label={t("transactions:noteLabel")}>{row.note}</td>
                  <td className="row-actions">
                    {row.hasReceipt ? (
                      <>
                        <button
                          type="button"
                          className="icon-btn"
                          onClick={() => viewReceipt(row.id)}
                          aria-label={t("transactions:viewReceiptAria")}
                        >
                          <ImageIcon />
                        </button>
                        <button
                          type="button"
                          className="icon-btn icon-btn-danger"
                          onClick={() => handleDeleteReceipt(row.id)}
                          aria-label={t("transactions:deleteReceiptAria")}
                        >
                          <CloseIcon />
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        className="icon-btn"
                        onClick={() => triggerUpload(row.id)}
                        aria-label={t("transactions:attachReceiptAria")}
                      >
                        <ImageIcon />
                      </button>
                    )}
                    <button type="button" className="icon-btn" onClick={() => startEdit(row)} aria-label={t("common:edit")}>
                      <EditIcon />
                    </button>
                    <button
                      type="button"
                      className="icon-btn icon-btn-danger"
                      onClick={() => handleDelete(row.id)}
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

        {pageData.totalPages > 1 && (
          <div className="pagination">
            <span className="pagination-info">
              {t("transactions:paginationInfo", {
                total: pageData.totalElements,
                page: pageData.page + 1,
                totalPages: pageData.totalPages,
              })}
            </span>
            <div className="pagination-controls">
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={pageData.page === 0}
              >
                {t("transactions:prevPage")}
              </button>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setPage((p) => Math.min(pageData.totalPages - 1, p + 1))}
                disabled={pageData.page >= pageData.totalPages - 1}
              >
                {t("transactions:nextPage")}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
