import { useEffect, useState } from "react";
import client from "../api/client";
import { EditIcon, WalletIcon } from "../components/AppIcons";
import { formatCurrency } from "../utils/format";

export default function Wallets() {
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
      setError(err.response?.data?.message || "Lưu ví thất bại");
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Ví</h1>
          <p className="page-header-subtitle">Quản lý các ví/tài khoản tiền của gia đình</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? "Cập nhật ví" : "Thêm ví mới"}</h2>
        <form className="inline-form" onSubmit={handleSubmit}>
          <label className="field">
            Tên ví
            <input placeholder="VD: Tiền mặt" value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label className="field">
            Tiền tệ
            <input
              placeholder="VND"
              value={currency}
              onChange={(e) => setCurrency(e.target.value.toUpperCase())}
              maxLength={3}
              required
            />
          </label>
          <label className="field">
            Số dư ban đầu
            <input
              type="number"
              step="0.01"
              placeholder="0"
              value={initialBalance}
              onChange={(e) => setInitialBalance(e.target.value)}
              required
            />
          </label>
          <button type="submit">{editingId ? "Cập nhật" : "Thêm ví"}</button>
          {editingId && (
            <button type="button" className="btn-secondary" onClick={cancelEdit}>
              Huỷ
            </button>
          )}
        </form>
        {error && <p className="error-text">{error}</p>}
      </div>

      <div className="section-card">
        <h2>Danh sách ví</h2>
        {wallets.length === 0 ? (
          <p className="empty-state">Chưa có ví nào</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Tên</th>
                <th>Tiền tệ</th>
                <th>Số dư ban đầu</th>
                <th>Số dư hiện tại</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {wallets.map((w) => (
                <tr key={w.id}>
                  <td>
                    <span className="table-cell-icon">
                      <WalletIcon /> {w.name}
                    </span>
                  </td>
                  <td>{w.currency}</td>
                  <td>{formatCurrency(w.initialBalance, w.currency)}</td>
                  <td>
                    <strong>{formatCurrency(w.currentBalance, w.currency)}</strong>
                  </td>
                  <td className="row-actions">
                    <button type="button" className="icon-btn" onClick={() => startEdit(w)} aria-label="Sửa">
                      <EditIcon />
                    </button>
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
