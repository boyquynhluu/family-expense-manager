import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import Swal from "sweetalert2";
import client from "../api/client";
import { useAuth } from "../hooks/useAuth";

function formatDate(value) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}

export default function AdminPanel() {
  const { t } = useTranslation(["adminPanel", "common"]);
  const { userId } = useAuth();
  const [families, setFamilies] = useState([]);
  const [users, setUsers] = useState([]);
  const [error, setError] = useState("");

  function load() {
    client
      .get("/admin/families")
      .then((res) => setFamilies(res.data.data))
      .catch((err) => setError(err.response?.data?.message || t("loadFamiliesFailed")));
    client
      .get("/admin/users")
      .then((res) => setUsers(res.data.data))
      .catch((err) => setError(err.response?.data?.message || t("loadUsersFailed")));
  }

  useEffect(load, []);

  async function toggleSystemAdmin(user) {
    const nextValue = !user.isSystemAdmin;
    const confirmMessage = nextValue
      ? t("grantAdminConfirm", { email: user.email })
      : t("revokeAdminConfirm", { email: user.email });
    const result = await confirmUpdate(confirmMessage);
    if(!result.isConfirmed) return;
    try {
      await client.put(`/admin/users/${user.id}/system-admin`, { isSystemAdmin: nextValue });
      toast.success(t("updateAdminSuccess"));
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || t("updateAdminFailed"));
    }
  }

  async function confirmUpdate(confirmMessage) {
    return Swal.fire({
        title: t("swalTitle"),
        text: confirmMessage,
        icon: "warning",
        showCancelButton: true,

        confirmButtonText: t("swalConfirmButtonText"),
        cancelButtonText: t("swalCancelButtonText"),

        customClass: {
            popup: "custom-swal-popup",
            title: "custom-swal-title",
            htmlContainer: "custom-swal-text",
            confirmButton: "custom-swal-confirm",
            cancelButton: "custom-swal-cancel",
            icon: "custom-swal-icon",
        },

        buttonsStyling: false,
    });
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("title")}</h1>
          <p className="page-header-subtitle">{t("subtitle")}</p>
        </div>
      </div>

      {error && <p className="error-text">{error}</p>}

      <div className="section-card">
        <h2>{t("familyListTitle", { count: families.length })}</h2>
        {families.length === 0 ? (
          <p className="empty-state">{t("noFamilies")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("familyNameHeader")}</th>
                <th>{t("ownerHeader")}</th>
                <th>{t("memberCountHeader")}</th>
                <th>{t("createdAtHeader")}</th>
              </tr>
            </thead>
            <tbody>
              {families.map((f) => (
                <tr key={f.id}>
                  <td data-label={t("familyNameHeader")}>{f.name}</td>
                  <td data-label={t("ownerHeader")}>
                    {f.ownerDisplayName} ({f.ownerEmail})
                  </td>
                  <td data-label={t("memberCountHeader")}>{f.memberCount}</td>
                  <td data-label={t("createdAtHeader")}>{formatDate(f.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="section-card">
        <h2>{t("userListTitle", { count: users.length })}</h2>
        {users.length === 0 ? (
          <p className="empty-state">{t("noUsers")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("emailHeader")}</th>
                <th>{t("displayNameHeader")}</th>
                <th>{t("familyHeader")}</th>
                <th>{t("roleHeader")}</th>
                <th>{t("statusHeader")}</th>
                <th>{t("systemAdminHeader")}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td data-label={t("emailHeader")}>{u.email}</td>
                  <td data-label={t("displayNameHeader")}>{u.displayName}</td>
                  <td data-label={t("familyHeader")}>{u.familyName ?? `#${u.familyId}`}</td>
                  <td data-label={t("roleHeader")}>{u.role}</td>
                  <td data-label={t("statusHeader")}>
                    <span className={`badge ${u.active ? "badge-income" : "badge-expense"}`}>
                      {u.active ? t("activated") : t("notActivated")}
                    </span>
                  </td>
                  <td data-label={t("systemAdminHeader")}>{u.isSystemAdmin ? t("common:yes") : t("common:no")}</td>
                  <td className="row-actions">
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => toggleSystemAdmin(u)}
                      disabled={String(u.id) === String(userId) && u.isSystemAdmin}
                    >
                      {u.isSystemAdmin ? t("revokeAdminButton") : t("grantAdminButton")}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
