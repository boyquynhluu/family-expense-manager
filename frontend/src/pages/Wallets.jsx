import { useEffect, useState } from "react";
import client from "../api/client";

export default function Wallets() {
  const [wallets, setWallets] = useState([]);
  const [name, setName] = useState("");
  const [currency, setCurrency] = useState("VND");
  const [initialBalance, setInitialBalance] = useState("0");
  const [error, setError] = useState("");

  function load() {
    client.get("/expenses/wallets").then((res) => setWallets(res.data.data));
  }

  useEffect(load, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      await client.post("/expenses/wallets", {
        name,
        currency,
        initialBalance: Number(initialBalance),
      });
      setName("");
      setInitialBalance("0");
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Tạo ví thất bại");
    }
  }

  return (
    <div>
      <h1>Ví</h1>
      <form className="inline-form" onSubmit={handleSubmit}>
        <input placeholder="Tên ví" value={name} onChange={(e) => setName(e.target.value)} required />
        <input
          placeholder="Tiền tệ (VD: VND)"
          value={currency}
          onChange={(e) => setCurrency(e.target.value.toUpperCase())}
          maxLength={3}
          required
        />
        <input
          type="number"
          step="0.01"
          placeholder="Số dư ban đầu"
          value={initialBalance}
          onChange={(e) => setInitialBalance(e.target.value)}
          required
        />
        <button type="submit">Thêm ví</button>
      </form>
      {error && <p className="error-text">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Tên</th>
            <th>Tiền tệ</th>
            <th>Số dư ban đầu</th>
          </tr>
        </thead>
        <tbody>
          {wallets.map((w) => (
            <tr key={w.id}>
              <td>{w.name}</td>
              <td>{w.currency}</td>
              <td>{w.initialBalance}</td>
            </tr>
          ))}
          {wallets.length === 0 && (
            <tr>
              <td colSpan={3}>Chưa có ví nào</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
