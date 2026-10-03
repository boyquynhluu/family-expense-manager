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
import Pagination from "./Pagination";
import { Button } from "./ui/Button";
import { Field } from "./ui/Field";
import { Input, Select } from "./ui/Input";
import { Table, TBody, Td, Th, THead } from "./ui/Table";

const emptyForm = { fromWalletId: "", toWalletId: "", amount: "", note: "" };

const STATUS_STYLE = {
  PENDING: "bg-amber-50 text-amber-800 ring-amber-200",
  COMPLETED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  REJECTED: "bg-red-50 text-red-700 ring-red-200",
};

/**
 * "Yêu cầu chuyển tiền" (backend TransferRequestService): ask another member to move money from their private
 * wallet into one of mine. The owner gets an email; the request is "Đang chờ" until they approve ("Đã chuyển" —
 * the real transfer is made then) or reject it ("Từ chối"). `onTransferred` lets the page refresh balances and the
 * transfer history after an approval.
 */
export default function TransferRequests({ wallets, userId, walletOptionLabel, onTransferred }) {
  const { t } = useTranslation(["wallets", "common"]);
  const cleanText = useCleanText();
  const { pageData, setPage, reload } = usePagedList("/expenses/transfer-requests");
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState(null);

  // Ask FROM another member's private wallet (shared wallets have no single owner to ask), INTO one of mine.
  const sourceWallets = wallets.filter((w) => w.ownerUserId != null && String(w.ownerUserId) !== String(userId));
  const myWallets = wallets.filter((w) => String(w.ownerUserId) === String(userId));

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const from = wallets.find((w) => String(w.id) === form.fromWalletId);
    const message = t("wallets:requestConfirm", {
      amount: formatCurrency(Number(form.amount)),
      wallet: from ? walletOptionLabel(from) : "",
    });
    if (!(await confirmDialog(message, { tone: "primary", icon: "question" }))) return;
    setError("");
    try {
      await client.post("/expenses/transfer-requests", {
        fromWalletId: Number(form.fromWalletId),
        toWalletId: Number(form.toWalletId),
        amount: Number(form.amount),
        note: form.note || null,
      });
      toast.success(t("wallets:requestSent"));
      setForm(emptyForm);
      reload();
    } catch (err) {
      setError(err.response?.data?.message || t("wallets:requestFailed"));
    }
  }

  async function decide(request, action) {
    const approve = action === "approve";
    const text = t(approve ? "wallets:requestApproveConfirm" : "wallets:requestRejectConfirm", {
      name: request.requesterName || "",
      amount: formatCurrency(request.amount),
    });
    if (!(await confirmDialog(text, approve ? { tone: "primary", icon: "question" } : {}))) return;
    setBusyId(request.id);
    try {
      await client.post(`/expenses/transfer-requests/${request.id}/${action}`);
      toast.success(t(approve ? "wallets:requestApproved" : "wallets:requestRejected"));
      if (approve) onTransferred?.();
    } catch (err) {
      toast.error(err.response?.data?.message || t("wallets:requestDecideFailed"));
    } finally {
      setBusyId(null);
      reload();
    }
  }

  const rows = pageData.content;

  return (
    <>
      <div className="section-card">
        <h2>{t("wallets:requestTitle")}</h2>
        <p className="page-header-subtitle">{t("wallets:requestSubtitle")}</p>
        {sourceWallets.length === 0 || myWallets.length === 0 ? (
          <p className="empty-state">
            {myWallets.length === 0 ? t("wallets:transferNoOwnWallet") : t("wallets:requestNoSource")}
          </p>
        ) : (
          <form className="inline-form" onSubmit={handleSubmit}>
            <Field>
              <span>
                {t("wallets:requestFromLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Select value={form.fromWalletId} onChange={(e) => update("fromWalletId", e.target.value)} required>
                <option value="">{t("wallets:selectWallet")}</option>
                {sourceWallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {walletOptionLabel(w)}
                  </option>
                ))}
              </Select>
            </Field>
            <Field>
              <span>
                {t("wallets:requestToLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Select value={form.toWalletId} onChange={(e) => update("toWalletId", e.target.value)} required>
                <option value="">{t("wallets:selectWallet")}</option>
                {myWallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
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
                value={form.amount}
                onChange={(v) => update("amount", v)}
                required
                positive
                min={LIMITS.minTransactionAmount}
                max={LIMITS.maxTransactionAmount}
              />
            </Field>
            <Field>
              {t("wallets:requestNoteLabel")}
              <Input
                value={form.note}
                validate={cleanText}
                maxLength={LIMITS.transferNote}
                onChange={(e) => update("note", e.target.value)}
                placeholder={t("wallets:requestNotePlaceholder")}
              />
            </Field>
            <Button type="submit">{t("wallets:requestSubmit")}</Button>
          </form>
        )}
        {error && <p className="error-text">{error}</p>}
      </div>

      <div className="section-card">
        <h2>{t("wallets:requestListTitle")}</h2>
        {rows.length === 0 ? (
          <p className="empty-state">{t("wallets:requestEmpty")}</p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>{t("wallets:colTime")}</Th>
                <Th>{t("wallets:colRequester")}</Th>
                <Th>{t("wallets:colFromWallet")}</Th>
                <Th>{t("wallets:colToWallet")}</Th>
                <Th align="right">{t("wallets:colAmount")}</Th>
                <Th>{t("wallets:colNote")}</Th>
                <Th>{t("wallets:colStatus")}</Th>
                <Th></Th>
              </tr>
            </THead>
            <TBody>
              {rows.map((r) => {
                const mine = String(r.requesterUserId) === String(userId);
                return (
                  <tr key={r.id}>
                    <Td data-label={t("wallets:colTime")}>{formatServerDateTime(r.createdAt)}</Td>
                    <Td data-label={t("wallets:colRequester")}>
                      {mine ? t("wallets:requestYou") : r.requesterName || "-"}
                    </Td>
                    <Td data-label={t("wallets:colFromWallet")}>{r.fromWalletName || t("wallets:deletedWallet")}</Td>
                    <Td data-label={t("wallets:colToWallet")}>{r.toWalletName || t("wallets:deletedWallet")}</Td>
                    <Td data-label={t("wallets:colAmount")} align="right">
                      {formatCurrency(r.amount)}
                    </Td>
                    <Td data-label={t("wallets:colNote")}>{r.note || "-"}</Td>
                    <Td data-label={t("wallets:colStatus")}>
                      <span
                        className={`inline-flex whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ring-inset ${STATUS_STYLE[r.status] ?? ""}`}
                        title={r.decidedByName ? `${r.decidedByName} — ${formatServerDateTime(r.decidedAt)}` : undefined}
                      >
                        {t(`wallets:requestStatus.${r.status}`)}
                      </span>
                    </Td>
                    <Td actions>
                      {r.canDecide && (
                        <>
                          <Button variant="success-outline" size="sm" disabled={busyId === r.id}
                            onClick={() => decide(r, "approve")}>
                            {t("wallets:requestApprove")}
                          </Button>
                          <Button variant="danger-outline" size="sm" disabled={busyId === r.id}
                            onClick={() => decide(r, "reject")}>
                            {t("wallets:requestReject")}
                          </Button>
                        </>
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
    </>
  );
}
