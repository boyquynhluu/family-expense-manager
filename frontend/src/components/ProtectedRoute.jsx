import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { loginUrl } from "../utils/authRedirect";

export default function ProtectedRoute() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  // Opening e.g. /loans while logged out: log in first, then land back on /loans.
  return isAuthenticated ? (
    <Outlet />
  ) : (
    <Navigate to={loginUrl(location.pathname + location.search + location.hash)} replace />
  );
}
