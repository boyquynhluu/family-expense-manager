import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { formatCurrency } from "../utils/format";
import { WalletIcon } from "./AppIcons";

function signedClass(value) {
  if (value > 0) return "amount-income";
  if (value < 0) return "amount-expense";
  return undefined;
}

function signed(value, currency) {
  return `${value > 0 ? "+" : ""}${formatCurrency(value, currency)}`;
}

// Per-wallet opening balance → income/expense/transfers → surplus/deficit → closing balance
// for one month (GET /expenses/reports/wallet-month). `reloadKey` lets the parent force a
// refetch after it changes wallets/transfers.
export default function WalletMonthlyTable({ yearMonth, reloadKey = 0 }) {
  const { t } = useTranslation("wallets");
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setError("");
    client
      .get("/expenses/reports/wallet-month", { params: { yearMonth } })
      .then((res) => {
        if (!cancelled) setRows(res.data.data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.response?.data?.message || t("monthlyLoadFailed"));
      });
    return () => {
      cancelled = true;
    };
  }, [yearMonth, reloadKey, t]);

  if (error) return <p className="error-text">{error}</p>;
  if (rows.length === 0) return <p className="empty-state">{t("emptyState")}</p>;

  const currency = rows[0].currency;
  const sum = (field) => rows.reduce((acc, r) => acc + Number(r[field]), 0);
  const totals = {
    openingBalance: sum("openingBalance"),
    income: sum("income"),
    expense: sum("expense"),
    net: sum("net"),
    closingBalance: sum("closingBalance"),
  };

  return (
    <table>
      <thead>
        <tr>
          <th>{t("colName")}</th>
          <th>{t("colOpening")}</th>
          <th>{t("colIncome")}</th>
          <th>{t("colExpense")}</th>
          <th>{t("colTransfers")}</th>
          <th>{t("colNet")}</th>
          <th>{t("colClosing")}</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => {
          const net = Number(r.net);
          const closing = Number(r.closingBalance);
          const transferIn = Number(r.transferIn);
          const transferOut = Number(r.transferOut);
          return (
            <tr key={r.walletId}>
              <td data-label={t("colName")}>
                <span className="table-cell-icon">
                  <WalletIcon /> {r.walletName}
                </span>
              </td>
              <td data-label={t("colOpening")} className={Number(r.openingBalance) < 0 ? "amount-expense" : undefined}>
                {formatCurrency(r.openingBalance, r.currency)}
              </td>
              <td data-label={t("colIncome")} className="amount-income">
                {formatCurrency(r.income, r.currency)}
              </td>
              <td data-label={t("colExpense")} className="amount-expense">
                {formatCurrency(r.expense, r.currency)}
              </td>
              <td data-label={t("colTransfers")}>
                {transferIn === 0 && transferOut === 0
                  ? "-"
                  : `+${formatCurrency(transferIn, r.currency)} / -${formatCurrency(transferOut, r.currency)}`}
              </td>
              <td data-label={t("colNet")} className={signedClass(net)}>
                {signed(net, r.currency)}
              </td>
              <td data-label={t("colClosing")} className={closing < 0 ? "amount-expense" : undefined}>
                <strong>{formatCurrency(closing, r.currency)}</strong>
              </td>
            </tr>
          );
        })}
      </tbody>
      {rows.length > 1 && (
        <tfoot>
          <tr>
            <td data-label={t("colName")}>
              <strong>{t("totalRow")}</strong>
            </td>
            <td data-label={t("colOpening")}>{formatCurrency(totals.openingBalance, currency)}</td>
            <td data-label={t("colIncome")} className="amount-income">
              {formatCurrency(totals.income, currency)}
            </td>
            <td data-label={t("colExpense")} className="amount-expense">
              {formatCurrency(totals.expense, currency)}
            </td>
            <td data-label={t("colTransfers")}>-</td>
            <td data-label={t("colNet")} className={signedClass(totals.net)}>
              {signed(totals.net, currency)}
            </td>
            <td data-label={t("colClosing")} className={totals.closingBalance < 0 ? "amount-expense" : undefined}>
              <strong>{formatCurrency(totals.closingBalance, currency)}</strong>
            </td>
          </tr>
        </tfoot>
      )}
    </table>
  );
}
