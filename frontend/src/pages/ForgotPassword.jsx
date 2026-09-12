import { useState } from "react";
import { Link } from "react-router-dom";
import client from "../api/client";
import { MailIcon } from "../components/AuthIcons";

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);
    try {
      const res = await client.post("/auth/forgot-password", { email });
      setMessage(res.data.data?.message || "Nếu email tồn tại, chúng tôi đã gửi link đặt lại mật khẩu.");
    } catch (err) {
      setError(err.response?.data?.message || "Có lỗi xảy ra, vui lòng thử lại");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1 className="auth-title">Quên mật khẩu</h1>

        <div className="auth-card">
          <p className="auth-card-title">Đặt lại mật khẩu</p>
          <p className="auth-card-subtitle">Nhập email đã đăng ký, chúng tôi sẽ gửi link đặt lại mật khẩu cho bạn.</p>

          {error && <p className="error-text">{error}</p>}
          {message && <p className="success-text">{message}</p>}

          {!message && (
            <>
              <div className="auth-input-group">
                <span className="auth-input-icon">
                  <MailIcon />
                </span>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Email@gmail.com"
                  aria-label="Email"
                  required
                />
              </div>

              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? "Đang gửi..." : "Gửi link đặt lại mật khẩu"}
              </button>
            </>
          )}
        </div>

        <p className="auth-footer-text">
          <Link to="/login">Quay lại đăng nhập</Link>
        </p>
      </form>
    </div>
  );
}
