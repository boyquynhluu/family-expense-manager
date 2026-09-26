import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import AmountInput from "../components/AmountInput";
import { EditIcon, TrashIcon, WalletIcon } from "../components/AppIcons";
import Pagination from "../components/Pagination";
import SeedDefaultsButton from "../components/SeedDefaultsButton";
import { useAuth } from "../hooks/useAuth";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency } from "../utils/format";
import { LIMITS } from "../utils/inputLimits";

// <input type="datetime-local"> wants "YYYY-MM-DDTHH:mm" in the browser's local time.
function nowForDateTimeInput() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

function emptyTransferForm() {
  return { fromWalletId: "", toWalletId: "", amount: "", occurredAt: nowForDateTimeInput(), note: "" };
}

export default function Wallets() {
  const { t } = useTranslation(["common", "wallets"]);
  const { role, userId } = useAuth();
  const isOwner = role === "OWNER";
  const [wallets, setWallets] = useState([]);
  const {
    pageData: transfersPage,
    page: transfersPageIndex,
    setPage: setTransfersPage,
    reload: reloadTransfers,
  } = usePagedList("/expenses/transfers");
  const transfers = transfersPage.content;
  const [transferForm, setTransferForm] = useState(emptyTransferForm);
  const [transferError, setTransferError] = useState("");
  const [editingTransferId, setEditingTransferId] = useState(null);
  const transferFormRef = useRef(null);
  const [name, setName] = useState("");
  const [currency, setCurrency] = useState("VND");
  const [initialBalance, setInitialBalance] = useState("0");
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/wallets").then((res) => setWallets(res.data.data));
  }

  useEffect(load, []);

  function startEdit(wallet) {
    setEditingId(wallet.id);
    setName(wallet.name);
    setCurrency(wallet.currency);
    setInitialBalance(String(wallet.initialBalance));
  }

  function cancelEdit() {
    setEditingId(null);
    setName("");
    setCurrency("VND");
    setInitialBalance("0");
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    const payload = {
      name,
      currency,
      initialBalance: Number(initialBalance),
    };
    try {
      if (editingId) {
        await client.put(`/expenses/wallets/${editingId}`, payload);
      } else {
        await client.post("/expenses/wallets", payload);
      }
      cancelEdit();
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("wallets:saveFailed"));
    }
  }

  async function handleDelete(id) {
    if (!(await confirmDialog(t("wallets:deleteConfirm")))) return;
    setError("");
    try {
      await client.delete(`/expenses/wallets/${id}`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("wallets:deleteFailed"));
    }
  }

  function updateTransferField(field, value) {
    setTransferForm((f) => ({ ...f, [field]: value }));
  }

  function walletName(id) {
    return wallets.find((w) => w.id === id)?.name ?? `#${id}`;
  }

  function canDeleteTransfer(transfer) {
    return isOwner || String(transfer.createdByUserId) === String(userId);
  }

  function startTransferEdit(transfer) {
    setEditingTransferId(transfer.id);
    setTransferError("");
    setTransferForm({
      fromWalletId: String(transfer.fromWalletId),
      toWalletId: String(transfer.toWalletId),
      amount: String(transfer.amount),
      occurredAt: transfer.occurredAt.slice(0, 16),
      note: transfer.note ?? "",
    });
    transferFormRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function cancelTransferEdit() {
    setEditingTransferId(null);
    setTransferError("");
    setTransferForm(emptyTransferForm());
  }

  async function handleTransferSubmit(e) {
    e.preventDefault();
    setTransferError("");
    if (transferForm.fromWalletId === transferForm.toWalletId) {
      setTransferError(t("wallets:transferSameWallet"));
      return;
    }
    const payload = {
      fromWalletId: Number(transferForm.fromWalletId),
      toWalletId: Number(transferForm.toWalletId),
      amount: Number(transferForm.amount),
      occurredAt: transferForm.occurredAt,
      note: transferForm.note || null,
    };
    try {
      if (editingTransferId) {
        await client.put(`/expenses/transfers/${editingTransferId}`, payload);
        toast.success(t("wallets:transferUpdated"));
        cancelTransferEdit();
        reloadTransfers();
      } else {
        await client.post("/expenses/transfers", payload);
        toast.success(t("wallets:transferSaved"));
        setTransferForm(emptyTransferForm());
        // Newest transfers come first, so jump to page 0 (reload if already there).
        if (transfersPageIndex === 0) reloadTransfers();
        else setTransfersPage(0);
      }
      load();
    } catch (err) {
      setTransferError(err.response?.data?.message || t("wallets:transferSaveFailed"));
    }
  }

  async function handleTransferDelete(id) {
    if (!(await confirmDialog(t("wallets:transferDeleteConfirm")))) return;
    try {
      await client.delete(`/expenses/transfers/${id}`);
      toast.success(t("wallets:transferDeleted"));
      if (editingTransferId === id) cancelTransferEdit();
      reloadTransfers();
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || t("wallets:transferDeleteFailed"));
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("wallets:title")}</h1>
          <p className="page-header-subtitle">{t("wallets:subtitle")}</p>
        </div>
      </div>

      {isOwner ? (
        <div className="section-card">
          <h2>{editingId ? t("wallets:editTitle") : t("wallets:addTitle")}</h2>
          <form className="inline-form" onSubmit={handleSubmit}>
            <label className="field">
              <span>
                {t("wallets:nameLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <input
                placeholder={t("wallets:namePlaceholder")}
                value={name}
                maxLength={LIMITS.walletName}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </label>
            <label className="field">
              <span>
                {t("wallets:currencyLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <input
                placeholder={t("wallets:currencyPlaceholder")}
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                maxLength={3}
                required
              />
            </label>
            <label className="field">
              <span>
                {t("wallets:initialBalanceLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <AmountInput placeholder="0" value={initialBalance} onChange={setInitialBalance} required />
            </label>
            <button type="submit">{editingId ? t("wallets:submitUpdate") : t("wallets:submitAdd")}</button>
            {editingId && (
              <button type="button" className="btn-secondary" onClick={cancelEdit}>
                {t("common:cancel")}
              </button>
            )}
          </form>
          {error && <p className="error-text">{error}</p>}
        </div>
      ) : (
        <div className="section-card">
          <p className="page-header-subtitle">{t("wallets:ownerOnlyManage")}</p>
        </div>
      )}

      <div className="section-card">
        <h2>{t("wallets:listTitle")}</h2>
        {wallets.length === 0 ? (
          <div>
            <p className="empty-state">{t("wallets:emptyState")}</p>
            <p className="page-header-subtitle">{t("wallets:seedDefaultsHint")}</p>
            <SeedDefaultsButton onDone={load} />
          </div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("wallets:colName")}</th>
                <th>{t("wallets:colCurrency")}</th>
                <th>{t("wallets:colInitialBalance")}</th>
                <th>{t("wallets:colCurrentBalance")}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {wallets.map((w) => (
                <tr key={w.id}>
                  <td data-label={t("wallets:colName")}>
                    <span className="table-cell-icon">
                      <WalletIcon /> {w.name}
                    </span>
                  </td>
                  <td data-label={t("wallets:colCurrency")}>{w.currency}</td>
                  <td data-label={t("wallets:colInitialBalance")}>{formatCurrency(w.initialBalance, w.currency)}</td>
                  <td data-label={t("wallets:colCurrentBalance")}>
                    <strong>{formatCurrency(w.currentBalance, w.currency)}</strong>
                  </td>
                  <td className="row-actions">
                    {isOwner && (
                      <>
                        <button
                          type="button"
                          className="icon-btn"
                          onClick={() => startEdit(w)}
                          aria-label={t("common:edit")}
                        >
                          <EditIcon />
                        </button>
                        <button
                          type="button"
                          className="icon-btn icon-btn-danger"
                          onClick={() => handleDelete(w.id)}
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
      </div>

      <div className="section-card" ref={transferFormRef}>
        <h2>{editingTransferId ? t("wallets:transferEditTitle") : t("wallets:transferTitle")}</h2>
        <p className="page-header-subtitle">{t("wallets:transferSubtitle")}</p>
        {wallets.length < 2 ? (
          <p className="empty-state">{t("wallets:transferNeedTwoWallets")}</p>
        ) : (
          <form className="inline-form" onSubmit={handleTransferSubmit}>
            <label className="field">
              <span>
                {t("wallets:fromWalletLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <select
                value={transferForm.fromWalletId}
                onChange={(e) => updateTransferField("fromWalletId", e.target.value)}
                required
              >
                <option value="">{t("wallets:selectWallet")}</option>
                {wallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>
                {t("wallets:toWalletLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <select
                value={transferForm.toWalletId}
                onChange={(e) => updateTransferField("toWalletId", e.target.value)}
                required
              >
                <option value="">{t("wallets:selectWallet")}</option>
                {wallets
                  .filter((w) => String(w.id) !== transferForm.fromWalletId)
                  .map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
              </select>
            </label>
            <label className="field">
              <span>
                {t("wallets:transferAmountLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <AmountInput placeholder="0" value={transferForm.amount} onChange={(v) => updateTransferField("amount", v)} required />
            </label>
            <label className="field">
              <span>
                {t("wallets:transferTimeLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <input
                type="datetime-local"
                value={transferForm.occurredAt}
                onChange={(e) => updateTransferField("occurredAt", e.target.value)}
                required
              />
            </label>
            <label className="field">
              {t("wallets:transferNoteLabel")}
              <input
                placeholder={t("wallets:transferNotePlaceholder")}
                value={transferForm.note}
                maxLength={LIMITS.transferNote}
                onChange={(e) => updateTransferField("note", e.target.value)}
              />
            </label>
            <button type="submit">
              {editingTransferId ? t("wallets:transferSubmitUpdate") : t("wallets:transferSubmit")}
            </button>
            {editingTransferId && (
              <button type="button" className="btn-secondary" onClick={cancelTransferEdit}>
                {t("common:cancel")}
              </button>
            )}
          </form>
        )}
        {transferError && <p className="error-text">{transferError}</p>}
      </div>

      <div className="section-card">
        <h2>{t("wallets:transferHistoryTitle")}</h2>
        {transfers.length === 0 ? (
          <p className="empty-state">{t("wallets:transferEmpty")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("wallets:colTime")}</th>
                <th>{t("wallets:colFromWallet")}</th>
                <th>{t("wallets:colToWallet")}</th>
                <th>{t("wallets:colAmount")}</th>
                <th>{t("wallets:colNote")}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {transfers.map((tr) => {
                const currency = wallets.find((w) => w.id === tr.fromWalletId)?.currency;
                return (
                  <tr key={tr.id}>
                    <td data-label={t("wallets:colTime")}>{tr.occurredAt.replace("T", " ").slice(0, 16)}</td>
                    <td data-label={t("wallets:colFromWallet")}>{walletName(tr.fromWalletId)}</td>
                    <td data-label={t("wallets:colToWallet")}>{walletName(tr.toWalletId)}</td>
                    <td data-label={t("wallets:colAmount")}>
                      <strong>{formatCurrency(tr.amount, currency)}</strong>
                    </td>
                    <td data-label={t("wallets:colNote")}>{tr.note || "-"}</td>
                    <td className="row-actions">
                      {canDeleteTransfer(tr) && (
                        <>
                          <button
                            type="button"
                            className="icon-btn"
                            onClick={() => startTransferEdit(tr)}
                            aria-label={t("common:edit")}
                          >
                            <EditIcon />
                          </button>
                          <button
                            type="button"
                            className="icon-btn icon-btn-danger"
                            onClick={() => handleTransferDelete(tr.id)}
                            aria-label={t("common:delete")}
                          >
                            <TrashIcon />
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        <Pagination pageData={transfersPage} onPageChange={setTransfersPage} />
      </div>
    </div>
  );
}
