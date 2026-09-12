import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import client from "../api/client";
import { EyeIcon, EyeOffIcon, KeyIcon, UserIcon } from "../components/AuthIcons";

export default function AcceptInvite() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token");

  const [invite, setInvite] = useState(null);
  const [loadError, setLoadError] = useState(token ? "" : "Thiếu token lời mời.");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!token) return;
    client
      .get(`/auth/invite/${token}`)
      .then((res) => setInvite(res.data.data))
      .catch((err) => setLoadError(err.response?.data?.message || "Lời mời không hợp lệ hoặc đã hết hạn."));
  }, [token]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await client.post(`/auth/invite/${token}/accept`, { displayName, password });
      toast.success(res.data.data?.message || "Tham gia gia đình thành công. Bạn có thể đăng nhập.", {
        duration: 6000,
      });
      navigate("/login", { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || "Tham gia thất bại. Lời mời có thể đã hết hạn.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1 className="auth-title">Tham gia gia đình</h1>

        <div className="auth-card">
          {invite ? (
            <>
              <p className="auth-card-title">Chào mừng!</p>
              <p className="auth-card-subtitle">
                Bạn được mời tham gia gia đình <strong>{invite.familyName}</strong> với email {invite.email}.
              </p>
            </>
          ) : (
            <p className="auth-card-subtitle">Đang kiểm tra lời mời...</p>
          )}

          {(error || loadError) && <p className="error-text">{error || loadError}</p>}

          {invite && (
            <>
              <div className="auth-input-group">
                <span className="auth-input-icon">
                  <UserIcon />
                </span>
                <input
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="Tên hiển thị của bạn"
                  aria-label="Tên hiển thị của bạn"
                  required
                />
              </div>

              <div className="auth-input-group">
                <span className="auth-input-icon">
                  <KeyIcon />
                </span>
                <input
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Mật khẩu (tối thiểu 8 ký tự)"
                  aria-label="Mật khẩu"
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
                {loading ? "Đang tham gia..." : "Tham gia gia đình"}
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
