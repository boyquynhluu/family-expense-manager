import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { formatCurrency } from "../utils/format";
import { WalletIcon } from "./AppIcons";

import { Table, THead, TBody, TFoot, Th, Td } from "./ui/Table";
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
    <Table>
      <THead>
        <tr>
          <Th>{t("colName")}</Th>
          <Th align="right">{t("colOpening")}</Th>
          <Th align="right">{t("colIncome")}</Th>
          <Th align="right">{t("colExpense")}</Th>
          <Th align="right">{t("colTransfers")}</Th>
          <Th align="right">{t("colNet")}</Th>
          <Th align="right">{t("colClosing")}</Th>
        </tr>
      </THead>
      <TBody>
        {rows.map((r) => {
          const net = Number(r.net);
          const closing = Number(r.closingBalance);
          const transferIn = Number(r.transferIn);
          const transferOut = Number(r.transferOut);
          return (
            <tr key={r.walletId}>
              <Td data-label={t("colName")}>
                <span className="table-cell-icon">
                  <WalletIcon /> {r.walletName}
                </span>
              </Td>
              <Td data-label={t("colOpening")} align="right" className={Number(r.openingBalance) < 0 ? "amount-expense" : undefined}>
                {formatCurrency(r.openingBalance, r.currency)}
              </Td>
              <Td data-label={t("colIncome")} align="right" className="amount-income">
                {formatCurrency(r.income, r.currency)}
              </Td>
              <Td data-label={t("colExpense")} align="right" className="amount-expense">
                {formatCurrency(r.expense, r.currency)}
              </Td>
              <Td data-label={t("colTransfers")} align="right">
                {transferIn === 0 && transferOut === 0
                  ? "-"
                  : `+${formatCurrency(transferIn, r.currency)} / -${formatCurrency(transferOut, r.currency)}`}
              </Td>
              <Td data-label={t("colNet")} align="right" className={signedClass(net)}>
                {signed(net, r.currency)}
              </Td>
              <Td data-label={t("colClosing")} align="right" className={closing < 0 ? "amount-expense" : undefined}>
                <strong>{formatCurrency(closing, r.currency)}</strong>
              </Td>
            </tr>
          );
        })}
      </TBody>
      {rows.length > 1 && (
        <TFoot>
          <tr>
            <Td data-label={t("colName")}>
              <strong>{t("totalRow")}</strong>
            </Td>
            <Td data-label={t("colOpening")} align="right">{formatCurrency(totals.openingBalance, currency)}</Td>
            <Td data-label={t("colIncome")} align="right" className="amount-income">
              {formatCurrency(totals.income, currency)}
            </Td>
            <Td data-label={t("colExpense")} align="right" className="amount-expense">
              {formatCurrency(totals.expense, currency)}
            </Td>
            <Td data-label={t("colTransfers")} align="right">-</Td>
            <Td data-label={t("colNet")} align="right" className={signedClass(totals.net)}>
              {signed(totals.net, currency)}
            </Td>
            <Td data-label={t("colClosing")} align="right" className={totals.closingBalance < 0 ? "amount-expense" : undefined}>
              <strong>{formatCurrency(totals.closingBalance, currency)}</strong>
            </Td>
          </tr>
        </TFoot>
      )}
    </Table>
  );
}
