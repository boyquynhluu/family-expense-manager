import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import toast from "react-hot-toast";
import client from "../api/client";

// Landed on from the verification link emailed after registration (see
// notification-service UserVerificationEventListener). Calls the API to activate the
// account, then routes to /login on success.
export default function Verify() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const handled = useRef(false);
  const [status, setStatus] = useState("verifying");
  const [error, setError] = useState("");

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const token = searchParams.get("token");
    if (!token) {
      setStatus("error");
      setError("Thiếu token xác thực.");
      return;
    }

    client
      .get("/auth/verify", { params: { token } })
      .then((res) => {
        toast.success(res.data.data?.message || "Xác thực email thành công.");
        navigate("/login", { replace: true });
      })
      .catch((err) => {
        setStatus("error");
        setError(err.response?.data?.message || "Xác thực email thất bại. Token có thể đã hết hạn.");
      });
  }, [searchParams, navigate]);

  return (
    <div className="auth-page">
      <div className="auth-form">
        {status === "verifying" && <p className="verify-message">Đang xác thực email...</p>}
        {status === "error" && (
          <>
            <p className="verify-message is-error">{error}</p>
            <p className="auth-footer-text">
              <Link to="/login">Quay lại đăng nhập</Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
