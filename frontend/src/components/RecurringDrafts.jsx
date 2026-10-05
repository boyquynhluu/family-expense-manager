import { useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency } from "../utils/format";
import { LIMITS } from "../utils/inputLimits";
import AmountInput from "./AmountInput";
import Pagination from "./Pagination";
import { Button } from "./ui/Button";
import { Table, TBody, Td, Th, THead } from "./ui/Table";

/**
 * README A4 "nhắc và chờ xác nhận": occurrences of CONFIRM-mode recurring rules waiting for their real amount.
 * Confirm records a normal transaction on the due date; skip closes the occurrence. The rule's creator or the
 * OWNER may decide (the backend checks it too).
 */
export default function RecurringDrafts({ walletName, categoryName, canDecide, onDecided }) {
  const { t } = useTranslation(["recurringTransactions", "common"]);
  const { pageData, setPage, reload } = usePagedList("/expenses/recurring-drafts");
  const [amounts, setAmounts] = useState({});
  const [busyId, setBusyId] = useState(null);

  async function confirm(draft) {
    const amount = amounts[draft.id] ?? String(draft.suggestedAmount);
    if (!(await confirmDialog(t("draftConfirmQuestion", { amount: formatCurrency(Number(amount)) }), { tone: "primary", icon: "question" }))) return;
    setBusyId(draft.id);
    try {
      await client.post(`/expenses/recurring-drafts/${draft.id}/confirm`, { amount: Number(amount), note: null });
      toast.success(t("draftConfirmed"));
      reload();
      onDecided?.();
    } catch (err) {
      toast.error(err.response?.data?.message || t("draftFailed"));
    } finally {
      setBusyId(null);
    }
  }

  async function skip(draft) {
    if (!(await confirmDialog(t("draftSkipQuestion")))) return;
    setBusyId(draft.id);
    try {
      await client.post(`/expenses/recurring-drafts/${draft.id}/skip`);
      toast.success(t("draftSkipped"));
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("draftFailed"));
    } finally {
      setBusyId(null);
    }
  }

  if (pageData.totalElements === 0) return null;

  return (
    <div className="section-card">
      <h2>{t("draftsTitle")}</h2>
      <p className="page-header-subtitle">{t("draftsHint")}</p>
      <Table>
        <THead>
          <tr>
            <Th>{t("draftDueDate")}</Th>
            <Th>{t("walletLabel")}</Th>
            <Th>{t("categoryLabel")}</Th>
            <Th>{t("noteLabel")}</Th>
            <Th align="right">{t("draftRealAmount")}</Th>
            <Th></Th>
          </tr>
        </THead>
        <TBody>
          {pageData.content.map((d) => (
            <tr key={d.id}>
              <Td data-label={t("draftDueDate")}>{d.dueDate}</Td>
              <Td data-label={t("walletLabel")}>{walletName(d.walletId)}</Td>
              <Td data-label={t("categoryLabel")}>{categoryName(d.categoryId)}</Td>
              <Td data-label={t("noteLabel")}>{d.note || "-"}</Td>
              <Td data-label={t("draftRealAmount")} align="right">
                {canDecide(d) ? (
                  <AmountInput
                    value={amounts[d.id] ?? String(d.suggestedAmount)}
                    onChange={(v) => setAmounts((a) => ({ ...a, [d.id]: v }))}
                    positive
                    min={LIMITS.minTransactionAmount}
                    max={LIMITS.maxTransactionAmount}
                  />
                ) : (
                  formatCurrency(d.suggestedAmount)
                )}
              </Td>
              <Td actions>
                {canDecide(d) && (
                  <>
                    <Button size="sm" disabled={busyId === d.id} onClick={() => confirm(d)}>
                      {t("draftConfirmButton")}
                    </Button>
                    <Button size="sm" variant="secondary" disabled={busyId === d.id} onClick={() => skip(d)}>
                      {t("draftSkipButton")}
                    </Button>
                  </>
                )}
              </Td>
            </tr>
          ))}
        </TBody>
      </Table>
      <Pagination pageData={pageData} onPageChange={setPage} />
    </div>
  );
}
