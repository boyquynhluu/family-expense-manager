import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { useUnreadNotifications } from "../hooks/useUnreadNotifications";
import {
  BellIcon,
  CloseIcon,
  GridIcon,
  LogoutIcon,
  MenuIcon,
  PieChartIcon,
  ReceiptIcon,
  ShieldIcon,
  TagIcon,
  UserIcon,
  WalletIcon,
} from "./AppIcons";

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
  const { logout, displayName, isSystemAdmin } = useAuth();
  const unreadCount = useUnreadNotifications();
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const visibleLinks = isSystemAdmin
    ? [...links, { to: "/admin", label: "Quản trị hệ thống", icon: ShieldIcon }]
    : links;

  // Close the drawer whenever the route changes (e.g. after tapping a nav link on mobile).
  useEffect(() => {
    setSidebarOpen(false);
  }, [location.pathname]);

  return (
    <div className="app-shell">
      <header className="mobile-topbar">
        <button
          type="button"
          className="mobile-menu-btn"
          onClick={() => setSidebarOpen(true)}
          aria-label="Mở menu"
        >
          <MenuIcon />
        </button>
        <div className="brand">
          <span className="brand-icon">💰</span>
          <span>Family Expense</span>
        </div>
      </header>

      {sidebarOpen && <div className="sidebar-backdrop" onClick={() => setSidebarOpen(false)} />}

      <nav className={`sidebar ${sidebarOpen ? "is-open" : ""}`}>
        <div className="sidebar-header">
          <div className="brand">
            <span className="brand-icon">💰</span>
            <span>Family Expense</span>
          </div>
          <button
            type="button"
            className="sidebar-close-btn"
            onClick={() => setSidebarOpen(false)}
            aria-label="Đóng menu"
          >
            <CloseIcon />
          </button>
        </div>
        <ul>
          {visibleLinks.map((link) => {
            const Icon = link.icon;
            return (
              <li key={link.to}>
                <NavLink to={link.to} end={link.to === "/"}>
                  <Icon />
                  {link.label}
                  {link.to === "/notifications" && unreadCount > 0 && (
                    <span className="nav-badge">{unreadCount > 99 ? "99+" : unreadCount}</span>
                  )}
                </NavLink>
              </li>
            );
          })}
        </ul>
        {displayName && (
          <div className="sidebar-user">
            <UserIcon />
            <span>{displayName}</span>
          </div>
        )}
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
