import { useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency, formatServerDateTime } from "../utils/format";
import Pagination from "./Pagination";
import { Button } from "./ui/Button";
import { Table, TBody, Td, Th, THead } from "./ui/Table";

const STATUS_STYLE = {
  PENDING: "bg-amber-50 text-amber-800 ring-amber-200",
  APPROVED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  REJECTED: "bg-red-50 text-red-700 ring-red-200",
};

/**
 * README A5 "duyệt khoản chi vượt ngưỡng": expenses above the family's approval threshold wait here — the OWNER
 * sees every request and approves (it is then recorded as a transaction) or rejects it; anyone else sees their own.
 * `reloadKey` changes after a new request is filed; `onApproved` lets the page reload its list.
 */
export default function TransactionApprovals({ isOwner, walletName, categoryName, reloadKey, onApproved }) {
  const { t } = useTranslation(["transactions", "common"]);
  const { pageData, setPage, reload } = usePagedList("/expenses/approvals", { reloadKey });
  const [busyId, setBusyId] = useState(null);

  async function decide(approval, approve) {
    const question = approve
      ? t("approvalApproveConfirm", { amount: formatCurrency(approval.amount), name: approval.requesterName })
      : t("approvalRejectConfirm", { amount: formatCurrency(approval.amount), name: approval.requesterName });
    if (!(await confirmDialog(question, approve ? { tone: "primary", icon: "question" } : undefined))) return;
    setBusyId(approval.id);
    try {
      await client.post(`/expenses/approvals/${approval.id}/${approve ? "approve" : "reject"}`, approve ? undefined : {});
      toast.success(approve ? t("approvalApproved") : t("approvalRejected"));
      reload();
      if (approve) onApproved?.();
    } catch (err) {
      toast.error(err.response?.data?.message || t("approvalFailed"));
    } finally {
      setBusyId(null);
    }
  }

  if (pageData.totalElements === 0) return null;

  return (
    <div className="section-card">
      <h2>{isOwner ? t("approvalsTitleOwner") : t("approvalsTitleMember")}</h2>
      <Table>
        <THead>
          <tr>
            <Th>{t("timeLabel")}</Th>
            <Th>{t("creatorLabel")}</Th>
            <Th>{t("walletLabel")}</Th>
            <Th>{t("categoryLabel")}</Th>
            <Th align="right">{t("amountLabel")}</Th>
            <Th>{t("noteLabel")}</Th>
            <Th>{t("approvalStatusLabel")}</Th>
            <Th></Th>
          </tr>
        </THead>
        <TBody>
          {pageData.content.map((a) => (
            <tr key={a.id}>
              <Td data-label={t("timeLabel")}>{formatServerDateTime(a.occurredAt)}</Td>
              <Td data-label={t("creatorLabel")}>{a.requesterName}</Td>
              <Td data-label={t("walletLabel")}>{walletName(a.walletId)}</Td>
              <Td data-label={t("categoryLabel")}>{categoryName(a.categoryId)}</Td>
              <Td data-label={t("amountLabel")} align="right" className="amount-expense">
                -{formatCurrency(a.amount)}
              </Td>
              <Td data-label={t("noteLabel")}>{a.note || "-"}</Td>
              <Td data-label={t("approvalStatusLabel")}>
                <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ring-inset ${STATUS_STYLE[a.status]}`}>
                  {t(`approvalStatus.${a.status}`)}
                </span>
                {a.decidedByName && <div className="text-xs text-slate-500">{a.decidedByName}</div>}
              </Td>
              <Td actions>
                {isOwner && a.status === "PENDING" && (
                  <>
                    <Button size="sm" disabled={busyId === a.id} onClick={() => decide(a, true)}>
                      {t("approvalApprove")}
                    </Button>
                    <Button size="sm" variant="secondary" disabled={busyId === a.id} onClick={() => decide(a, false)}>
                      {t("approvalReject")}
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
