import { useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency, formatServerDateTime } from "../utils/format";
import { LIMITS } from "../utils/inputLimits";
import { useCleanText } from "../utils/textQuality";
import AmountInput from "./AmountInput";
import { LockIcon, TrashIcon } from "./AppIcons";
import Pagination from "./Pagination";
import { Button, IconButton } from "./ui/Button";
import { Field } from "./ui/Field";
import { Input, Select } from "./ui/Input";
import { Table, TBody, Td, Th, THead } from "./ui/Table";

const emptyForm = { walletId: "", actualBalance: "", note: "" };

function signed(value, currency) {
  return `${Number(value) > 0 ? "+" : ""}${formatCurrency(value, currency)}`;
}

/**
 * "Điều chỉnh số dư" (backend WalletAdjustmentService, README B1): type the balance a wallet REALLY holds and the
 * difference from the app's balance is recorded — it moves the wallet balance only, never income/expense, budgets
 * or category reports. The OWNER may adjust any wallet, a member only their own private wallet. `onChanged` lets
 * the page refresh balances afterwards.
 */
export default function WalletAdjustments({ wallets, userId, isOwner, walletOptionLabel, isLocked, onChanged }) {
  const { t } = useTranslation(["wallets", "common"]);
  const cleanText = useCleanText();
  const { pageData, page, setPage, reload } = usePagedList("/expenses/wallet-adjustments");
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState("");

  const adjustable = wallets.filter(
    (w) => isOwner || (w.ownerUserId != null && String(w.ownerUserId) === String(userId)),
  );
  const selected = wallets.find((w) => String(w.id) === form.walletId);
  const difference =
    selected && form.actualBalance !== "" && form.actualBalance !== "-"
      ? Number(form.actualBalance) - Number(selected.currentBalance)
      : null;

  function walletOf(id) {
    return wallets.find((w) => w.id === id);
  }

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    if (difference === null || Number.isNaN(difference)) return;
    if (difference === 0) {
      setError(t("wallets:adjustNoDifference"));
      return;
    }
    const ok = await confirmDialog(
      t("wallets:adjustConfirm", {
        wallet: selected.name,
        from: formatCurrency(selected.currentBalance, selected.currency),
        to: formatCurrency(form.actualBalance, selected.currency),
      }),
      { tone: "primary", icon: "question" },
    );
    if (!ok) return;
    try {
      await client.post("/expenses/wallet-adjustments", {
        walletId: Number(form.walletId),
        actualBalance: Number(form.actualBalance),
        note: form.note || null,
      });
      toast.success(t("wallets:adjustSaved"));
      setForm(emptyForm);
      // Newest first, so jump to page 0 (reload if already there).
      if (page === 0) reload();
      else setPage(0);
      onChanged();
    } catch (err) {
      setError(err.response?.data?.message || t("wallets:adjustFailed"));
    }
  }

  async function handleDelete(adjustment) {
    if (!(await confirmDialog(t("wallets:adjustDeleteConfirm")))) return;
    try {
      await client.delete(`/expenses/wallet-adjustments/${adjustment.id}`);
      toast.success(t("wallets:adjustDeleted"));
      reload();
      onChanged();
    } catch (err) {
      toast.error(err.response?.data?.message || t("wallets:adjustDeleteFailed"));
    }
  }

  const adjustments = pageData.content;

  return (
    <div className="section-card">
      <h2>{t("wallets:adjustTitle")}</h2>
      <p className="page-header-subtitle">{t("wallets:adjustSubtitle")}</p>
      {adjustable.length === 0 ? (
        <p className="empty-state">{t("wallets:adjustNoWallet")}</p>
      ) : (
        <form className="inline-form" onSubmit={handleSubmit}>
          <Field>
            <span>
              {t("wallets:adjustWalletLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <Select value={form.walletId} onChange={(e) => update("walletId", e.target.value)} required>
              <option value="">{t("wallets:selectWallet")}</option>
              {adjustable.map((w) => (
                <option key={w.id} value={w.id}>
                  {walletOptionLabel(w)}
                </option>
              ))}
            </Select>
          </Field>
          <Field>
            <span>
              {t("wallets:adjustActualLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <AmountInput
              placeholder="0"
              value={form.actualBalance}
              onChange={(v) => update("actualBalance", v)}
              allowNegative
              required
            />
          </Field>
          <Field>
            {t("wallets:adjustNoteLabel")}
            <Input
              placeholder={t("wallets:adjustNotePlaceholder")}
              value={form.note}
              validate={cleanText}
              maxLength={LIMITS.transferNote}
              onChange={(e) => update("note", e.target.value)}
            />
          </Field>
          <Button type="submit">{t("wallets:adjustSubmit")}</Button>
        </form>
      )}
      {selected && (
        <p className="page-header-subtitle">
          {t("wallets:adjustAppBalance", { balance: formatCurrency(selected.currentBalance, selected.currency) })}
          {difference !== null && !Number.isNaN(difference) && difference !== 0 && (
            <>
              {" · "}
              <span className={difference > 0 ? "amount-income" : "amount-expense"}>
                {t("wallets:adjustDifference", { amount: signed(difference, selected.currency) })}
              </span>
            </>
          )}
        </p>
      )}
      {error && <p className="error-text">{error}</p>}

      <h3 className="mt-4">{t("wallets:adjustHistoryTitle")}</h3>
      {adjustments.length === 0 ? (
        <p className="empty-state">{t("wallets:adjustEmpty")}</p>
      ) : (
        <Table>
          <THead>
            <tr>
              <Th>{t("wallets:colTime")}</Th>
              <Th>{t("wallets:colName")}</Th>
              <Th align="right">{t("wallets:adjustColBefore")}</Th>
              <Th align="right">{t("wallets:adjustColAfter")}</Th>
              <Th align="right">{t("wallets:adjustColDifference")}</Th>
              <Th>{t("wallets:colNote")}</Th>
              <Th>{t("wallets:adjustColBy")}</Th>
              <Th></Th>
            </tr>
          </THead>
          <TBody>
            {adjustments.map((a) => {
              const wallet = walletOf(a.walletId);
              const currency = wallet?.currency;
              const locked = isLocked(a.occurredAt);
              const canDelete = isOwner || String(a.createdByUserId) === String(userId);
              return (
                <tr key={a.id}>
                  <Td data-label={t("wallets:colTime")}>{formatServerDateTime(a.occurredAt)}</Td>
                  <Td data-label={t("wallets:colName")}>{wallet?.name ?? t("wallets:deletedWallet")}</Td>
                  <Td data-label={t("wallets:adjustColBefore")} align="right">
                    {formatCurrency(a.balanceBefore, currency)}
                  </Td>
                  <Td data-label={t("wallets:adjustColAfter")} align="right">
                    {formatCurrency(a.balanceAfter, currency)}
                  </Td>
                  <Td
                    data-label={t("wallets:adjustColDifference")}
                    align="right"
                    className={Number(a.amount) > 0 ? "amount-income" : "amount-expense"}
                  >
                    <strong>{signed(a.amount, currency)}</strong>
                  </Td>
                  <Td data-label={t("wallets:colNote")}>{a.note || "-"}</Td>
                  <Td data-label={t("wallets:adjustColBy")}>{a.createdByName || t("wallets:formerMember")}</Td>
                  <Td actions>
                    {locked ? (
                      <span title={t("wallets:lockedRowHint")} aria-label={t("wallets:lockedRowHint")}>
                        <LockIcon />
                      </span>
                    ) : (
                      canDelete && (
                        <IconButton variant="danger" onClick={() => handleDelete(a)} aria-label={t("common:delete")}>
                          <TrashIcon />
                        </IconButton>
                      )
                    )}
                  </Td>
                </tr>
              );
            })}
          </TBody>
        </Table>
      )}
      <Pagination pageData={pageData} onPageChange={setPage} />
    </div>
  );
}
