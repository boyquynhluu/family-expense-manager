import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { formatServerDateTime } from "../utils/format";
import Modal from "./Modal";

// Fields that are derived or technical — not something a person changed.
const HIDDEN_FIELDS = new Set(["id", "familyId", "currentBalance", "deletedAt", "createdAt", "version"]);

function display(value) {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "boolean") return value ? "✓" : "✗";
  return String(value);
}

/**
 * README A3: who created / changed / deleted a wallet, budget or transfer, and which fields changed
 * (GET /expenses/audit-logs/{type}/{id}). `entity` = { type: "WALLET" | "BUDGET" | "TRANSFER", id, title } or null.
 */
export default function EntityHistoryModal({ entity, onClose }) {
  const { t } = useTranslation("common");
  const [rows, setRows] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!entity) return undefined;
    let cancelled = false;
    setRows(null);
    setError("");
    client
      .get(`/expenses/audit-logs/${entity.type}/${entity.id}`)
      .then((res) => !cancelled && setRows(res.data.data))
      .catch((err) => !cancelled && setError(err.response?.data?.message || t("historyLoadFailed")));
    return () => {
      cancelled = true;
    };
  }, [entity, t]);

  if (!entity) return null;

  function changes(before, after) {
    const keys = new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})]);
    return [...keys]
      .filter((k) => !HIDDEN_FIELDS.has(k))
      .filter((k) => JSON.stringify(before?.[k] ?? null) !== JSON.stringify(after?.[k] ?? null))
      .map((k) => ({ key: k, from: before?.[k], to: after?.[k] }));
  }

  return (
    <Modal open onClose={onClose} title={`${t("historyTitle")} — ${entity.title ?? ""}`}>
      {error && <p className="error-text">{error}</p>}
      {rows === null && !error && <p className="empty-state">{t("loading")}</p>}
      {rows !== null && rows.length === 0 && <p className="empty-state">{t("historyEmpty")}</p>}
      {rows !== null && rows.length > 0 && (
        <ol className="space-y-3">
          {rows.map((r) => (
            <li key={r.id} className="rounded-lg border border-slate-200 p-3">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="badge badge-neutral">{t(`historyAction.${r.action}`, r.action)}</span>
                <strong>{r.actorName || t("historySystem")}</strong>
                <span className="text-slate-500">{formatServerDateTime(r.createdAt)}</span>
              </div>
              {r.action === "UPDATED" && (
                <ul className="mt-2 text-sm">
                  {changes(r.before, r.after).map((c) => (
                    <li key={c.key}>
                      <code>{c.key}</code>: {display(c.from)} → <strong>{display(c.to)}</strong>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ol>
      )}
    </Modal>
  );
}
