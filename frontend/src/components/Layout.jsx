import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../hooks/useAuth";
import { useUnreadNotifications } from "../hooks/useUnreadNotifications";
import FamilySwitcher from "./FamilySwitcher";
import LanguageSwitcher from "./LanguageSwitcher";
import {
  BellIcon,
  CloseIcon,
  GridIcon,
  LogoutIcon,
  MenuIcon,
  PieChartIcon,
  ReceiptIcon,
  RepeatIcon,
  ShieldIcon,
  TagIcon,
  TrashIcon,
  UserIcon,
  WalletIcon,
} from "./AppIcons";

export default function Layout() {
  const { t } = useTranslation("layout");
  const { logout, displayName, isSystemAdmin } = useAuth();
  const unreadCount = useUnreadNotifications();
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const links = [
    { to: "/", label: t("nav.dashboard"), icon: GridIcon },
    { to: "/wallets", label: t("nav.wallets"), icon: WalletIcon },
    { to: "/categories", label: t("nav.categories"), icon: TagIcon },
    { to: "/transactions", label: t("nav.transactions"), icon: ReceiptIcon },
    { to: "/recurring-transactions", label: t("nav.recurringTransactions"), icon: RepeatIcon },
    { to: "/budgets", label: t("nav.budgets"), icon: PieChartIcon },
    { to: "/notifications", label: t("nav.notifications"), icon: BellIcon },
    { to: "/trash", label: t("nav.trash"), icon: TrashIcon },
    { to: "/profile", label: t("nav.profile"), icon: UserIcon },
  ];
  const visibleLinks = isSystemAdmin
    ? [...links, { to: "/admin", label: t("nav.admin"), icon: ShieldIcon }]
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
          aria-label={t("openMenu")}
        >
          <MenuIcon />
        </button>
        <div className="brand">
          <span className="brand-icon">💰</span>
          <span>{t("brand")}</span>
        </div>
      </header>

      {sidebarOpen && <div className="sidebar-backdrop" onClick={() => setSidebarOpen(false)} />}

      <nav className={`sidebar ${sidebarOpen ? "is-open" : ""}`}>
        <div className="sidebar-header">
          <div className="brand">
            <span className="brand-icon">💰</span>
            <span>{t("brand")}</span>
          </div>
          <button
            type="button"
            className="sidebar-close-btn"
            onClick={() => setSidebarOpen(false)}
            aria-label={t("closeMenu")}
          >
            <CloseIcon />
          </button>
        </div>
        <FamilySwitcher />
        <LanguageSwitcher />
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
          {t("logout")}
        </button>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
