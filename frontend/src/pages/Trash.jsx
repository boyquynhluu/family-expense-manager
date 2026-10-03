import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import Pagination from "../components/Pagination";
import { useAuth } from "../hooks/useAuth";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog, confirmTypedDialog } from "../utils/confirm";
import { formatCurrency, formatServerDateTime } from "../utils/format";
import { notifyTrashChanged } from "../utils/trashEvents";

import { LockIcon, TrashIcon } from "../components/AppIcons";
import { Button } from "../components/ui/Button";
import { Table, TBody, Td, Th, THead } from "../components/ui/Table";

const MASK = "***";
// Matches the backend default trash.retention-days (TrashRetentionScheduler).
const RETENTION_DAYS = 30;

export default function Trash() {
  const { t } = useTranslation("trash");
  const { role, userId } = useAuth();
  const isOwner = role === "OWNER";
  // One usePagedList per table: each keeps its own page state, so paging one table
  // never refetches (or resets) the other two.
  const { pageData: walletsPage, setPage: setWalletsPage, reload: loadWallets } =
    usePagedList("/expenses/wallets/trash");
  const { pageData: categoriesPage, setPage: setCategoriesPage, reload: loadCategories } =
    usePagedList("/expenses/categories/trash");
  const { pageData: transactionsPage, setPage: setTransactionsPage, reload: loadTransactions } =
    usePagedList("/expenses/transactions/trash");
  const wallets = walletsPage.content;
  const categories = categoriesPage.content;
  const transactions = transactionsPage.content;

  async function handleRestore(kind, id, reload) {
    const kindLabel = t(`trash:kind.${kind}`);
    if (!(await confirmDialog(t("trash:restoreConfirm", {kind: kindLabel})))) return;

    try {
      await client.post(`/expenses/${kind}/${id}/restore`);
      notifyTrashChanged();
      toast.success(t("restoreSuccess"));
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("restoreFailed"));
    }
  }

  // Same rights as restoring (the backend re-checks): wallets/categories OWNER, transactions creator or OWNER.
  async function handlePurge(kind, id, reload) {
    const kindLabel = t(`trash:kind.${kind}`);
    if (!(await confirmDialog(t("purgeConfirm", { kind: kindLabel }), { confirmButtonText: t("purgeButton") }))) return;

    try {
      await client.delete(`/expenses/trash/${kind}/${id}`);
      notifyTrashChanged();
      toast.success(t("purgeSuccess"));
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("purgeFailed"));
    }
  }

  const trashTotal = walletsPage.totalElements + categoriesPage.totalElements + transactionsPage.totalElements;

  async function handleEmptyTrash() {
    const confirmed = await confirmTypedDialog(t("emptyConfirm", { count: trashTotal }), t("emptyConfirmWord"), {
      title: t("emptyTitle"),
      confirmButtonText: t("emptyButton"),
    });
    if (!confirmed) return;

    try {
      const res = await client.delete("/expenses/trash");
      const result = res.data.data;
      notifyTrashChanged();
      const purged = result.transactions + result.wallets + result.categories;
      if (result.skipped > 0) {
        toast(t("emptyPartial", { count: purged, skipped: result.skipped }), { icon: "⚠️", duration: 6000 });
      } else {
        toast.success(t("emptySuccess", { count: purged }));
      }
    } catch (err) {
      toast.error(err.response?.data?.message || t("emptyFailed"));
    } finally {
      loadWallets();
      loadCategories();
      loadTransactions();
    }
  }

  function formatDateTime(value) {
    if (!value) return "-";
    return formatServerDateTime(value);
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("title")}</h1>
          <p className="page-header-subtitle">{t("subtitle")}</p>
          <p className="page-header-subtitle">{t("retentionHint", { days: RETENTION_DAYS })}</p>
        </div>
        {isOwner && (
          <Button variant="danger" onClick={handleEmptyTrash} disabled={trashTotal === 0}>
            <TrashIcon />
            {t("emptyButton")}
          </Button>
        )}
      </div>

      <div className="section-card">
        <h2>{t("walletsTitle")}</h2>
        {wallets.length === 0 ? (
          <p className="empty-state">{t("walletsEmpty")}</p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>{t("colName")}</Th>
                <Th>{t("colCurrency")}</Th>
                <Th>{t("colDeletedAt")}</Th>
                {isOwner && <Th></Th>}
              </tr>
            </THead>
            <TBody>
              {wallets.map((w) => (
                <tr key={w.id}>
                  <Td data-label={t("colName")}>{w.name}</Td>
                  <Td data-label={t("colCurrency")}>{w.currency}</Td>
                  <Td data-label={t("colDeletedAt")}>{formatDateTime(w.deletedAt)}</Td>
                  {isOwner && (
                    <Td actions>
                      <Button variant="success-outline" size="sm" onClick={() => handleRestore("wallets", w.id, loadWallets)}>
                        {t("restoreButton")}
                      </Button>
                      <Button variant="danger-outline" size="sm" onClick={() => handlePurge("wallets", w.id, loadWallets)}>
                        {t("purgeButton")}
                      </Button>
                    </Td>
                  )}
                </tr>
              ))}
            </TBody>
          </Table>
        )}
        <Pagination pageData={walletsPage} onPageChange={setWalletsPage} />
      </div>

      <div className="section-card">
        <h2>{t("categoriesTitle")}</h2>
        {categories.length === 0 ? (
          <p className="empty-state">{t("categoriesEmpty")}</p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>{t("colName")}</Th>
                <Th>{t("colType")}</Th>
                <Th>{t("colDeletedAt")}</Th>
                {isOwner && <Th></Th>}
              </tr>
            </THead>
            <TBody>
              {categories.map((c) => (
                <tr key={c.id}>
                  <Td data-label={t("colName")}>{c.name}</Td>
                  <Td data-label={t("colType")}>{c.type === "EXPENSE" ? t("typeExpense") : t("typeIncome")}</Td>
                  <Td data-label={t("colDeletedAt")}>{formatDateTime(c.deletedAt)}</Td>
                  {isOwner && (
                    <Td actions>
                      <Button variant="success-outline" size="sm" onClick={() => handleRestore("categories", c.id, loadCategories)}>
                        {t("restoreButton")}
                      </Button>
                      <Button variant="danger-outline" size="sm" onClick={() => handlePurge("categories", c.id, loadCategories)}>
                        {t("purgeButton")}
                      </Button>
                    </Td>
                  )}
                </tr>
              ))}
            </TBody>
          </Table>
        )}
        <Pagination pageData={categoriesPage} onPageChange={setCategoriesPage} />
      </div>

      <div className="section-card">
        <h2>{t("transactionsTitle")}</h2>
        {transactions.length === 0 ? (
          <p className="empty-state">{t("transactionsEmpty")}</p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th align="right">{t("colAmount")}</Th>
                <Th>{t("colType")}</Th>
                <Th>{t("colVisibility")}</Th>
                <Th>{t("colOccurredAt")}</Th>
                <Th>{t("colNote")}</Th>
                <Th>{t("colDeletedAt")}</Th>
                <Th>{t("colDeletedBy")}</Th>
                <Th></Th>
              </tr>
            </THead>
            <TBody>
              {transactions.map((t2) => {
                const mine = String(t2.userId) === String(userId);
                // Another member's private transaction: the API already withheld its details.
                const masked = t2.isPrivate && !mine;
                return (
                <tr key={t2.id}>
                  <Td data-label={t("colAmount")} align="right">{masked ? MASK : formatCurrency(t2.amount)}</Td>
                  <Td data-label={t("colType")}>
                    {masked ? MASK : t2.type === "EXPENSE" ? t("typeExpense") : t("typeIncome")}
                  </Td>
                  <Td data-label={t("colVisibility")}>
                    {t2.isPrivate ? (
                      <span
                        className="inline-flex items-center gap-1 whitespace-nowrap rounded-full bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-800 ring-1 ring-inset ring-amber-200 [&_svg]:size-3"
                        title={masked ? t("privateMaskedHint") : t("privateOwnHint")}
                      >
                        <LockIcon />
                        {t("privateBadge")}
                      </span>
                    ) : (
                      <span className="inline-flex whitespace-nowrap rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                        {t("publicBadge")}
                      </span>
                    )}
                  </Td>
                  {/* The date of a private transaction is public (only its details are masked). */}
                  <Td data-label={t("colOccurredAt")}>{formatDateTime(t2.occurredAt)}</Td>
                  <Td data-label={t("colNote")}>{masked ? MASK : t2.note || "-"}</Td>
                  <Td data-label={t("colDeletedAt")}>{formatDateTime(t2.deletedAt)}</Td>
                  {/* Null for rows deleted before "who deleted it" was recorded (V12). */}
                  <Td data-label={t("colDeletedBy")}>{t2.deletedByName || "-"}</Td>
                  <Td actions>
                    {!masked && (isOwner || mine) && (
                      <>
                        <Button variant="success-outline" size="sm" onClick={() => handleRestore("transactions", t2.id, loadTransactions)}>
                          {t("restoreButton")}
                        </Button>
                        <Button variant="danger-outline" size="sm" onClick={() => handlePurge("transactions", t2.id, loadTransactions)}>
                          {t("purgeButton")}
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
        <Pagination pageData={transactionsPage} onPageChange={setTransactionsPage} />
      </div>
    </div>
  );
}
