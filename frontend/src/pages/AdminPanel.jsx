import { useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import Pagination from "../components/Pagination";
import { confirmDialog } from "../utils/confirm";
import { useAuth } from "../hooks/useAuth";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { usePagedList } from "../hooks/usePagedList";
import { LIMITS } from "../utils/inputLimits";

import { Table, THead, TBody, Th, Td } from "../components/ui/Table";
import { Button } from "../components/ui/Button";
import { Input } from "../components/ui/Input";
function formatDate(value) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}

export default function AdminPanel() {
  const { t } = useTranslation(["adminPanel", "common"]);
  const { userId } = useAuth();
  const [familyQuery, setFamilyQuery] = useState("");
  const [userQuery, setUserQuery] = useState("");
  const familySearch = useDebouncedValue(familyQuery.trim());
  const userSearch = useDebouncedValue(userQuery.trim());
  const { pageData: familiesPage, setPage: setFamiliesPage } = usePagedList("/admin/families", {
    q: familySearch || undefined,
  });
  const { pageData: usersPage, setPage: setUsersPage, reload: reloadUsers } = usePagedList("/admin/users", {
    q: userSearch || undefined,
  });
  const families = familiesPage.content;
  const users = usersPage.content;

  async function toggleSystemAdmin(user) {
    const nextValue = !user.isSystemAdmin;
    const confirmMessage = nextValue
      ? t("grantAdminConfirm", { email: user.email })
      : t("revokeAdminConfirm", { email: user.email });
    if (!(await confirmDialog(confirmMessage, { title: t("swalTitle"), tone: nextValue ? "primary" : "warning" }))) {
      return;
    }
    try {
      await client.put(`/admin/users/${user.id}/system-admin`, { isSystemAdmin: nextValue });
      toast.success(t("updateAdminSuccess"));
      reloadUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || t("updateAdminFailed"));
    }
  }

  async function toggleLocked(user) {
    const nextValue = !user.locked;
    const confirmMessage = nextValue
      ? t("lockConfirm", { email: user.email })
      : t("unlockConfirm", { email: user.email });
    const title = nextValue ? t("lockSwalTitle") : t("unlockSwalTitle");
    if (!(await confirmDialog(confirmMessage, { title, tone: nextValue ? "danger" : "primary" }))) return;
    try {
      await client.put(`/admin/users/${user.id}/locked`, { locked: nextValue });
      toast.success(nextValue ? t("lockSuccess") : t("unlockSuccess"));
      reloadUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || t("lockFailed"));
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("title")}</h1>
          <p className="page-header-subtitle">{t("subtitle")}</p>
        </div>
      </div>

      <div className="section-card">
        <div className="table-toolbar">
          <h2>{t("familyListTitle", { count: familiesPage.totalElements })}</h2>
          <Input
            type="search"
            className="table-search"
            value={familyQuery}
            onChange={(e) => setFamilyQuery(e.target.value)}
            placeholder={t("searchFamiliesPlaceholder")}
            aria-label={t("searchFamiliesPlaceholder")}
            maxLength={LIMITS.search}
          />
        </div>
        {families.length === 0 ? (
          <p className="empty-state">{familySearch ? t("noSearchResults") : t("noFamilies")}</p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>{t("familyNameHeader")}</Th>
                <Th>{t("ownerHeader")}</Th>
                <Th align="right">{t("memberCountHeader")}</Th>
                <Th>{t("createdAtHeader")}</Th>
              </tr>
            </THead>
            <TBody>
              {families.map((f) => (
                <tr key={f.id}>
                  <Td data-label={t("familyNameHeader")}>{f.name}</Td>
                  <Td data-label={t("ownerHeader")}>
                    {f.ownerDisplayName} ({f.ownerEmail})
                  </Td>
                  <Td data-label={t("memberCountHeader")} align="right">{f.memberCount}</Td>
                  <Td data-label={t("createdAtHeader")}>{formatDate(f.createdAt)}</Td>
                </tr>
              ))}
            </TBody>
          </Table>
        )}
        <Pagination pageData={familiesPage} onPageChange={setFamiliesPage} />
      </div>

      <div className="section-card">
        <div className="table-toolbar">
          <h2>{t("userListTitle", { count: usersPage.totalElements })}</h2>
          <Input
            type="search"
            className="table-search"
            value={userQuery}
            onChange={(e) => setUserQuery(e.target.value)}
            placeholder={t("searchUsersPlaceholder")}
            aria-label={t("searchUsersPlaceholder")}
            maxLength={LIMITS.search}
          />
        </div>
        {users.length === 0 ? (
          <p className="empty-state">{userSearch ? t("noSearchResults") : t("noUsers")}</p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>{t("emailHeader")}</Th>
                <Th>{t("displayNameHeader")}</Th>
                <Th>{t("familyHeader")}</Th>
                <Th>{t("roleHeader")}</Th>
                <Th>{t("statusHeader")}</Th>
                <Th>{t("systemAdminHeader")}</Th>
                <Th></Th>
              </tr>
            </THead>
            <TBody>
              {users.map((u) => (
                <tr key={u.id}>
                  <Td data-label={t("emailHeader")}>{u.email}</Td>
                  <Td data-label={t("displayNameHeader")}>{u.displayName}</Td>
                  <Td data-label={t("familyHeader")}>{u.familyName ?? `#${u.familyId}`}</Td>
                  <Td data-label={t("roleHeader")}>{u.role}</Td>
                  <Td data-label={t("statusHeader")}>
                    {u.locked ? (
                      <span className="badge badge-expense">{t("locked")}</span>
                    ) : (
                      <span className={`badge ${u.active ? "badge-income" : "badge-expense"}`}>
                        {u.active ? t("activated") : t("notActivated")}
                      </span>
                    )}
                  </Td>
                  <Td data-label={t("systemAdminHeader")}>{u.isSystemAdmin ? t("common:yes") : t("common:no")}</Td>
                  <Td actions>
                    <Button variant={u.isSystemAdmin ? "warning-outline" : "secondary"} size="sm"
                      onClick={() => toggleSystemAdmin(u)}
                      disabled={String(u.id) === String(userId) && u.isSystemAdmin}
                    >
                      {u.isSystemAdmin ? t("revokeAdminButton") : t("grantAdminButton")}
                    </Button>
                    <Button variant={u.locked ? "success-outline" : "danger-outline"} size="sm"
                      onClick={() => toggleLocked(u)}
                      disabled={!u.locked && (u.isSystemAdmin || String(u.id) === String(userId))}
                    >
                      {u.locked ? t("unlockButton") : t("lockButton")}
                    </Button>
                  </Td>
                </tr>
              ))}
            </TBody>
          </Table>
        )}
        <Pagination pageData={usersPage} onPageChange={setUsersPage} />
      </div>
    </div>
  );
}
