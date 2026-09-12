import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import client from "../api/client";
import { EyeIcon, EyeOffIcon, KeyIcon } from "../components/AuthIcons";

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token");
  const [newPassword, setNewPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState(token ? "" : "Thiếu token đặt lại mật khẩu.");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await client.post("/auth/reset-password", { token, newPassword });
      navigate("/login", { replace: true, state: { passwordResetSuccess: true } });
    } catch (err) {
      setError(err.response?.data?.message || "Đặt lại mật khẩu thất bại. Token có thể đã hết hạn.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1 className="auth-title">Đặt lại mật khẩu</h1>

        <div className="auth-card">
          <p className="auth-card-title">Tạo mật khẩu mới</p>
          <p className="auth-card-subtitle">Nhập mật khẩu mới cho tài khoản của bạn.</p>

          {error && <p className="error-text">{error}</p>}

          {token && (
            <>
              <div className="auth-input-group">
                <span className="auth-input-icon">
                  <KeyIcon />
                </span>
                <input
                  type={showPassword ? "text" : "password"}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="Mật khẩu mới (tối thiểu 8 ký tự)"
                  aria-label="Mật khẩu mới"
                  minLength={8}
                  required
                />
                <button
                  type="button"
                  className="auth-input-toggle"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                >
                  {showPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              </div>

              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? "Đang lưu..." : "Đặt lại mật khẩu"}
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
