import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { oauth2AuthorizationUrl } from "../api/client";
import { EyeIcon, EyeOffIcon, FacebookIcon, GithubIcon, GoogleIcon, KeyIcon, MailIcon } from "../components/AuthIcons";
import { useAuth } from "../hooks/useAuth";

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState(location.state?.oauth2Error ? "Đăng nhập bằng mạng xã hội thất bại" : "");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await login(email, password, rememberMe);
      navigate("/");
    } catch (err) {
      setError(err.response?.data?.message || "Đăng nhập thất bại");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1 className="auth-title">Đăng nhập tài khoản</h1>

        <div className="auth-card">
          <p className="auth-card-title">Chào mừng trở lại!</p>
          <p className="auth-card-subtitle">Đăng nhập để tiếp tục quản lý chi tiêu của gia đình bạn.</p>

          {error && <p className="error-text">{error}</p>}

          <div className="auth-input-group">
            <span className="auth-input-icon">
              <MailIcon />
            </span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="Email@vidu.com"
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
              placeholder="Mật khẩu"
              aria-label="Mật khẩu"
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

          <div className="auth-options">
            <label className="auth-remember">
              <span className="auth-toggle">
                <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} />
                <span className="auth-toggle-track" />
              </span>
              Ghi nhớ đăng nhập
            </label>
            <button type="button" className="auth-forgot" title="Tính năng đang phát triển">
              Quên mật khẩu?
            </button>
          </div>

          <button type="submit" className="auth-submit" disabled={loading}>
            {loading ? "Đang đăng nhập..." : "Đăng nhập"}
          </button>
        </div>

        <div className="auth-divider">
          <span>hoặc tiếp tục với</span>
        </div>

        <div className="oauth2-row">
          <a
            className="oauth2-icon-button"
            href={oauth2AuthorizationUrl("google")}
            title="Đăng nhập với Google"
          >
            <span className="oauth2-icon-badge oauth2-badge-google">
              <GoogleIcon />
            </span>
            <span>Google</span>
          </a>
          <span className="oauth2-icon-button is-disabled" title="Facebook đăng nhập sắp ra mắt">
            <span className="oauth2-icon-badge oauth2-badge-facebook">
              <FacebookIcon />
            </span>
            <span>Facebook</span>
          </span>
          <span className="oauth2-icon-button is-disabled" title="GitHub đăng nhập sắp ra mắt">
            <span className="oauth2-icon-badge oauth2-badge-github">
              <GithubIcon />
            </span>
            <span>GitHub</span>
          </span>
        </div>

        <p className="auth-footer-text">
          Chưa có tài khoản? <Link to="/register">Đăng ký gia đình mới</Link>
        </p>
      </form>
    </div>
  );
}
