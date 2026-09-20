import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { useAuth } from "../hooks/useAuth";

/**
 * Only renders once an account belongs to more than one family (README "6. 1 tài
 * khoản chỉ thuộc đúng 1 gia đình") — the common single-family case stays exactly as
 * before, no extra UI clutter.
 */
export default function FamilySwitcher() {
  const { t } = useTranslation("familySwitcher");
  const { familyId, switchFamily } = useAuth();
  const [families, setFamilies] = useState([]);

  useEffect(() => {
    client.get("/auth/my-families").then((res) => setFamilies(res.data.data));
  }, [familyId]);

  if (families.length <= 1) {
    return null;
  }

  async function handleChange(e) {
    const targetFamilyId = Number(e.target.value);
    if (targetFamilyId === familyId) return;
    try {
      await switchFamily(targetFamilyId);
      // Every page's already-loaded data was fetched under the old family — a full
      // reload is the simplest way to make sure all of it gets refetched correctly.
      window.location.href = "/";
    } catch (err) {
      toast.error(err.response?.data?.message || t("switchFailed"));
    }
  }

  return (
    <label className="family-switcher">
      {t("label")}
      <select value={familyId ?? ""} onChange={handleChange}>
        {families.map((f) => (
          <option key={f.familyId} value={f.familyId}>
            {f.familyName}
            {f.role === "OWNER" ? ` (${t("owner")})` : ""}
          </option>
        ))}
      </select>
    </label>
  );
}
