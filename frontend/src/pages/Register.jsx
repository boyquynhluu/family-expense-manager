import { useState } from "react";
import toast from "react-hot-toast";
import { Link, useNavigate } from "react-router-dom";
import { EyeIcon, EyeOffIcon, HomeIcon, KeyIcon, MailIcon, UserIcon } from "../components/AuthIcons";
import { useAuth } from "../hooks/useAuth";

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [familyName, setFamilyName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const message = await register(familyName, email, password, displayName);
      toast.success(message || "Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.", {
        duration: 6000,
      });
      navigate("/login");
    } catch (err) {
      setError(err.response?.data?.message || "Đăng ký thất bại");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1 className="auth-title">Đăng ký tài khoản</h1>

        <div className="auth-card">
          <p className="auth-card-title">Chào mừng!</p>
          <p className="auth-card-subtitle">Chỉ cần vài thông tin để tạo tài khoản chủ hộ cho gia đình bạn.</p>

          {error && <p className="error-text">{error}</p>}

          <div className="auth-input-group">
            <span className="auth-input-icon">
              <HomeIcon />
            </span>
            <input
              value={familyName}
              onChange={(e) => setFamilyName(e.target.value)}
              placeholder="Tên gia đình"
              aria-label="Tên gia đình"
              required
            />
          </div>

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

          <label className="auth-terms">
            <input type="checkbox" required />
            Tôi đồng ý với Điều khoản sử dụng
          </label>

          <button type="submit" className="auth-submit" disabled={loading}>
            {loading ? "Đang đăng ký..." : "Đăng ký"}
          </button>
        </div>

        <p className="auth-footer-text">
          Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
        </p>
      </form>
    </div>
  );
}
