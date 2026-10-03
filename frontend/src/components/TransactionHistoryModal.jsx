import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { formatCurrency, formatServerDateTime } from "../utils/format";
import { HistoryIcon } from "./AppIcons";
import Modal from "./Modal";

// occurredAt is the user's own wall-clock time ("2026-09-12T08:30:00"), shown as typed — unlike createdAt,
// which is a server (UTC) timestamp and goes through formatServerDateTime.
function formatOccurredAt(value) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

/** Fields compared between the before/after snapshots of an UPDATED entry, in display order. */
function changedFields(before, after, { t, walletName, categoryName }) {
  const typeLabel = (type) => (type === "INCOME" ? t("transactions:historyTypeIncome") : t("transactions:historyTypeExpense"));
  const receiptLabel = (has) => (has ? t("transactions:historyReceiptYes") : t("transactions:historyReceiptNo"));
  const fields = [
    ["amount", (s) => formatCurrency(s.amount), (a, b) => Number(a.amount) !== Number(b.amount)],
    ["type", (s) => typeLabel(s.type)],
    ["wallet", (s) => walletName(s.walletId), (a, b) => a.walletId !== b.walletId],
    ["category", (s) => categoryName(s.categoryId), (a, b) => a.categoryId !== b.categoryId],
    ["occurredAt", (s) => formatOccurredAt(s.occurredAt)],
    ["note", (s) => s.note || "-"],
    ["receipt", (s) => receiptLabel(s.hasReceipt)],
  ];
  return fields
    .filter(([, show, differs]) => (differs ? differs(before, after) : show(before) !== show(after)))
    .map(([key, show]) => ({ key, from: show(before), to: show(after) }));
}

/**
 * Who created / edited / deleted / restored one transaction, and what changed — backed by
 * GET /expenses/transactions/{id}/history (TRANSACTION_AUDIT_LOGS). `transactionId` null = closed.
 */
export default function TransactionHistoryModal({ transactionId, onClose, walletName, categoryName }) {
  const { t } = useTranslation(["common", "transactions"]);
  const [entries, setEntries] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (transactionId == null) return undefined;
    let cancelled = false;
    setEntries(null);
    setError("");
    client
      .get(`/expenses/transactions/${transactionId}/history`)
      .then((res) => !cancelled && setEntries(res.data.data))
      .catch((err) => !cancelled && setError(err.response?.data?.message || t("transactions:historyLoadFailed")));
    return () => {
      cancelled = true;
    };
  }, [transactionId, t]);

  return (
    <Modal
      open={transactionId != null}
      onClose={onClose}
      closeLabel={t("common:close")}
      className="history-modal"
      icon={<HistoryIcon />}
      title={t("transactions:historyTitle")}
      subtitle={t("transactions:historySubtitle")}
    >
      {error && <p className="error-text">{error}</p>}
      {!error && entries === null && <p className="history-empty">{t("common:loading")}</p>}
      {entries?.length === 0 && <p className="history-empty">{t("transactions:historyEmpty")}</p>}
      {entries?.length > 0 && (
        <ol className="history-list">
          {entries.map((entry) => {
            const changes =
              entry.action === "UPDATED" && entry.before && entry.after
                ? changedFields(entry.before, entry.after, { t, walletName, categoryName })
                : [];
            const snapshot = entry.after ?? entry.before;
            return (
              <li key={entry.id} className={`history-item history-item-${entry.action.toLowerCase()}`}>
                <div className="history-item-header">
                  <span className={`badge history-badge-${entry.action.toLowerCase()}`}>
                    {t(`transactions:historyAction${entry.action}`)}
                  </span>
                  <span className="history-actor">{entry.actorName || t("transactions:formerMember")}</span>
                  <time className="history-time">{formatServerDateTime(entry.createdAt)}</time>
                </div>
                {entry.action === "UPDATED" ? (
                  changes.length === 0 ? (
                    <p className="history-detail">{t("transactions:historyNoChanges")}</p>
                  ) : (
                    <ul className="history-changes">
                      {changes.map((c) => (
                        <li key={c.key}>
                          <span className="history-field">{t(`transactions:historyField_${c.key}`)}</span>
                          <span className="history-from">{c.from}</span>
                          <span aria-hidden="true">→</span>
                          <span className="history-to">{c.to}</span>
                        </li>
                      ))}
                    </ul>
                  )
                ) : (
                  snapshot && (
                    <p className="history-detail">
                      {formatCurrency(snapshot.amount)} · {categoryName(snapshot.categoryId)} ·{" "}
                      {walletName(snapshot.walletId)} · {formatOccurredAt(snapshot.occurredAt)}
                    </p>
                  )
                )}
              </li>
            );
          })}
        </ol>
      )}
    </Modal>
  );
}
