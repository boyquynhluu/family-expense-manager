import { useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";

/**
 * Onboarding shortcut for empty states: asks the backend to create a default wallet and
 * default categories (each only if the family has none yet), then lets the page reload.
 */
export default function SeedDefaultsButton({ onDone }) {
  const { t } = useTranslation("common");
  const [loading, setLoading] = useState(false);

  async function handleClick() {
    setLoading(true);
    try {
      const res = await client.post("/expenses/onboarding/seed-defaults");
      const { categoriesCreated, walletsCreated } = res.data.data;
      toast.success(
        categoriesCreated + walletsCreated > 0 ? t("seedDefaultsSuccess") : t("seedDefaultsNothingToDo"),
      );
      onDone?.();
    } catch (err) {
      toast.error(err.response?.data?.message || t("seedDefaultsFailed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <button type="button" onClick={handleClick} disabled={loading}>
      {loading ? t("saving") : t("seedDefaultsButton")}
    </button>
  );
}
