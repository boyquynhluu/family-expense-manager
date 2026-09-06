import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

const links = [
  { to: "/", label: "Dashboard" },
  { to: "/wallets", label: "Ví" },
  { to: "/categories", label: "Danh mục" },
  { to: "/transactions", label: "Giao dịch" },
  { to: "/budgets", label: "Ngân sách" },
  { to: "/notifications", label: "Thông báo" },
];

export default function Layout() {
  const { logout } = useAuth();

  return (
    <div className="app-shell">
      <nav className="sidebar">
        <div className="brand">Family Expense</div>
        <ul>
          {links.map((link) => (
            <li key={link.to}>
              <NavLink to={link.to} end={link.to === "/"}>
                {link.label}
              </NavLink>
            </li>
          ))}
        </ul>
        <button type="button" className="logout-btn" onClick={logout}>
          Đăng xuất
        </button>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
