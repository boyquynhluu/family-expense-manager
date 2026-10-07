import { Suspense, lazy } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { Toaster } from "react-hot-toast";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";
import Layout from "./components/Layout";
import Login from "./pages/Login";

// Every other page is its own chunk, loaded the first time it is opened — bundled together they made a single
// ~1.2 MB script the browser had to download and parse before showing anything (recharts alone is a big part).
const Register = lazy(() => import("./pages/Register"));
const Verify = lazy(() => import("./pages/Verify"));
const ForgotPassword = lazy(() => import("./pages/ForgotPassword"));
const ResetPassword = lazy(() => import("./pages/ResetPassword"));
const AcceptInvite = lazy(() => import("./pages/AcceptInvite"));
const OAuth2Callback = lazy(() => import("./pages/OAuth2Callback"));
const Dashboard = lazy(() => import("./pages/Dashboard"));
const Wallets = lazy(() => import("./pages/Wallets"));
const Categories = lazy(() => import("./pages/Categories"));
const Transactions = lazy(() => import("./pages/Transactions"));
const RecurringTransactions = lazy(() => import("./pages/RecurringTransactions"));
const Budgets = lazy(() => import("./pages/Budgets"));
const Goals = lazy(() => import("./pages/Goals"));
const Guide = lazy(() => import("./pages/Guide"));
const Loans = lazy(() => import("./pages/Loans"));
const Reports = lazy(() => import("./pages/Reports"));
const Notifications = lazy(() => import("./pages/Notifications"));
const Profile = lazy(() => import("./pages/Profile"));
const AdminPanel = lazy(() => import("./pages/AdminPanel"));
const Trash = lazy(() => import("./pages/Trash"));

function PageLoader() {
  return (
    <div className="flex justify-center py-16">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-gray-300 border-t-transparent" />
    </div>
  );
}

export default function App() {
  return (
    // v7_startTransition: navigating to a page whose chunk is not loaded yet keeps the current page (and the
    // sidebar) on screen until it arrives, instead of flashing the Suspense spinner over the whole layout.
    <BrowserRouter basename={import.meta.env.BASE_URL} future={{ v7_startTransition: true }}>
      <Toaster position="top-center" />
      <AuthProvider>
        <Suspense fallback={<PageLoader />}>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/verify" element={<Verify />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />
            <Route path="/reset-password" element={<ResetPassword />} />
            <Route path="/accept-invite" element={<AcceptInvite />} />
            <Route path="/oauth2/callback" element={<OAuth2Callback />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<Layout />}>
                <Route path="/" element={<Dashboard />} />
                <Route path="/wallets" element={<Wallets />} />
                <Route path="/categories" element={<Categories />} />
                <Route path="/transactions" element={<Transactions />} />
                <Route path="/recurring-transactions" element={<RecurringTransactions />} />
                <Route path="/budgets" element={<Budgets />} />
                <Route path="/loans" element={<Loans />} />
                <Route path="/goals" element={<Goals />} />
                <Route path="/guide" element={<Guide />} />
                <Route path="/reports" element={<Reports />} />
                <Route path="/notifications" element={<Notifications />} />
                <Route path="/profile" element={<Profile />} />
                <Route path="/trash" element={<Trash />} />
                <Route path="/admin" element={<AdminPanel />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}
