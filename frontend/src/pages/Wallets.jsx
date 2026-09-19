import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { EditIcon, TrashIcon, WalletIcon } from "../components/AppIcons";
import { formatCurrency } from "../utils/format";
import { useAuth } from "../hooks/useAuth";

export default function Wallets() {
  const { t } = useTranslation(["common", "wallets"]);
  const { role } = useAuth();
  const isOwner = role === "OWNER";
  const [wallets, setWallets] = useState([]);
  const [name, setName] = useState("");
  const [currency, setCurrency] = useState("VND");
  const [initialBalance, setInitialBalance] = useState("0");
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/wallets").then((res) => setWallets(res.data.data));
  }

  useEffect(load, []);

  function startEdit(wallet) {
    setEditingId(wallet.id);
    setName(wallet.name);
    setCurrency(wallet.currency);
    setInitialBalance(String(wallet.initialBalance));
  }

  function cancelEdit() {
    setEditingId(null);
    setName("");
    setCurrency("VND");
    setInitialBalance("0");
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    const payload = {
      name,
      currency,
      initialBalance: Number(initialBalance),
    };
    try {
      if (editingId) {
        await client.put(`/expenses/wallets/${editingId}`, payload);
      } else {
        await client.post("/expenses/wallets", payload);
      }
      cancelEdit();
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("wallets:saveFailed"));
    }
  }

  async function handleDelete(id) {
    if (!window.confirm(t("wallets:deleteConfirm"))) return;
    setError("");
    try {
      await client.delete(`/expenses/wallets/${id}`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || t("wallets:deleteFailed"));
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("wallets:title")}</h1>
          <p className="page-header-subtitle">{t("wallets:subtitle")}</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? t("wallets:editTitle") : t("wallets:addTitle")}</h2>
        <form className="inline-form" onSubmit={handleSubmit}>
          <label className="field">
            {t("wallets:nameLabel")}
            <input
              placeholder={t("wallets:namePlaceholder")}
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </label>
          <label className="field">
            {t("wallets:currencyLabel")}
            <input
              placeholder={t("wallets:currencyPlaceholder")}
              value={currency}
              onChange={(e) => setCurrency(e.target.value.toUpperCase())}
              maxLength={3}
              required
            />
          </label>
          <label className="field">
            {t("wallets:initialBalanceLabel")}
            <input
              type="number"
              step="0.01"
              placeholder="0"
              value={initialBalance}
              onChange={(e) => setInitialBalance(e.target.value)}
              required
            />
          </label>
          <button type="submit">{editingId ? t("wallets:submitUpdate") : t("wallets:submitAdd")}</button>
          {editingId && (
            <button type="button" className="btn-secondary" onClick={cancelEdit}>
              {t("common:cancel")}
            </button>
          )}
        </form>
        {error && <p className="error-text">{error}</p>}
      </div>

      <div className="section-card">
        <h2>{t("wallets:listTitle")}</h2>
        {wallets.length === 0 ? (
          <p className="empty-state">{t("wallets:emptyState")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("wallets:colName")}</th>
                <th>{t("wallets:colCurrency")}</th>
                <th>{t("wallets:colInitialBalance")}</th>
                <th>{t("wallets:colCurrentBalance")}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {wallets.map((w) => (
                <tr key={w.id}>
                  <td data-label={t("wallets:colName")}>
                    <span className="table-cell-icon">
                      <WalletIcon /> {w.name}
                    </span>
                  </td>
                  <td data-label={t("wallets:colCurrency")}>{w.currency}</td>
                  <td data-label={t("wallets:colInitialBalance")}>{formatCurrency(w.initialBalance, w.currency)}</td>
                  <td data-label={t("wallets:colCurrentBalance")}>
                    <strong>{formatCurrency(w.currentBalance, w.currency)}</strong>
                  </td>
                  <td className="row-actions">
                    <button type="button" className="icon-btn" onClick={() => startEdit(w)} aria-label={t("common:edit")}>
                      <EditIcon />
                    </button>
                    {isOwner && (
                      <button
                        type="button"
                        className="icon-btn icon-btn-danger"
                        onClick={() => handleDelete(w.id)}
                        aria-label={t("common:delete")}
                      >
                        <TrashIcon />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
