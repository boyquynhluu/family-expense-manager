import { useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { formatServerDateTime, formatYearMonth } from "../utils/format";
import { LockIcon } from "./AppIcons";
import Pagination from "./Pagination";
import { Button } from "./ui/Button";
import { Field } from "./ui/Field";
import { Input } from "./ui/Input";
import { Table, TBody, Td, Th, THead } from "./ui/Table";

// Only past months can be closed — the latest one is last month.
function lastMonth() {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

/**
 * "Chốt sổ theo tháng" (backend PeriodLockService, README B2). Every member sees which months are closed and the
 * lock/unlock history; only the OWNER closes or reopens a month. `locks` / `onChanged` come from the page's
 * usePeriodLocks(), so its other sections (transfers, adjustments) hide edit/delete for closed months right away.
 */
export default function PeriodLocks({ isOwner, locks, onChanged }) {
  const { t } = useTranslation(["wallets", "common"]);
  const { pageData: historyPage, setPage: setHistoryPage, reload: reloadHistory } =
    usePagedList("/expenses/period-locks/history");
  const [month, setMonth] = useState(lastMonth);
  const [busy, setBusy] = useState(false);

  async function handleLock(e) {
    e.preventDefault();
    if (!(await confirmDialog(t("wallets:lockConfirm", { month: formatYearMonth(month) }), { tone: "primary", icon: "question" }))) {
      return;
    }
    setBusy(true);
    try {
      await client.post("/expenses/period-locks", { periodMonth: month });
      toast.success(t("wallets:lockDone", { month: formatYearMonth(month) }));
      onChanged();
      reloadHistory();
    } catch (err) {
      toast.error(err.response?.data?.message || t("wallets:lockFailed"));
    } finally {
      setBusy(false);
    }
  }

  async function handleUnlock(periodMonth) {
    if (!(await confirmDialog(t("wallets:unlockConfirm", { month: formatYearMonth(periodMonth) })))) return;
    setBusy(true);
    try {
      await client.delete(`/expenses/period-locks/${periodMonth}`);
      toast.success(t("wallets:unlockDone", { month: formatYearMonth(periodMonth) }));
      onChanged();
      reloadHistory();
    } catch (err) {
      toast.error(err.response?.data?.message || t("wallets:unlockFailed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="section-card">
      <h2>{t("wallets:lockTitle")}</h2>
      <p className="page-header-subtitle">{t("wallets:lockSubtitle")}</p>

      {isOwner && (
        <form className="inline-form" onSubmit={handleLock}>
          <Field>
            <span>
              {t("wallets:lockMonthLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <Input
              type="month"
              value={month}
              max={lastMonth()}
              onChange={(e) => setMonth(e.target.value)}
              required
            />
          </Field>
          <Button type="submit" disabled={busy}>
            {t("wallets:lockSubmit")}
          </Button>
        </form>
      )}

      {locks.length === 0 ? (
        <p className="empty-state">{t("wallets:lockEmpty")}</p>
      ) : (
        <Table>
          <THead>
            <tr>
              <Th>{t("wallets:lockColMonth")}</Th>
              <Th>{t("wallets:lockColBy")}</Th>
              <Th>{t("wallets:lockColAt")}</Th>
              <Th></Th>
            </tr>
          </THead>
          <TBody>
            {locks.map((l) => (
              <tr key={l.periodMonth}>
                <Td data-label={t("wallets:lockColMonth")}>
                  <span className="table-cell-icon">
                    <LockIcon /> {formatYearMonth(l.periodMonth)}
                  </span>
                </Td>
                <Td data-label={t("wallets:lockColBy")}>{l.lockedByName || t("wallets:formerMember")}</Td>
                <Td data-label={t("wallets:lockColAt")}>{formatServerDateTime(l.lockedAt)}</Td>
                <Td actions>
                  {isOwner && (
                    <Button variant="secondary" size="sm" disabled={busy} onClick={() => handleUnlock(l.periodMonth)}>
                      {t("wallets:unlockButton")}
                    </Button>
                  )}
                </Td>
              </tr>
            ))}
          </TBody>
        </Table>
      )}

      <h3 className="mt-4">{t("wallets:lockHistoryTitle")}</h3>
      {historyPage.content.length === 0 ? (
        <p className="empty-state">{t("wallets:lockHistoryEmpty")}</p>
      ) : (
        <Table>
          <THead>
            <tr>
              <Th>{t("wallets:lockColAt")}</Th>
              <Th>{t("wallets:lockColMonth")}</Th>
              <Th>{t("wallets:lockColAction")}</Th>
              <Th>{t("wallets:lockColBy")}</Th>
            </tr>
          </THead>
          <TBody>
            {historyPage.content.map((h) => (
              <tr key={h.id}>
                <Td data-label={t("wallets:lockColAt")}>{formatServerDateTime(h.createdAt)}</Td>
                <Td data-label={t("wallets:lockColMonth")}>{formatYearMonth(h.periodMonth)}</Td>
                <Td data-label={t("wallets:lockColAction")}>
                  <span className={`badge ${h.action === "LOCKED" ? "badge-neutral" : "badge-income"}`}>
                    {t(`wallets:lockAction.${h.action}`)}
                  </span>
                </Td>
                <Td data-label={t("wallets:lockColBy")}>{h.actorName || t("wallets:formerMember")}</Td>
              </tr>
            ))}
          </TBody>
        </Table>
      )}
      <Pagination pageData={historyPage} onPageChange={setHistoryPage} />
    </div>
  );
}
