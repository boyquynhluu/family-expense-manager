import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import AmountInput from "../components/AmountInput";
import {
    CloseIcon,
    EditIcon,
    FileSpreadsheetIcon,
    FileTextIcon,
    HistoryIcon,
    ImageIcon,
    LockIcon,
    TrashIcon,
    UploadIcon,
} from "../components/AppIcons";
import Pagination from "../components/Pagination";
import RefundModal from "../components/RefundModal";
import SeedDefaultsButton from "../components/SeedDefaultsButton";
import TransactionApprovals from "../components/TransactionApprovals";
import TransactionHistoryModal from "../components/TransactionHistoryModal";
import { useAuth } from "../hooks/useAuth";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { useIdempotencyKey } from "../hooks/useIdempotencyKey";
import { PAGE_SIZE } from "../hooks/usePagedList";
import { usePeriodLocks } from "../hooks/usePeriodLocks";
import { confirmDialog } from "../utils/confirm";
import { maxDateTime, minDateTime } from "../utils/dateLimits";
import { formatCurrency } from "../utils/format";
import { LIMITS } from "../utils/inputLimits";
import { notifyTrashChanged } from "../utils/trashEvents";
import { canUseWallet, usableWallets } from "../utils/walletAccess";

import { Button, IconButton } from "../components/ui/Button";
import { Field } from "../components/ui/Field";
import { Checkbox, Input, Select } from "../components/ui/Input";
import { Table, TBody, Td, Th, THead } from "../components/ui/Table";
import { useCleanText } from "../utils/textQuality";
// Shown in place of every detail of another member's private transaction.
const MASK = "***";

// How long a just-saved row stays highlighted after the list jumps to it.
const HIGHLIGHT_MS = 3500;

// The filters as query params — shared by the list, the export and "locate".
function filterParams(filter) {
  const params = {};
  if (filter.walletId) params.walletId = filter.walletId;
  if (filter.categoryId) params.categoryId = filter.categoryId;
  if (filter.type) params.type = filter.type;
  if (filter.fromDate) params.fromDate = filter.fromDate;
  if (filter.toDate) params.toDate = filter.toDate;
  if (filter.q.trim()) params.q = filter.q.trim();
  if (filter.minAmount !== "") params.minAmount = filter.minAmount;
  if (filter.maxAmount !== "") params.maxAmount = filter.maxAmount;
  if (filter.tagId) params.tagId = filter.tagId;
  return params;
}

const emptyForm = {
  walletId: "",
  categoryId: "",
  type: "EXPENSE",
  amount: "",
  occurredAt: "",
  note: "",
  isPrivate: false,
  // README C6: comma-separated tag names.
  tags: "",
  // README C5: [] = not split; otherwise [{ categoryId, amount }] adding up to the amount.
  splits: [],
};

function tagsOf(text) {
  return text
    .split(",")
    .map((x) => x.trim())
    .filter(Boolean);
}

const emptyFilter = {
  walletId: "",
  categoryId: "",
  type: "",
  fromDate: "",
  toDate: "",
  q: "",
  minAmount: "",
  maxAmount: "",
  tagId: "",
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

// Must match BulkDeleteRequest's @Size(max = 100) on the backend.
const BULK_DELETE_MAX = 100;

const emptyPage = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

export default function Transactions() {
  const { t } = useTranslation(["common", "transactions"]);
  const cleanText = useCleanText();
  const { role, userId } = useAuth();
  const { isLocked } = usePeriodLocks();
  // Retrying a create whose response got lost must not record the transaction twice (see useIdempotencyKey).
  const createKey = useIdempotencyKey();
  const [pageData, setPageData] = useState(emptyPage);
  const [wallets, setWallets] = useState([]);
  // Wallet of the entry being edited — kept selectable even if it has since become another
  // member's private wallet (the backend only re-checks ownership when the wallet CHANGES).
  const [editingWalletId, setEditingWalletId] = useState(null);
  // Creator of the entry being edited: only they may change its private flag.
  const [editingCreatorId, setEditingCreatorId] = useState(null);
  const [categories, setCategories] = useState([]);
  const [historyTransactionId, setHistoryTransactionId] = useState(null);
  const [tagOptions, setTagOptions] = useState([]);
  const [refundTarget, setRefundTarget] = useState(null);
  const [approvalsKey, setApprovalsKey] = useState(0);
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
  // Row just added/edited: scrolled to and highlighted once its page has loaded, then faded out.
  const [highlightId, setHighlightId] = useState(null);
  const highlightedRowRef = useRef(null);

  useEffect(() => {
    if (highlightId == null) return undefined;
    const row = pageData.content.find((r) => r.id === highlightId);
    if (!row) return undefined; // its page hasn't arrived yet
    highlightedRowRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    const timer = setTimeout(() => setHighlightId(null), HIGHLIGHT_MS);
    return () => clearTimeout(timer);
  }, [highlightId, pageData]);

  // The search box only hits the backend once typing pauses (it used to send a request per keystroke);
  // clearing it applies at once. Every other filter field applies immediately.
  const debouncedQ = useDebouncedValue(filter.q);
  const appliedFilter = { ...filter, q: filter.q === "" ? "" : debouncedQ };
  const appliedFilterKey = JSON.stringify(appliedFilter);
  // Bumped on every load() — a response is only applied if no newer load() started since, so a slow
  // older request (e.g. for "an") can never overwrite the results of a newer one (e.g. "an uong").
  const loadSeq = useRef(0);

  // Filtering/paging happens on the backend now (see README "1. Phân trang/lọc chỉ làm
  // ở frontend") — the client only ever holds the current page's rows.
  function load() {
    const params = { ...filterParams(appliedFilter), page, size: PAGE_SIZE };
    const seq = ++loadSeq.current;

    client
      .get("/expenses/transactions", { params })
      .then((res) => {
        if (seq !== loadSeq.current) return;
        const data = res.data.data;
        // Deleting the last row on a page beyond the first leaves it empty — step back
        // one page rather than showing a stranded "no results" screen.
        if (data.content.length === 0 && data.page > 0 && data.totalElements > 0) {
          setPage(data.page - 1);
        } else {
          // Selection is NOT pruned to the visible rows: ticking rows on page 1, going to
          // page 2 and back must keep page 1's ticks (selection spans pages).
          setPageData(data);
        }
      })
      .catch((err) => {
        if (seq !== loadSeq.current) return;
        toast.error(err.response?.data?.message || t("transactions:loadFailed"));
      });
  }

  // Paging keeps the selection; a new filter clears it, since it can hide rows that are
  // still selected and a bulk delete would then remove rows the user can no longer see.
  useEffect(() => setSelectedIds(new Set()), [filter]);
  // eslint-disable-next-line react-hooks/exhaustive-deps -- appliedFilterKey stands in for appliedFilter
  useEffect(load, [appliedFilterKey, page, t]);

  function loadTags() {
    client.get("/expenses/tags").then((res) => setTagOptions(res.data.data)).catch(() => {});
  }

  function loadWalletsAndCategories() {
    loadTags();
    const showError = (err) => toast.error(err.response?.data?.message || t("transactions:loadFailed"));
    client.get("/expenses/wallets").then((res) => setWallets(res.data.data)).catch(showError);
    client
      .get("/expenses/categories")
      .then((res) => setCategories(res.data.data))
      .catch(showError);
  }

  useEffect(() => {
    loadWalletsAndCategories();
    client
      .get("/auth/family/members", { params: { page: 0, size: 100 } })
      .then((res) => setMembers(res.data.data.content))
      .catch(() => {});
    // Load once on mount; loadWalletsAndCategories is also re-run by hand after seeding defaults.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Wallets this member may record into (their own + shared; any for the OWNER) — the add/edit
  // form only offers these. Lists, filters and names still use every wallet.
  const formWallets = usableWallets(wallets, { role, userId }, editingId ? editingWalletId : null);
  // An OWNER editing another member's entry: the private toggle is theirs, not ours, to change.
  const editingOthers = editingId != null && String(editingCreatorId) !== String(userId);

  // Default the form to the first wallet the member may use, once wallets have loaded.
  const firstFormWalletId = formWallets[0]?.id;
  useEffect(() => {
    if (firstFormWalletId != null) {
      setForm((f) => (f.walletId ? f : { ...f, walletId: String(firstFormWalletId) }));
    }
  }, [firstFormWalletId]);

  // The category pickers only list categories of the chosen type. Keep the form's selection one of them —
  // after switching type, loading categories or cancelling an edit — or the <select> shows its first option
  // while the state still holds a category of the OTHER type, which the backend then rejects ("Danh mục … thuộc
  // loại EXPENSE, không khớp loại INCOME"). Split parts with such a category go back to "choose a category".
  const formCategories = categories.filter((c) => c.type === form.type);
  const formCategoryIdsKey = formCategories.map((c) => c.id).join(",");
  useEffect(() => {
    const validIds = formCategoryIdsKey ? formCategoryIdsKey.split(",") : [];
    setForm((f) => {
      const categoryId = validIds.includes(f.categoryId) ? f.categoryId : (validIds[0] ?? "");
      const splitsValid = f.splits.every((p) => p.categoryId === "" || validIds.includes(p.categoryId));
      if (categoryId === f.categoryId && splitsValid) return f;
      return {
        ...f,
        categoryId,
        splits: splitsValid
          ? f.splits
          : f.splits.map((p) => (p.categoryId === "" || validIds.includes(p.categoryId) ? p : { ...p, categoryId: "" })),
      };
    });
  }, [formCategoryIdsKey]);

  function updateField(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function toCents(value) {
    return Math.round(Number(value || 0) * 100);
  }

  function splitTotal() {
    return form.splits.reduce((sum, p) => sum + toCents(p.amount), 0);
  }

  // README C5: start splitting with two parts — the current category with the whole amount, and an empty one.
  function toggleSplits(enabled) {
    setForm((f) => ({
      ...f,
      splits: enabled ? [{ categoryId: f.categoryId, amount: f.amount }, { categoryId: "", amount: "" }] : [],
    }));
  }

  function updateSplit(index, field, value) {
    setForm((f) => ({ ...f, splits: f.splits.map((p, i) => (i === index ? { ...p, [field]: value } : p)) }));
  }

  function startEdit(transaction) {
    setEditingId(transaction.id);
    setEditingWalletId(transaction.walletId);
    setEditingCreatorId(transaction.userId);
    setForm({
      walletId: String(transaction.walletId),
      categoryId: String(transaction.categoryId),
      type: transaction.type,
      amount: String(transaction.amount),
      occurredAt: transaction.occurredAt.slice(0, 16),
      note: transaction.note ?? "",
      isPrivate: Boolean(transaction.isPrivate),
      tags: (transaction.tags ?? []).join(", "),
      splits: (transaction.splits ?? []).map((p) => ({ categoryId: String(p.categoryId), amount: String(p.amount) })),
    });
  }

  function cancelEdit() {
    setEditingId(null);
    setEditingWalletId(null);
    setEditingCreatorId(null);
    setReceiptFile(null);
    setReceiptInputKey((k) => k + 1);
    setForm((f) => ({ ...emptyForm, walletId: f.walletId, categoryId: f.categoryId }));
  }

  function startDuplicate(transaction) {
    setEditingId(null);
    setEditingWalletId(null);
    setEditingCreatorId(null);
    setReceiptFile(null);
    setReceiptInputKey((k) => k + 1);
    // Copying someone else's entry made in THEIR private wallet: switch to one we may use.
    const sourceWallet = wallets.find((w) => w.id === transaction.walletId);
    const walletId = canUseWallet(sourceWallet, { role, userId })
      ? transaction.walletId
      : usableWallets(wallets, { role, userId })[0]?.id ?? "";
    setForm({
      walletId: String(walletId),
      categoryId: String(transaction.categoryId),
      type: transaction.type,
      amount: String(transaction.amount),
      occurredAt: nowForDateTimeInput(),
      note: transaction.note ?? "",
      // A private entry we see is necessarily our own — keep the copy private too.
      isPrivate: Boolean(transaction.isPrivate),
      tags: (transaction.tags ?? []).join(", "),
      splits: (transaction.splits ?? []).map((p) => ({ categoryId: String(p.categoryId), amount: String(p.amount) })),
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

  // The header checkbox only (un)ticks the CURRENT page's rows — selections made on
  // other pages are left untouched.
  function toggleSelectAll() {
    const selectableIds = pageData.content.filter(canChange).map((r) => r.id);
    const allSelected = selectableIds.length > 0 && selectableIds.every((id) => selectedIds.has(id));
    setSelectedIds((prev) => {
      const next = new Set(prev);
      for (const id of selectableIds) {
        if (allSelected) next.delete(id);
        else next.add(id);
      }
      return next;
    });
  }

  async function handleBulkDelete() {
    if (selectedIds.size === 0) return;
    if (!(await confirmDialog(t("transactions:bulkDeleteConfirm", { count: selectedIds.size })))) return;
    setError("");
    setBulkDeleting(true);
    try {
      // The endpoint takes at most BULK_DELETE_MAX ids per call (BulkDeleteRequest @Size),
      // and a selection spanning several pages can exceed that — send it in chunks.
      const ids = [...selectedIds];
      let deleted = 0;
      let skipped = 0;
      let forbidden = 0;
      let locked = 0;
      for (let i = 0; i < ids.length; i += BULK_DELETE_MAX) {
        const res = await client.post("/expenses/transactions/bulk-delete", { ids: ids.slice(i, i + BULK_DELETE_MAX) });
        deleted += res.data.data.deleted;
        skipped += res.data.data.skipped;
        forbidden += res.data.data.forbidden;
        locked += res.data.data.locked ?? 0;
      }
      const message =
        t("transactions:bulkDeleteResult", { deleted, skipped, forbidden }) +
        (locked > 0 ? ` ${t("transactions:bulkDeleteLocked", { locked })}` : "");
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
    const confirmKey = editingId ? "transactions:updateConfirm" : "transactions:addConfirm";
    if (!(await confirmDialog(t(confirmKey, { amount: formatCurrency(Number(form.amount)) }), { tone: "primary", icon: "question" }))) return;
    setError("");
    const payload = {
      walletId: Number(form.walletId),
      categoryId: Number(form.categoryId),
      type: form.type,
      amount: Number(form.amount),
      occurredAt: form.occurredAt,
      note: form.note || null,
      isPrivate: form.isPrivate,
      tags: tagsOf(form.tags),
      splits: form.splits.length > 0
        ? form.splits.map((p) => ({ categoryId: Number(p.categoryId), amount: Number(p.amount) }))
        : null,
    };
    if (form.splits.length > 0 && splitTotal() !== toCents(form.amount)) {
      setError(t("transactions:splitMismatch"));
      return;
    }
    let savedId;
    try {
      if (editingId) {
        await client.put(`/expenses/transactions/${editingId}`, payload);
        savedId = editingId;
      } else {
        const res = await client.post("/expenses/transactions", payload, {
          headers: { "Idempotency-Key": createKey.keyFor(payload) },
        });
        createKey.reset();
        if (res.status === 202) {
          // README A5: above the approval threshold — nothing is recorded until the OWNER approves.
          toast(t("transactions:sentForApproval"), { icon: "⏳" });
          if (receiptFile) toast(t("transactions:receiptAfterApproval"), { icon: "ℹ️" });
          cancelEdit();
          setApprovalsKey((k) => k + 1);
          return;
        }
        savedId = res.data.data.id;
      }
      loadTags();
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
    showSaved(savedId);
  }

  // The list is sorted by transaction date, so a back-dated entry can land on a later page: jump to the
  // page that holds it and briefly highlight it (or say it's outside the current filters).
  async function showSaved(id) {
    try {
      const res = await client.get(`/expenses/transactions/${id}/location`, {
        params: { ...filterParams(appliedFilter), size: PAGE_SIZE },
      });
      const { inList, page: target } = res.data.data;
      if (!inList) {
        toast(t("transactions:savedOutsideFilter"), { icon: "ℹ️" });
        load();
        return;
      }
      setHighlightId(id);
      if (target === page) load();
      else setPage(target); // the [filter, page] effect reloads
    } catch {
      load(); // locating is a nicety — never block showing the list
    }
  }

  async function handleDelete(id) {
    if (!(await confirmDialog(t("transactions:deleteConfirm")))) return;
    setError("");
    try {
      await client.delete(`/expenses/transactions/${id}`);
      notifyTrashChanged();
      toast.success(t("transactions:deleteSuccess"));
      setSelectedIds((prev) => {
        if (!prev.has(id)) return prev;
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
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
    const replacing = pageData.content.some((row) => row.id === uploadTargetId && row.hasReceipt);
    const confirmKey = replacing ? "transactions:replaceReceiptConfirm" : "transactions:uploadReceiptConfirm";
    if (!(await confirmDialog(t(confirmKey, { file: file.name }), { tone: "primary", icon: "question" }))) return;
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
      toast.success(t("transactions:deleteSuccess"));
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

  // Another member's private transaction (listed masked, without details): nobody but its creator may touch it.
  function isMasked(row) {
    return row.isPrivate && String(row.userId) !== String(userId);
  }

  function canModify(row) {
    return !isMasked(row) && (role === "OWNER" || String(row.userId) === String(userId));
  }

  // README C4: an expense (not itself a refund) the caller may modify, in an open month.
  function canRefund(row) {
    return row.type === "EXPENSE" && Number(row.amount) > 0 && canChange(row);
  }

  // Edit/delete/select also need the row's month to be open (README B2 "chốt sổ"); a receipt doesn't change
  // any number, so it stays attachable in a closed month.
  function canChange(row) {
    return canModify(row) && !isLocked(row.occurredAt);
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
  const selectableRows = pageData.content.filter(canChange);
  const allSelectableSelected = selectableRows.length > 0 && selectableRows.every((r) => selectedIds.has(r.id));
  const selectedOnPage = pageData.content.filter((r) => selectedIds.has(r.id)).length;
  // Selected rows the user can't see right now — surfaced in the bulk bar so "Xoá đã chọn"
  // never silently deletes rows from other pages.
  const selectedOffPage = selectedIds.size - selectedOnPage;

  async function exportReport(format) {
    setError("");
    const params = { ...filterParams(filter), format };

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
    if (!(await confirmDialog(t("transactions:importConfirm", { file: file.name }), { tone: "primary", icon: "question" }))) return;
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
        ) : formWallets.length === 0 ? (
          <p className="empty-state">{t("transactions:noUsableWallet")}</p>
        ) : (
          <form className="inline-form" onSubmit={handleSubmit}>
            <Field>
              <span>
                {t("transactions:walletLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Select value={form.walletId} onChange={(e) => updateField("walletId", e.target.value)} required>
                {formWallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </Select>
            </Field>
            {form.splits.length === 0 && (
              <Field>
                <span>
                  {t("transactions:categoryLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <Select value={form.categoryId} onChange={(e) => updateField("categoryId", e.target.value)} required>
                  {formCategories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.parentId ? `— ${c.name}` : c.name}
                    </option>
                  ))}
                </Select>
              </Field>
            )}
            <Field>
              {t("transactions:typeLabel")}
              <Select value={form.type} onChange={(e) => updateField("type", e.target.value)}>
                <option value="EXPENSE">{t("transactions:typeExpense")}</option>
                <option value="INCOME">{t("transactions:typeIncome")}</option>
              </Select>
            </Field>
            <Field>
              <span>
                {t("transactions:amountLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <AmountInput
                placeholder="0"
                value={form.amount}
                onChange={(v) => updateField("amount", v)}
                required
                positive
                min={LIMITS.minTransactionAmount}
                max={LIMITS.maxTransactionAmount}
              />
            </Field>
            <Field>
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
            </Field>
            <Field>
              {t("transactions:noteLabel")}
              <Input
                placeholder={t("transactions:notePlaceholder")}
                value={form.note} validate={cleanText}
                maxLength={LIMITS.transactionNote}
                onChange={(e) => updateField("note", e.target.value)}
              />
            </Field>
            <Field>
              {t("transactions:tagsLabel")}
              <Input
                list="transaction-tag-options"
                placeholder={t("transactions:tagsPlaceholder")}
                value={form.tags}
                maxLength={300}
                onChange={(e) => updateField("tags", e.target.value)}
              />
              <datalist id="transaction-tag-options">
                {tagOptions.map((tag) => (
                  <option key={tag.id} value={tag.name} />
                ))}
              </datalist>
            </Field>
            <div className="field">
              {t("transactions:splitLabel")}
              <label className="inline-flex h-9 items-center gap-2 text-sm">
                <Checkbox checked={form.splits.length > 0} onChange={(e) => toggleSplits(e.target.checked)} />
                {t("transactions:splitToggle")}
              </label>
            </div>
            {form.splits.length > 0 && (
              <div className="w-full space-y-2 rounded-lg border border-slate-200 p-3">
                {form.splits.map((part, index) => (
                  <div key={index} className="flex flex-wrap items-end gap-2">
                    <Select value={part.categoryId} onChange={(e) => updateSplit(index, "categoryId", e.target.value)} required>
                      <option value="">{t("transactions:splitChooseCategory")}</option>
                      {formCategories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.parentId ? `— ${c.name}` : c.name}
                        </option>
                      ))}
                    </Select>
                    <AmountInput value={part.amount} onChange={(v) => updateSplit(index, "amount", v)} required positive />
                    {form.splits.length > 2 && (
                      <IconButton variant="ghost-danger" size="sm" aria-label={t("common:delete")}
                        onClick={() => setForm((f) => ({ ...f, splits: f.splits.filter((_, i) => i !== index) }))}>
                        <CloseIcon />
                      </IconButton>
                    )}
                  </div>
                ))}
                <div className="flex flex-wrap items-center gap-3 text-sm">
                  {form.splits.length < 10 && (
                    <Button variant="secondary" size="sm"
                      onClick={() => setForm((f) => ({ ...f, splits: [...f.splits, { categoryId: "", amount: "" }] }))}>
                      {t("transactions:splitAddPart")}
                    </Button>
                  )}
                  <span className={splitTotal() === toCents(form.amount) ? "text-emerald-700" : "text-red-600"}>
                    {t("transactions:splitTotal", {
                      total: formatCurrency(splitTotal() / 100),
                      amount: formatCurrency(Number(form.amount || 0)),
                    })}
                  </span>
                </div>
              </div>
            )}
            <div className="field">
              {t("transactions:visibilityLabel")}
              {/* Only the creator decides (the backend ignores the flag when an OWNER edits someone
                  else's entry), so the toggle is locked in that case. */}
              <label
                title={t("transactions:privateHint")}
                className={`inline-flex h-9 items-center gap-2 rounded-lg px-3 text-sm font-normal shadow-sm ring-1 ring-inset transition-colors ${
                  form.isPrivate ? "bg-amber-50 text-amber-800 ring-amber-300" : "bg-white text-slate-700 ring-slate-300"
                } ${editingOthers ? "cursor-not-allowed opacity-50" : "cursor-pointer hover:ring-slate-400"}`}
              >
                <Checkbox
                  checked={form.isPrivate}
                  disabled={editingOthers}
                  onChange={(e) => updateField("isPrivate", e.target.checked)}
                />
                <LockIcon />
                {t("transactions:privateLabel")}
              </label>
            </div>
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

      <TransactionApprovals
        isOwner={role === "OWNER"}
        walletName={walletName}
        categoryName={categoryName}
        reloadKey={approvalsKey}
        onApproved={load}
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
          <Field>
            {t("transactions:walletLabel")}
            <Select value={filter.walletId} onChange={(e) => updateFilter("walletId", e.target.value)}>
              <option value="">{t("transactions:allOption")}</option>
              {wallets.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field>
            {t("transactions:categoryLabel")}
            <Select value={filter.categoryId} onChange={(e) => updateFilter("categoryId", e.target.value)}>
              <option value="">{t("transactions:allOption")}</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field>
            {t("transactions:typeLabel")}
            <Select value={filter.type} onChange={(e) => updateFilter("type", e.target.value)}>
              <option value="">{t("transactions:allOption")}</option>
              <option value="EXPENSE">{t("transactions:typeExpense")}</option>
              <option value="INCOME">{t("transactions:typeIncome")}</option>
            </Select>
          </Field>
          {tagOptions.length > 0 && (
            <Field>
              {t("transactions:tagsLabel")}
              <Select value={filter.tagId} onChange={(e) => updateFilter("tagId", e.target.value)}>
                <option value="">{t("transactions:allOption")}</option>
                {tagOptions.map((tag) => (
                  <option key={tag.id} value={tag.id}>
                    {tag.name}
                  </option>
                ))}
              </Select>
            </Field>
          )}
          <Field>
            {t("transactions:searchLabel")}
            <Input
              type="search"
              placeholder={t("transactions:searchPlaceholder")}
              value={filter.q}
              maxLength={LIMITS.search}
              onChange={(e) => updateFilter("q", e.target.value)}
            />
          </Field>
          <Field>
            {t("transactions:minAmountLabel")}
            <AmountInput placeholder="0" value={filter.minAmount} onChange={(v) => updateFilter("minAmount", v)} />
          </Field>
          <Field>
            {t("transactions:maxAmountLabel")}
            <AmountInput placeholder="0" value={filter.maxAmount} onChange={(v) => updateFilter("maxAmount", v)} />
          </Field>
          <Field>
            {t("transactions:fromDateLabel")}
            <Input type="date" value={filter.fromDate} onChange={(e) => updateFilter("fromDate", e.target.value)} />
          </Field>
          <Field>
            {t("transactions:toDateLabel")}
            <Input type="date" value={filter.toDate} onChange={(e) => updateFilter("toDate", e.target.value)} />
          </Field>
          {hasActiveFilter && (
            <Button variant="secondary" onClick={clearFilter}>
              {t("transactions:clearFilter")}
            </Button>
          )}
        </form>

        {selectedIds.size > 0 && (
          // Bulk-action bar: what's selected on the left, actions on the right (the
          // destructive one last); the two halves wrap onto separate lines on phones.
          <div
            role="region"
            aria-live="polite"
            aria-label={t("transactions:selectedCount", { count: selectedIds.size })}
            className="mb-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-2.5 rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-2.5"
          >
            <span className="flex items-center gap-2.5 text-sm font-semibold text-indigo-900">
              <span className="inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white">
                <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" className="size-3.5">
                  <path
                    fillRule="evenodd"
                    d="M16.7 5.3a1 1 0 0 1 0 1.4l-7.5 7.5a1 1 0 0 1-1.4 0L3.3 9.7a1 1 0 1 1 1.4-1.4l3.8 3.8 6.8-6.8a1 1 0 0 1 1.4 0Z"
                    clipRule="evenodd"
                  />
                </svg>
              </span>
              {t("transactions:selectedCount", { count: selectedIds.size })}
              {selectedOffPage > 0 && (
                <span className="font-normal text-indigo-700">
                  · {t("transactions:selectedOffPage", { count: selectedOffPage })}
                </span>
              )}
            </span>
            <div className="flex items-center gap-2 [&_svg]:size-4">
              <Button variant="secondary" size="sm" onClick={() => setSelectedIds(new Set())}>
                <CloseIcon />
                {t("transactions:clearSelection")}
              </Button>
              <Button variant="danger" size="sm" onClick={handleBulkDelete} disabled={bulkDeleting}>
                <TrashIcon />
                {t("transactions:bulkDeleteButton")}
              </Button>
            </div>
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
                    indeterminate={!allSelectableSelected && selectedOnPage > 0}
                    disabled={selectableRows.length === 0}
                    onChange={toggleSelectAll}
                    aria-label={t("transactions:selectAllAria")}
                  />
                </Th>
                <Th>{t("transactions:timeLabel")}</Th>
                <Th>{t("transactions:walletLabel")}</Th>
                <Th>{t("transactions:categoryLabel")}</Th>
                <Th>{t("transactions:typeLabel")}</Th>
                <Th>{t("transactions:visibilityLabel")}</Th>
                <Th align="right">{t("transactions:amountLabel")}</Th>
                <Th>{t("transactions:noteLabel")}</Th>
                <Th>{t("transactions:creatorLabel")}</Th>
                <Th></Th>
              </tr>
            </THead>
            <TBody>
              {pageData.content.map((row) => isMasked(row) ? (
                // Another member's private transaction: the API sent only who made it and when (and lists it
                // only while filtering by date at most). Nothing to act on.
                <tr key={row.id}>
                  <Td>
                    <Checkbox disabled aria-label={t("transactions:selectRowAria")} />
                  </Td>
                  <Td data-label={t("transactions:timeLabel")}>
                    <span className="whitespace-nowrap">{row.occurredAt.slice(0, 10)}</span>{" "}
                    <span className="whitespace-nowrap">{row.occurredAt.slice(11)}</span>
                  </Td>
                  <Td data-label={t("transactions:walletLabel")}>{MASK}</Td>
                  <Td data-label={t("transactions:categoryLabel")}>{MASK}</Td>
                  <Td data-label={t("transactions:typeLabel")}>{MASK}</Td>
                  <Td data-label={t("transactions:visibilityLabel")}>
                    <span
                      className="inline-flex items-center gap-1 whitespace-nowrap rounded-full bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-800 ring-1 ring-inset ring-amber-200 [&_svg]:size-3"
                      title={t("transactions:privateMaskedHint")}
                    >
                      <LockIcon />
                      {t("transactions:privateBadge")}
                    </span>
                  </Td>
                  <Td data-label={t("transactions:amountLabel")} align="right">{MASK}</Td>
                  <Td data-label={t("transactions:noteLabel")}>{MASK}</Td>
                  <Td data-label={t("transactions:creatorLabel")}>{memberName(row)}</Td>
                  <Td actions />
                </tr>
              ) : (
                <tr
                  key={row.id}
                  ref={row.id === highlightId ? highlightedRowRef : undefined}
                  // Inline: the table's zebra/hover backgrounds are more specific than a utility class.
                  style={
                    row.id === highlightId
                      ? { backgroundColor: "#fef3c7", boxShadow: "inset 4px 0 0 #f59e0b", transition: "background-color 0.6s" }
                      : undefined
                  }
                >
                  <Td>
                    <Checkbox
                      checked={selectedIds.has(row.id)}
                      disabled={!canChange(row)}
                      onChange={() => toggleSelected(row.id)}
                      aria-label={t("transactions:selectRowAria")}
                    />
                  </Td>
                  <Td data-label={t("transactions:timeLabel")}>
                    {/* Break only between date and time — never inside "2026-09-22" at a hyphen. */}
                    <span className="whitespace-nowrap">{row.occurredAt.slice(0, 10)}</span>{" "}
                    <span className="whitespace-nowrap">{row.occurredAt.slice(11)}</span>
                    {isLocked(row.occurredAt) && (
                      <span
                        className="ml-1 inline-flex align-middle text-slate-400 [&_svg]:size-3"
                        title={t("transactions:lockedRowHint")}
                        aria-label={t("transactions:lockedRowHint")}
                      >
                        <LockIcon />
                      </span>
                    )}
                  </Td>
                  <Td data-label={t("transactions:walletLabel")}>{walletName(row.walletId)}</Td>
                  <Td data-label={t("transactions:categoryLabel")}>
                    {row.splits ? (
                      <span title={row.splits.map((p) => `${categoryName(p.categoryId)}: ${formatCurrency(p.amount)}`).join("\n")}>
                        {row.splits.map((p) => categoryName(p.categoryId)).join(" + ")}
                        <span className="badge badge-neutral ml-1">{t("transactions:splitBadge", { count: row.splits.length })}</span>
                      </span>
                    ) : (
                      categoryName(row.categoryId)
                    )}
                  </Td>
                  <Td data-label={t("transactions:typeLabel")}>
                    {row.refundOfId ? (
                      <span className="badge badge-income">{t("transactions:refundBadge")}</span>
                    ) : (
                      <span className={`badge ${row.type === "EXPENSE" ? "badge-expense" : "badge-income"}`}>
                        {row.type === "EXPENSE" ? t("transactions:typeExpense") : t("transactions:typeIncome")}
                      </span>
                    )}
                  </Td>
                  <Td data-label={t("transactions:visibilityLabel")}>
                    {row.isPrivate ? (
                      <span
                        className="inline-flex items-center gap-1 whitespace-nowrap rounded-full bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-800 ring-1 ring-inset ring-amber-200 [&_svg]:size-3"
                        title={t("transactions:privateHint")}
                      >
                        <LockIcon />
                        {t("transactions:privateBadge")}
                      </span>
                    ) : (
                      <span className="inline-flex whitespace-nowrap rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                        {t("transactions:publicBadge")}
                      </span>
                    )}
                  </Td>
                  <Td
                    data-label={t("transactions:amountLabel")}
                    align="right"
                    className={row.type === "EXPENSE" && !row.refundOfId ? "amount-expense" : "amount-income"}
                  >
                    {row.refundOfId ? "+" : row.type === "EXPENSE" ? "-" : "+"}
                    {formatCurrency(Math.abs(Number(row.amount)))}
                  </Td>
                  <Td data-label={t("transactions:noteLabel")}>
                    {row.note}
                    {row.tags && (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {row.tags.map((tag) => (
                          <span key={tag} className="rounded-full bg-indigo-50 px-2 py-0.5 text-xs text-indigo-700">
                            #{tag}
                          </span>
                        ))}
                      </div>
                    )}
                  </Td>
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
                        {canRefund(row) && (
                          <Button size="sm" variant="secondary" onClick={() => setRefundTarget(row)}>
                            {t("transactions:refundButton")}
                          </Button>
                        )}
                        {canChange(row) && (
                          <>
                            {!row.refundOfId && (
                              <IconButton
                                onClick={() => startEdit(row)}
                              aria-label={t("common:edit")}
                            >
                                <EditIcon />
                              </IconButton>
                            )}
                            <IconButton variant="danger"
                              onClick={() => handleDelete(row.id)}
                              aria-label={t("common:delete")}
                            >
                              <TrashIcon />
                            </IconButton>
                          </>
                        )}
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

      <RefundModal
        transaction={refundTarget}
        onClose={() => setRefundTarget(null)}
        onDone={() => {
          setRefundTarget(null);
          load();
        }}
      />

      <TransactionHistoryModal
        transactionId={historyTransactionId}
        onClose={() => setHistoryTransactionId(null)}
        walletName={walletName}
        categoryName={categoryName}
      />
    </div>
  );
}
