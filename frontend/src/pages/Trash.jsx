import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import Pagination from "../components/Pagination";
import { formatCurrency, formatServerDateTime } from "../utils/format";
import { useAuth } from "../hooks/useAuth";
import { usePagedList } from "../hooks/usePagedList";

export default function Trash() {
  const { t } = useTranslation("trash");
  const { role } = useAuth();
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
    try {
      await client.post(`/expenses/${kind}/${id}/restore`);
      toast.success(t("restoreSuccess"));
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("restoreFailed"));
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
        </div>
      </div>

      <div className="section-card">
        <h2>{t("walletsTitle")}</h2>
        {wallets.length === 0 ? (
          <p className="empty-state">{t("walletsEmpty")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("colName")}</th>
                <th>{t("colCurrency")}</th>
                <th>{t("colDeletedAt")}</th>
                {isOwner && <th></th>}
              </tr>
            </thead>
            <tbody>
              {wallets.map((w) => (
                <tr key={w.id}>
                  <td data-label={t("colName")}>{w.name}</td>
                  <td data-label={t("colCurrency")}>{w.currency}</td>
                  <td data-label={t("colDeletedAt")}>{formatDateTime(w.deletedAt)}</td>
                  {isOwner && (
                    <td className="row-actions">
                      <button type="button" onClick={() => handleRestore("wallets", w.id, loadWallets)}>
                        {t("restoreButton")}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination pageData={walletsPage} onPageChange={setWalletsPage} />
      </div>

      <div className="section-card">
        <h2>{t("categoriesTitle")}</h2>
        {categories.length === 0 ? (
          <p className="empty-state">{t("categoriesEmpty")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("colName")}</th>
                <th>{t("colType")}</th>
                <th>{t("colDeletedAt")}</th>
                {isOwner && <th></th>}
              </tr>
            </thead>
            <tbody>
              {categories.map((c) => (
                <tr key={c.id}>
                  <td data-label={t("colName")}>{c.name}</td>
                  <td data-label={t("colType")}>{c.type === "EXPENSE" ? t("typeExpense") : t("typeIncome")}</td>
                  <td data-label={t("colDeletedAt")}>{formatDateTime(c.deletedAt)}</td>
                  {isOwner && (
                    <td className="row-actions">
                      <button type="button" onClick={() => handleRestore("categories", c.id, loadCategories)}>
                        {t("restoreButton")}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination pageData={categoriesPage} onPageChange={setCategoriesPage} />
      </div>

      <div className="section-card">
        <h2>{t("transactionsTitle")}</h2>
        {transactions.length === 0 ? (
          <p className="empty-state">{t("transactionsEmpty")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("colAmount")}</th>
                <th>{t("colType")}</th>
                <th>{t("colOccurredAt")}</th>
                <th>{t("colNote")}</th>
                <th>{t("colDeletedAt")}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((t2) => (
                <tr key={t2.id}>
                  <td data-label={t("colAmount")}>{formatCurrency(t2.amount)}</td>
                  <td data-label={t("colType")}>{t2.type === "EXPENSE" ? t("typeExpense") : t("typeIncome")}</td>
                  <td data-label={t("colOccurredAt")}>{formatDateTime(t2.occurredAt)}</td>
                  <td data-label={t("colNote")}>{t2.note || "-"}</td>
                  <td data-label={t("colDeletedAt")}>{formatDateTime(t2.deletedAt)}</td>
                  <td className="row-actions">
                    <button type="button" onClick={() => handleRestore("transactions", t2.id, loadTransactions)}>
                      {t("restoreButton")}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination pageData={transactionsPage} onPageChange={setTransactionsPage} />
      </div>
    </div>
  );
}
