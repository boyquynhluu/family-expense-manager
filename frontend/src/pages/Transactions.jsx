import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import AmountInput from "../components/AmountInput";
import {
  CloseIcon,
  EditIcon,
  FileTextIcon,
  FileSpreadsheetIcon,
  HistoryIcon,
  ImageIcon,
  TrashIcon,
  UploadIcon,
} from "../components/AppIcons";
import Pagination from "../components/Pagination";
import SeedDefaultsButton from "../components/SeedDefaultsButton";
import TransactionHistoryModal from "../components/TransactionHistoryModal";
import { useAuth } from "../hooks/useAuth";
import { PAGE_SIZE } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { maxDateTime, minDateTime } from "../utils/dateLimits";
import { formatCurrency } from "../utils/format";
import { LIMITS } from "../utils/inputLimits";
import { notifyTrashChanged } from "../utils/trashEvents";

import { Table, THead, TBody, Th, Td } from "../components/ui/Table";
import { Button, IconButton } from "../components/ui/Button";
import { Checkbox, Input, Select } from "../components/ui/Input";
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
  q: "",
  minAmount: "",
  maxAmount: "",
};

// <input type="datetime-local"> wants "YYYY-MM-DDTHH:mm" in the browser's local time.
function nowForDateTimeInput() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

function CopyIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <rect x="9" y="9" width="11" height="11" rx="2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M5 15V6a2 2 0 0 1 2-2h9" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

const emptyPage = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

export default function Transactions() {
  const { t } = useTranslation(["common", "transactions"]);
  const { role, userId } = useAuth();
  const [pageData, setPageData] = useState(emptyPage);
  const [wallets, setWallets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [historyTransactionId, setHistoryTransactionId] = useState(null);
  const [members, setMembers] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [filter, setFilter] = useState(emptyFilter);
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");
  const [receiptFile, setReceiptFile] = useState(null);
  const [receiptInputKey, setReceiptInputKey] = useState(0);
  const fileInputRef = useRef(null);
  const [uploadTargetId, setUploadTargetId] = useState(null);
  const importInputRef = useRef(null);
  const [importing, setImporting] = useState(false);
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [bulkDeleting, setBulkDeleting] = useState(false);
  const formCardRef = useRef(null);

  // Filtering/paging happens on the backend now (see README "1. Phân trang/lọc chỉ làm
  // ở frontend") — the client only ever holds the current page's rows.
  function load() {
    const params = { page, size: PAGE_SIZE };
    if (filter.walletId) params.walletId = filter.walletId;
    if (filter.categoryId) params.categoryId = filter.categoryId;
    if (filter.type) params.type = filter.type;
    if (filter.fromDate) params.fromDate = filter.fromDate;
    if (filter.toDate) params.toDate = filter.toDate;
    if (filter.q.trim()) params.q = filter.q.trim();
    if (filter.minAmount !== "") params.minAmount = filter.minAmount;
    if (filter.maxAmount !== "") params.maxAmount = filter.maxAmount;

    client
      .get("/expenses/transactions", { params })
      .then((res) => {
        const data = res.data.data;
        // Deleting the last row on a page beyond the first leaves it empty — step back
        // one page rather than showing a stranded "no results" screen.
        if (data.content.length === 0 && data.page > 0 && data.totalElements > 0) {
          setPage(data.page - 1);
        } else {
          setPageData(data);
          setSelectedIds((prev) => {
            const visible = new Set(data.content.map((r) => r.id));
            return new Set([...prev].filter((id) => visible.has(id)));
          });
        }
      })
      .catch((err) => toast.error(err.response?.data?.message || t("transactions:loadFailed")));
  }

  useEffect(() => setSelectedIds(new Set()), [filter, page]);
  useEffect(load, [filter, page, t]);

  function loadWalletsAndCategories() {
    client.get("/expenses/wallets").then((res) => {
      setWallets(res.data.data);
      setForm((f) => ({ ...f, walletId: f.walletId || String(res.data.data[0]?.id ?? "") }));
    });
    client.get("/expenses/categories").then((res) => {
      setCategories(res.data.data);
      setForm((f) => ({ ...f, categoryId: f.categoryId || String(res.data.data[0]?.id ?? "") }));
    });
  }

  useEffect(() => {
    loadWalletsAndCategories();
    client
      .get("/auth/family/members", { params: { page: 0, size: 100 } })
      .then((res) => setMembers(res.data.data.content))
      .catch(() => {});
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
    setReceiptFile(null);
    setReceiptInputKey((k) => k + 1);
    setForm((f) => ({ ...emptyForm, walletId: f.walletId, categoryId: f.categoryId }));
  }

  function startDuplicate(transaction) {
    setEditingId(null);
    setReceiptFile(null);
    setReceiptInputKey((k) => k + 1);
    setForm({
      walletId: String(transaction.walletId),
      categoryId: String(transaction.categoryId),
      type: transaction.type,
      amount: String(transaction.amount),
      occurredAt: nowForDateTimeInput(),
      note: transaction.note ?? "",
    });
    formCardRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function toggleSelected(id) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleSelectAll() {
    const selectableIds = pageData.content.filter(canModify).map((r) => r.id);
    const allSelected = selectableIds.length > 0 && selectableIds.every((id) => selectedIds.has(id));
    setSelectedIds(allSelected ? new Set() : new Set(selectableIds));
  }

  async function handleBulkDelete() {
    if (selectedIds.size === 0) return;
    if (!(await confirmDialog(t("transactions:bulkDeleteConfirm", { count: selectedIds.size })))) return;
    setError("");
    setBulkDeleting(true);
    try {
      const res = await client.post("/expenses/transactions/bulk-delete", { ids: [...selectedIds] });
      const { deleted, skipped, forbidden } = res.data.data;
      const message = t("transactions:bulkDeleteResult", { deleted, skipped, forbidden });
      if (deleted > 0) {
        notifyTrashChanged();
        toast.success(message);
      } else {
        toast.error(message);
      }
      setSelectedIds(new Set());
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("transactions:deleteFailed"));
    } finally {
      setBulkDeleting(false);
    }
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
    let savedId;
    try {
      if (editingId) {
        await client.put(`/expenses/transactions/${editingId}`, payload);
        savedId = editingId;
      } else {
        const res = await client.post("/expenses/transactions", payload);
        savedId = res.data.data.id;
      }
    } catch (err) {
      setError(err.response?.data?.message || t("transactions:saveFailed"));
      return;
    }

    if (receiptFile) {
      const formData = new FormData();
      formData.append("file", receiptFile);
      try {
        await client.post(`/expenses/transactions/${savedId}/receipt`, formData, {
          headers: { "Content-Type": "multipart/form-data" },
        });
      } catch (err) {
        setError(err.response?.data?.message || t("transactions:uploadReceiptFailed"));
      }
    }
    cancelEdit();
    load();
  }

  async function handleDelete(id) {
    if (!(await confirmDialog(t("transactions:deleteConfirm")))) return;
    setError("");
    try {
      await client.delete(`/expenses/transactions/${id}`);
      notifyTrashChanged();
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
    if (!(await confirmDialog(t("transactions:deleteReceiptConfirm")))) return;
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

  function memberName(row) {
    return members.find((m) => m.id === row.userId)?.displayName ?? row.createdByName ?? t("transactions:formerMember");
  }

  function canModify(row) {
    return role === "OWNER" || String(row.userId) === String(userId);
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
  const selectableRows = pageData.content.filter(canModify);
  const allSelectableSelected = selectableRows.length > 0 && selectableRows.every((r) => selectedIds.has(r.id));

  async function exportReport(format) {
    setError("");
    const params = { format };
    if (filter.walletId) params.walletId = filter.walletId;
    if (filter.categoryId) params.categoryId = filter.categoryId;
    if (filter.type) params.type = filter.type;
    if (filter.fromDate) params.fromDate = filter.fromDate;
    if (filter.toDate) params.toDate = filter.toDate;
    if (filter.q.trim()) params.q = filter.q.trim();
    if (filter.minAmount !== "") params.minAmount = filter.minAmount;
    if (filter.maxAmount !== "") params.maxAmount = filter.maxAmount;

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

      <div className="section-card" ref={formCardRef}>
        <h2>{editingId ? t("transactions:editFormTitle") : t("transactions:addFormTitle")}</h2>
        {wallets.length === 0 || categories.length === 0 ? (
          <>
            <p className="empty-state">
              {wallets.length === 0 && categories.length === 0
                ? t("transactions:needWalletAndCategory")
                : wallets.length === 0
                  ? t("transactions:needWallet")
                  : t("transactions:needCategory")}
            </p>
            <SeedDefaultsButton onDone={loadWalletsAndCategories} />
          </>
        ) : (
          <form className="inline-form" onSubmit={handleSubmit}>
            <label className="field">
              <span>
                {t("transactions:walletLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Select value={form.walletId} onChange={(e) => updateField("walletId", e.target.value)} required>
                {wallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </Select>
            </label>
            <label className="field">
              <span>
                {t("transactions:categoryLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Select value={form.categoryId} onChange={(e) => updateField("categoryId", e.target.value)} required>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </Select>
            </label>
            <label className="field">
              {t("transactions:typeLabel")}
              <Select value={form.type} onChange={(e) => updateField("type", e.target.value)}>
                <option value="EXPENSE">{t("transactions:typeExpense")}</option>
                <option value="INCOME">{t("transactions:typeIncome")}</option>
              </Select>
            </label>
            <label className="field">
              <span>
                {t("transactions:amountLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <AmountInput placeholder="0" value={form.amount} onChange={(v) => updateField("amount", v)} required />
            </label>
            <label className="field">
              <span>
                {t("transactions:timeLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Input
                type="datetime-local"
                value={form.occurredAt}
                onChange={(e) => updateField("occurredAt", e.target.value)}
                min={minDateTime()}
                max={maxDateTime()}
                required
              />
            </label>
            <label className="field">
              {t("transactions:noteLabel")}
              <Input
                placeholder={t("transactions:notePlaceholder")}
                value={form.note}
                maxLength={LIMITS.transactionNote}
                onChange={(e) => updateField("note", e.target.value)}
              />
            </label>
            <div className="field">
              {t("transactions:receiptLabel")}
              <div className={`file-picker${receiptFile ? " has-file" : ""}`}>
                <label className="file-picker-trigger">
                  <input
                    key={receiptInputKey}
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    onChange={(e) => setReceiptFile(e.target.files?.[0] ?? null)}
                  />
                  <ImageIcon />
                  <span className="file-picker-name">
                    {receiptFile ? receiptFile.name : t("transactions:chooseReceipt")}
                  </span>
                </label>
                {receiptFile && (
                  <IconButton variant="ghost-danger" size="sm" className="mr-1"
                    onClick={() => {
                      setReceiptFile(null);
                      setReceiptInputKey((k) => k + 1);
                    }}
                    aria-label={t("transactions:clearReceipt")}
                    title={t("transactions:clearReceipt")}
                  >
                    <CloseIcon />
                  </IconButton>
                )}
              </div>
            </div>
            <Button type="submit">{editingId ? t("transactions:submitUpdate") : t("transactions:submitAdd")}</Button>
            {editingId && (
              <Button variant="secondary" onClick={cancelEdit}>
                {t("common:cancel")}
              </Button>
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
            <Button variant="import" onClick={triggerImport} disabled={importing}>
              <UploadIcon />
              {importing ? t("transactions:importing") : t("transactions:importButton")}
            </Button>
            {pageData.totalElements > 0 && (
              <>
                <Button variant="csv" onClick={() => exportReport("CSV")}>
                  <FileTextIcon />
                  {t("transactions:exportCsv")}
                </Button>
                <Button variant="excel" onClick={() => exportReport("EXCEL")}>
                  <FileSpreadsheetIcon />
                  {t("transactions:exportExcel")}
                </Button>
              </>
            )}
          </div>
        </div>
        <p className="page-header-subtitle" style={{ marginBottom: "1rem" }}>
          {t("transactions:importHint")}
        </p>

        <form className="inline-form filter-bar" onSubmit={(e) => e.preventDefault()}>
          <label className="field">
            {t("transactions:walletLabel")}
            <Select value={filter.walletId} onChange={(e) => updateFilter("walletId", e.target.value)}>
              <option value="">{t("transactions:allOption")}</option>
              {wallets.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </Select>
          </label>
          <label className="field">
            {t("transactions:categoryLabel")}
            <Select value={filter.categoryId} onChange={(e) => updateFilter("categoryId", e.target.value)}>
              <option value="">{t("transactions:allOption")}</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </label>
          <label className="field">
            {t("transactions:typeLabel")}
            <Select value={filter.type} onChange={(e) => updateFilter("type", e.target.value)}>
              <option value="">{t("transactions:allOption")}</option>
              <option value="EXPENSE">{t("transactions:typeExpense")}</option>
              <option value="INCOME">{t("transactions:typeIncome")}</option>
            </Select>
          </label>
          <label className="field">
            {t("transactions:searchLabel")}
            <Input
              type="search"
              placeholder={t("transactions:searchPlaceholder")}
              value={filter.q}
              maxLength={LIMITS.search}
              onChange={(e) => updateFilter("q", e.target.value)}
            />
          </label>
          <label className="field">
            {t("transactions:minAmountLabel")}
            <AmountInput placeholder="0" value={filter.minAmount} onChange={(v) => updateFilter("minAmount", v)} />
          </label>
          <label className="field">
            {t("transactions:maxAmountLabel")}
            <AmountInput placeholder="0" value={filter.maxAmount} onChange={(v) => updateFilter("maxAmount", v)} />
          </label>
          <label className="field">
            {t("transactions:fromDateLabel")}
            <Input type="date" value={filter.fromDate} onChange={(e) => updateFilter("fromDate", e.target.value)} />
          </label>
          <label className="field">
            {t("transactions:toDateLabel")}
            <Input type="date" value={filter.toDate} onChange={(e) => updateFilter("toDate", e.target.value)} />
          </label>
          {hasActiveFilter && (
            <Button variant="secondary" onClick={clearFilter}>
              {t("transactions:clearFilter")}
            </Button>
          )}
        </form>

        {selectedIds.size > 0 && (
          <div className="row-actions" style={{ marginBottom: "0.75rem" }}>
            <span className="page-header-subtitle">
              {t("transactions:selectedCount", { count: selectedIds.size })}
            </span>
            <Button variant="danger"
              onClick={handleBulkDelete}
              disabled={bulkDeleting}
            >
              {t("transactions:bulkDeleteButton")}
            </Button>
            <Button variant="ghost" onClick={() => setSelectedIds(new Set())}>
              {t("transactions:clearSelection")}
            </Button>
          </div>
        )}

        {pageData.content.length === 0 ? (
          <p className="empty-state">
            {hasActiveFilter ? t("transactions:noMatchFilter") : t("transactions:emptyState")}
          </p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>
                  <Checkbox
                    checked={allSelectableSelected}
                    disabled={selectableRows.length === 0}
                    onChange={toggleSelectAll}
                    aria-label={t("transactions:selectAllAria")}
                  />
                </Th>
                <Th>{t("transactions:timeLabel")}</Th>
                <Th>{t("transactions:walletLabel")}</Th>
                <Th>{t("transactions:categoryLabel")}</Th>
                <Th>{t("transactions:typeLabel")}</Th>
                <Th align="right">{t("transactions:amountLabel")}</Th>
                <Th>{t("transactions:noteLabel")}</Th>
                <Th>{t("transactions:creatorLabel")}</Th>
                <Th></Th>
              </tr>
            </THead>
            <TBody>
              {pageData.content.map((row) => (
                <tr key={row.id}>
                  <Td>
                    <Checkbox
                      checked={selectedIds.has(row.id)}
                      disabled={!canModify(row)}
                      onChange={() => toggleSelected(row.id)}
                      aria-label={t("transactions:selectRowAria")}
                    />
                  </Td>
                  <Td data-label={t("transactions:timeLabel")}>
                    {/* Break only between date and time — never inside "2026-09-22" at a hyphen. */}
                    <span className="whitespace-nowrap">{row.occurredAt.slice(0, 10)}</span>{" "}
                    <span className="whitespace-nowrap">{row.occurredAt.slice(11)}</span>
                  </Td>
                  <Td data-label={t("transactions:walletLabel")}>{walletName(row.walletId)}</Td>
                  <Td data-label={t("transactions:categoryLabel")}>{categoryName(row.categoryId)}</Td>
                  <Td data-label={t("transactions:typeLabel")}>
                    <span className={`badge ${row.type === "EXPENSE" ? "badge-expense" : "badge-income"}`}>
                      {row.type === "EXPENSE" ? t("transactions:typeExpense") : t("transactions:typeIncome")}
                    </span>
                  </Td>
                  <Td
                    data-label={t("transactions:amountLabel")}
                    align="right"
                    className={row.type === "EXPENSE" ? "amount-expense" : "amount-income"}
                  >
                    {row.type === "EXPENSE" ? "-" : "+"}
                    {formatCurrency(row.amount)}
                  </Td>
                  <Td data-label={t("transactions:noteLabel")}>{row.note}</Td>
                  <Td data-label={t("transactions:creatorLabel")}>{memberName(row)}</Td>
                  <Td actions>
                    {row.hasReceipt && (
                      <IconButton
                        onClick={() => viewReceipt(row.id)}
                        aria-label={t("transactions:viewReceiptAria")}
                        title={t("transactions:viewReceiptAria")}
                      >
                        <ImageIcon />
                      </IconButton>
                    )}
                    <IconButton
                      onClick={() => startDuplicate(row)}
                      aria-label={t("transactions:duplicateAria")}
                      title={t("transactions:duplicateAria")}
                    >
                      <CopyIcon />
                    </IconButton>
                    <IconButton
                      onClick={() => setHistoryTransactionId(row.id)}
                      aria-label={t("transactions:historyAria")}
                      title={t("transactions:historyAria")}
                    >
                      <HistoryIcon />
                    </IconButton>
                    {canModify(row) && (
                      <>
                        {row.hasReceipt ? (
                          <IconButton variant="danger"
                            onClick={() => handleDeleteReceipt(row.id)}
                            aria-label={t("transactions:deleteReceiptAria")}
                          >
                            <CloseIcon />
                          </IconButton>
                        ) : (
                          <IconButton
                            onClick={() => triggerUpload(row.id)}
                            aria-label={t("transactions:attachReceiptAria")}
                            title={t("transactions:attachReceiptAria")}
                          >
                            <ImageIcon />
                          </IconButton>
                        )}
                        <IconButton
                          onClick={() => startEdit(row)}
                          aria-label={t("common:edit")}
                        >
                          <EditIcon />
                        </IconButton>
                        <IconButton variant="danger"
                          onClick={() => handleDelete(row.id)}
                          aria-label={t("common:delete")}
                        >
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

      <TransactionHistoryModal
        transactionId={historyTransactionId}
        onClose={() => setHistoryTransactionId(null)}
        walletName={walletName}
        categoryName={categoryName}
      />
    </div>
  );
}
