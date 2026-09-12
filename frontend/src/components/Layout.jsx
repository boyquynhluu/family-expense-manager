import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { BellIcon, GridIcon, LogoutIcon, PieChartIcon, ReceiptIcon, TagIcon, UserIcon, WalletIcon } from "./AppIcons";

const links = [
  { to: "/", label: "Dashboard", icon: GridIcon },
  { to: "/wallets", label: "Ví", icon: WalletIcon },
  { to: "/categories", label: "Danh mục", icon: TagIcon },
  { to: "/transactions", label: "Giao dịch", icon: ReceiptIcon },
  { to: "/budgets", label: "Ngân sách", icon: PieChartIcon },
  { to: "/notifications", label: "Thông báo", icon: BellIcon },
  { to: "/profile", label: "Hồ sơ", icon: UserIcon },
];

export default function Layout() {
  const { logout } = useAuth();

  return (
    <div className="app-shell">
      <nav className="sidebar">
        <div className="brand">
          <span className="brand-icon">💰</span>
          <span>Family Expense</span>
        </div>
        <ul>
          {links.map((link) => {
            const Icon = link.icon;
            return (
              <li key={link.to}>
                <NavLink to={link.to} end={link.to === "/"}>
                  <Icon />
                  {link.label}
                </NavLink>
              </li>
            );
          })}
        </ul>
        <button type="button" className="logout-btn" onClick={logout}>
          <LogoutIcon />
          Đăng xuất
        </button>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
