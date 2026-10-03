import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import AmountInput from "../components/AmountInput";
import { CalendarIcon, EditIcon, TrashIcon, WalletIcon } from "../components/AppIcons";
import Pagination from "../components/Pagination";
import SeedDefaultsButton from "../components/SeedDefaultsButton";
import TransferRequests from "../components/TransferRequests";
import WalletMonthlyTable from "../components/WalletMonthlyTable";
import { useAuth } from "../hooks/useAuth";
import { useClientPage } from "../hooks/useClientPage";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { maxDateTime, minDateTime } from "../utils/dateLimits";
import { formatCurrency } from "../utils/format";
import { LIMITS } from "../utils/inputLimits";
import { notifyTrashChanged } from "../utils/trashEvents";


import { Button, IconButton } from "../components/ui/Button";
import { Field } from "../components/ui/Field";
import { Input, Select } from "../components/ui/Input";
import { Table, TBody, Td, Th, THead } from "../components/ui/Table";
import { hasInvalidNameChars } from "../utils/namePatterns";
import { useCleanText } from "../utils/textQuality";
// <input type="datetime-local"> wants "YYYY-MM-DDTHH:mm" in the browser's local time.
function nowForDateTimeInput() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function emptyTransferForm() {
  return { fromWalletId: "", toWalletId: "", amount: "", occurredAt: nowForDateTimeInput(), note: "" };
}

export default function Wallets() {
  const { t } = useTranslation(["common", "wallets"]);
  const cleanText = useCleanText();
  // Character rule first (it names what is wrong), then profanity/junk.
  const validateName = (value) => (hasInvalidNameChars(value) ? t("wallets:nameInvalidChars") : cleanText(value));
  const { role, userId } = useAuth();
  const isOwner = role === "OWNER";
  const [wallets, setWallets] = useState([]);
  // Paged on the client: the full list is still needed for the wallet selects and owner checks.
  const walletsPage = useClientPage(wallets);
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
  // "" = shared by the whole family ("ví chung"); otherwise the owning member's user id.
  const [ownerUserId, setOwnerUserId] = useState("");
  const [members, setMembers] = useState([]);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");
  const [yearMonth, setYearMonth] = useState(currentYearMonth);
  const [reloadKey, setReloadKey] = useState(0);

  function load() {
    client.get("/expenses/wallets").then((res) => setWallets(res.data.data));
    setReloadKey((k) => k + 1);
  }

  useEffect(load, []);

  useEffect(() => {
    client
      .get("/auth/family/members", { params: { page: 0, size: 100 } })
      .then((res) => setMembers(res.data.data.content))
      .catch(() => {});
  }, []);

  function ownerName(id) {
    if (id == null) return null;
    return members.find((m) => String(m.id) === String(id))?.displayName ?? t("wallets:formerMember");
  }

  // Transfers go from the sender's OWN private wallet to another member's private wallet or a shared one
  // (same rule as the backend, the OWNER included). When editing, the sender is the transfer's creator, and the
  // wallets it already uses stay selectable (an older transfer may predate the rule).
  const editingTransfer = editingTransferId ? transfers.find((tr) => tr.id === editingTransferId) : null;
  const senderId = editingTransfer ? editingTransfer.createdByUserId : userId;
  const isSendersOwn = (w) => w.ownerUserId != null && String(w.ownerUserId) === String(senderId);
  const transferSourceWallets = wallets.filter((w) => isSendersOwn(w) || w.id === editingTransfer?.fromWalletId);
  const transferDestinationWallets = wallets.filter((w) => !isSendersOwn(w) || w.id === editingTransfer?.toWalletId);

  function walletOptionLabel(w) {
    return `${w.name} — ${w.ownerUserId == null ? t("wallets:sharedBadge") : ownerName(w.ownerUserId)}`;
  }

  function startEdit(wallet) {
    setEditingId(wallet.id);
    setName(wallet.name);
    setCurrency(wallet.currency);
    setInitialBalance(String(wallet.initialBalance));
    setOwnerUserId(wallet.ownerUserId == null ? "" : String(wallet.ownerUserId));
  }

  function cancelEdit() {
    setEditingId(null);
    setName("");
    setCurrency("VND");
    setInitialBalance("0");
    setOwnerUserId("");
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const msgConfirm = editingId ? t("wallets:updateConfirm") : t("wallets:addConfirm");
    if (!(await confirmDialog(`${msgConfirm} ${name}`, { tone: "primary", icon: "question" }))) return;
    setError("");

    const payload = {
      name,
      currency,
      initialBalance: Number(initialBalance),
      ownerUserId: ownerUserId ? Number(ownerUserId) : null,
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
      notifyTrashChanged();
      toast.success(t("wallets:deleteSuccess"));
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
      const formattedAmount = Number(transferForm.amount).toLocaleString("vi-VN");
      if (editingTransferId) {
        if (!(await confirmDialog(t("wallets:transferUpdateConfirm", {amount: `${formattedAmount} ₫`})))) return;

        await client.put(`/expenses/transfers/${editingTransferId}`, payload);
        toast.success(t("wallets:transferUpdated"));
        cancelTransferEdit();
        reloadTransfers();
      } else {
        if (!(await confirmDialog(t("wallets:transferAddConfirm", {amount: `${formattedAmount} ₫`})))) return;

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
            <Field>
              <span>
                {t("wallets:nameLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Input
                placeholder={t("wallets:namePlaceholder")}
                value={name} validate={validateName}
                maxLength={LIMITS.walletName}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </Field>
            <Field>
              <span>
                {t("wallets:currencyLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Input
                placeholder={t("wallets:currencyPlaceholder")}
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase().replace(/[^A-Z]/g, ""))}
                maxLength={3}
                pattern="[A-Z]{3}"
                title={t("wallets:currencyPatternHint")}
                required
                disabled
              />
            </Field>
            <Field>
              <span>
                {t("wallets:initialBalanceLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <AmountInput placeholder="0" value={initialBalance} onChange={setInitialBalance} required />
            </Field>
            <Field>
              {t("wallets:ownerLabel")}
              <Select value={ownerUserId} onChange={(e) => setOwnerUserId(e.target.value)}>
                <option value="">{t("wallets:sharedWallet")}</option>
                {members.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.displayName}
                  </option>
                ))}
              </Select>
            </Field>
            <Button type="submit">{editingId ? t("wallets:submitUpdate") : t("wallets:submitAdd")}</Button>
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
          <Table>
            <THead>
              <tr>
                <Th>{t("wallets:colName")}</Th>
                <Th>{t("wallets:colOwner")}</Th>
                <Th>{t("wallets:colCurrency")}</Th>
                <Th align="right">{t("wallets:colInitialBalance")}</Th>
                <Th align="right">{t("wallets:colCurrentBalance")}</Th>
                <Th></Th>
              </tr>
            </THead>
            <TBody>
              {walletsPage.rows.map((w) => (
                <tr key={w.id}>
                  <Td data-label={t("wallets:colName")}>
                    <span className="table-cell-icon">
                      <WalletIcon /> {w.name}
                    </span>
                  </Td>
                  <Td data-label={t("wallets:colOwner")}>
                    {w.ownerUserId == null ? (
                      <span className="badge badge-neutral">{t("wallets:sharedBadge")}</span>
                    ) : (
                      ownerName(w.ownerUserId)
                    )}
                  </Td>
                  <Td data-label={t("wallets:colCurrency")}>{w.currency}</Td>
                  <Td data-label={t("wallets:colInitialBalance")} align="right">{formatCurrency(w.initialBalance, w.currency)}</Td>
                  <Td data-label={t("wallets:colCurrentBalance")} align="right">
                    <strong>{formatCurrency(w.currentBalance, w.currency)}</strong>
                  </Td>
                  <Td actions>
                    {isOwner && (
                      <>
                        <IconButton
                          onClick={() => startEdit(w)}
                          aria-label={t("common:edit")}
                        >
                          <EditIcon />
                        </IconButton>
                        <IconButton variant="danger"
                          onClick={() => handleDelete(w.id)}
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
        <Pagination pageData={walletsPage.pageData} onPageChange={walletsPage.setPage} />
      </div>

      <div className="section-card">
        <div className="page-header">
          <h2>{t("wallets:monthlyTitle")}</h2>
          <label className="month-picker">
            <CalendarIcon />
            <input type="month" value={yearMonth} onChange={(e) => setYearMonth(e.target.value)} />
          </label>
        </div>
        <p className="page-header-subtitle">{t("wallets:monthlyHint")}</p>
        <WalletMonthlyTable yearMonth={yearMonth} reloadKey={reloadKey} />
      </div>

      <div className="section-card" ref={transferFormRef}>
        <h2>{editingTransferId ? t("wallets:transferEditTitle") : t("wallets:transferTitle")}</h2>
        <p className="page-header-subtitle">{t("wallets:transferSubtitle")}</p>
        {transferSourceWallets.length === 0 ? (
          <p className="empty-state">{t("wallets:transferNoOwnWallet")}</p>
        ) : transferDestinationWallets.length === 0 ? (
          <p className="empty-state">{t("wallets:transferNoDestination")}</p>
        ) : (
          <form className="inline-form" onSubmit={handleTransferSubmit}>
            <Field>
              <span>
                {t("wallets:fromWalletLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Select
                value={transferForm.fromWalletId}
                onChange={(e) => updateTransferField("fromWalletId", e.target.value)}
                required
              >
                <option value="">{t("wallets:selectWallet")}</option>
                {transferSourceWallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {walletOptionLabel(w)}
                  </option>
                ))}
              </Select>
            </Field>
            <Field>
              <span>
                {t("wallets:toWalletLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Select
                value={transferForm.toWalletId}
                onChange={(e) => updateTransferField("toWalletId", e.target.value)}
                required
                validate={(v) => (v && v === transferForm.fromWalletId ? t("validation:walletsMustDiffer") : "")}
              >
                <option value="">{t("wallets:selectWallet")}</option>
                {transferDestinationWallets
                  .filter((w) => String(w.id) !== transferForm.fromWalletId)
                  .map((w) => (
                    <option key={w.id} value={w.id}>
                      {walletOptionLabel(w)}
                    </option>
                  ))}
              </Select>
            </Field>
            <Field>
              <span>
                {t("wallets:transferAmountLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <AmountInput
                placeholder="0"
                value={transferForm.amount}
                onChange={(v) => updateTransferField("amount", v)}
                required
                positive
                min={LIMITS.minTransactionAmount}
                max={LIMITS.maxTransactionAmount}
              />
            </Field>
            <Field>
              <span>
                {t("wallets:transferTimeLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Input
                type="datetime-local"
                value={transferForm.occurredAt}
                onChange={(e) => updateTransferField("occurredAt", e.target.value)}
                min={minDateTime()}
                max={maxDateTime()}
                required
              />
            </Field>
            <Field>
              {t("wallets:transferNoteLabel")}
              <Input
                placeholder={t("wallets:transferNotePlaceholder")}
                value={transferForm.note} validate={cleanText}
                maxLength={LIMITS.transferNote}
                onChange={(e) => updateTransferField("note", e.target.value)}
              />
            </Field>
            <Button type="submit">
              {editingTransferId ? t("wallets:transferSubmitUpdate") : t("wallets:transferSubmit")}
            </Button>
            {editingTransferId && (
              <Button variant="secondary" onClick={cancelTransferEdit}>
                {t("common:cancel")}
              </Button>
            )}
          </form>
        )}
        {transferError && <p className="error-text">{transferError}</p>}
      </div>

      <TransferRequests
        wallets={wallets}
        userId={userId}
        walletOptionLabel={walletOptionLabel}
        onTransferred={() => {
          load();
          reloadTransfers();
        }}
      />

      <div className="section-card">
        <h2>{t("wallets:transferHistoryTitle")}</h2>
        {transfers.length === 0 ? (
          <p className="empty-state">{t("wallets:transferEmpty")}</p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>{t("wallets:colTime")}</Th>
                <Th>{t("wallets:colFromWallet")}</Th>
                <Th>{t("wallets:colToWallet")}</Th>
                <Th align="right">{t("wallets:colAmount")}</Th>
                <Th>{t("wallets:colNote")}</Th>
                <Th></Th>
              </tr>
            </THead>
            <TBody>
              {transfers.map((tr) => {
                const currency = wallets.find((w) => w.id === tr.fromWalletId)?.currency;
                return (
                  <tr key={tr.id}>
                    <Td data-label={t("wallets:colTime")}>{tr.occurredAt.replace("T", " ").slice(0, 16)}</Td>
                    <Td data-label={t("wallets:colFromWallet")}>{walletName(tr.fromWalletId)}</Td>
                    <Td data-label={t("wallets:colToWallet")}>{walletName(tr.toWalletId)}</Td>
                    <Td data-label={t("wallets:colAmount")} align="right">
                      <strong>{formatCurrency(tr.amount, currency)}</strong>
                    </Td>
                    <Td data-label={t("wallets:colNote")}>{tr.note || "-"}</Td>
                    <Td actions>
                      {canDeleteTransfer(tr) && (
                        <>
                          <IconButton
                            onClick={() => startTransferEdit(tr)}
                            aria-label={t("common:edit")}
                          >
                            <EditIcon />
                          </IconButton>
                          <IconButton variant="danger"
                            onClick={() => handleTransferDelete(tr.id)}
                            aria-label={t("common:delete")}
                          >
                            <TrashIcon />
                          </IconButton>
                        </>
                      )}
                    </Td>
                  </tr>
                );
              })}
            </TBody>
          </Table>
        )}
        <Pagination pageData={transfersPage} onPageChange={setTransfersPage} />
      </div>
    </div>
  );
}
