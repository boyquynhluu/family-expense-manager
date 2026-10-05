import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency } from "../utils/format";
import AmountInput from "./AmountInput";
import { Button } from "./ui/Button";
import { Field } from "./ui/Field";
import { Input } from "./ui/Input";
import { Table, TBody, Td, Th, THead } from "./ui/Table";

function previousYearMonth() {
  const date = new Date();
  date.setDate(1);
  date.setMonth(date.getMonth() - 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

/**
 * OWNER only. README A5: the approval threshold and each member's daily / monthly spending caps (meant for a CHILD,
 * possible for anyone); README C7: send a past month's summary now instead of waiting for the 1st.
 */
export default function FamilyControls({ members }) {
  const { t } = useTranslation(["profile", "common"]);
  const [threshold, setThreshold] = useState("");
  const [savedThreshold, setSavedThreshold] = useState(null);
  // What is being saved right now ("threshold" or a member id) — its buttons are disabled until the call returns.
  const [busy, setBusy] = useState(null);
  const [limits, setLimits] = useState({});
  const [drafts, setDrafts] = useState({});
  const [summaryMonth, setSummaryMonth] = useState(previousYearMonth);

  function loadLimits() {
    client.get("/expenses/spending-limits").then((res) => {
      const byUser = {};
      res.data.data.forEach((l) => {
        byUser[l.userId] = l;
      });
      setLimits(byUser);
    });
  }

  useEffect(() => {
    client.get("/expenses/family-settings").then((res) => {
      const value = res.data.data.approvalThreshold;
      setThreshold(value == null ? "" : String(value));
      setSavedThreshold(value);
    });
    loadLimits();
  }, []);

  async function putThreshold(value) {
    setBusy("threshold");
    try {
      await client.put("/expenses/family-settings", { approvalThreshold: value });
      setThreshold(value == null ? "" : String(value));
      setSavedThreshold(value);
      toast.success(t(value == null ? "profile:thresholdOff" : "profile:thresholdSaved"));
    } catch (err) {
      toast.error(err.response?.data?.message || t("profile:controlsSaveFailed"));
    } finally {
      setBusy(null);
    }
  }

  // Save is only enabled when the field holds something new — after a save it greys out until the value is changed.
  const thresholdChanged = threshold !== "" && (savedThreshold == null || Number(threshold) !== Number(savedThreshold));

  async function saveThreshold(e) {
    e.preventDefault();
    const question = t("profile:thresholdConfirm", { amount: formatCurrency(Number(threshold)) });
    if (!(await confirmDialog(question, { tone: "primary", icon: "question" }))) return;
    await putThreshold(Number(threshold));
  }

  async function turnOffThreshold() {
    if (!(await confirmDialog(t("profile:thresholdOffConfirm"), { tone: "primary", icon: "question" }))) return;
    await putThreshold(null);
  }

  function draftOf(userId) {
    const saved = limits[userId];
    return drafts[userId] ?? {
      daily: saved?.dailyLimit == null ? "" : String(saved.dailyLimit),
      monthly: saved?.monthlyLimit == null ? "" : String(saved.monthlyLimit),
    };
  }

  function updateDraft(userId, field, value) {
    setDrafts((d) => ({ ...d, [userId]: { ...draftOf(userId), [field]: value } }));
  }

  function isValidLimit(value) {
    return value === "" || Number(value) > 0;
  }

  function canSaveLimit(draft) {
    return (draft.daily !== "" || draft.monthly !== "") && isValidLimit(draft.daily) && isValidLimit(draft.monthly);
  }

  function limitChanged(draft, saved) {
    const same = (value, savedValue) => (value === "" ? savedValue == null : Number(value) === Number(savedValue));
    return !same(draft.daily, saved?.dailyLimit) || !same(draft.monthly, saved?.monthlyLimit);
  }

  async function saveLimit(member) {
    const draft = draftOf(member.id);
    if (!canSaveLimit(draft)) {
      toast.error(t("profile:limitRequired"));
      return;
    }
    const question = t("profile:limitConfirm", {
      name: member.displayName,
      daily: draft.daily ? formatCurrency(Number(draft.daily)) : t("profile:noLimit"),
      monthly: draft.monthly ? formatCurrency(Number(draft.monthly)) : t("profile:noLimit"),
    });
    if (!(await confirmDialog(question, { tone: "primary", icon: "question" }))) return;
    await putLimit(member, Number(draft.daily) || null, Number(draft.monthly) || null, "profile:limitSaved");
  }

  async function removeLimit(member) {
    if (!(await confirmDialog(t("profile:limitRemoveConfirm", { name: member.displayName })))) return;
    await putLimit(member, null, null, "profile:limitRemoved");
  }

  async function putLimit(member, dailyLimit, monthlyLimit, successKey) {
    setBusy(member.id);
    try {
      await client.put(`/expenses/spending-limits/${member.id}`, { dailyLimit, monthlyLimit });
      toast.success(t(successKey, { name: member.displayName }));
      setDrafts((d) => {
        const next = { ...d };
        delete next[member.id];
        return next;
      });
      loadLimits();
    } catch (err) {
      toast.error(err.response?.data?.message || t("profile:controlsSaveFailed"));
    } finally {
      setBusy(null);
    }
  }

  async function sendSummary(e) {
    e.preventDefault();
    if (!(await confirmDialog(t("profile:summaryConfirm", { month: summaryMonth }), { tone: "primary", icon: "question" }))) return;
    try {
      await client.post("/expenses/monthly-summary/send", null, { params: { yearMonth: summaryMonth } });
      toast.success(t("profile:summarySent"));
    } catch (err) {
      toast.error(err.response?.data?.message || t("profile:controlsSaveFailed"));
    }
  }

  const others = members.filter((m) => m.role !== "OWNER");

  return (
    <div className="section-card">
      <h2>{t("profile:controlsTitle")}</h2>
      <p className="page-header-subtitle">{t("profile:controlsHint")}</p>

      <h3>{t("profile:thresholdTitle")}</h3>
      <form className="inline-form" onSubmit={saveThreshold}>
        <Field>
          <span>
            {t("profile:thresholdLabel")}
            <span className="required-mark" aria-hidden="true"> *</span>
          </span>
          <AmountInput value={threshold} onChange={setThreshold} required positive />
        </Field>
        <Button type="submit" disabled={!thresholdChanged || busy === "threshold"}>
          {t("common:save")}
        </Button>
        {savedThreshold != null && (
          <Button variant="secondary" onClick={turnOffThreshold} disabled={busy === "threshold"}>
            {t("profile:thresholdTurnOff")}
          </Button>
        )}
      </form>
      <p className="page-header-subtitle">
        {savedThreshold == null
          ? t("profile:thresholdCurrentNone")
          : t("profile:thresholdCurrent", { amount: formatCurrency(savedThreshold) })}
      </p>

      <h3 className="mt-4">{t("profile:limitsTitle")}</h3>
      <p className="page-header-subtitle">{t("profile:limitsHint")}</p>
      {others.length === 0 ? (
        <p className="empty-state">{t("profile:noOtherMembers")}</p>
      ) : (
        <Table>
          <THead>
            <tr>
              <Th>{t("profile:displayNameLabel")}</Th>
              <Th>{t("profile:dailyLimitLabel")}</Th>
              <Th>{t("profile:monthlyLimitLabel")}</Th>
              <Th>{t("profile:spentLabel")}</Th>
              <Th></Th>
            </tr>
          </THead>
          <TBody>
            {others.map((m) => {
              const draft = draftOf(m.id);
              const saved = limits[m.id];
              const hasLimit = saved && (saved.dailyLimit != null || saved.monthlyLimit != null);
              const changed = limitChanged(draft, saved);
              return (
                <tr key={m.id}>
                  <Td data-label={t("profile:displayNameLabel")}>
                    {m.displayName}
                    <div className="text-xs text-slate-500">{t(`profile:role.${m.role}`, m.role)}</div>
                  </Td>
                  <Td data-label={t("profile:dailyLimitLabel")}>
                    <AmountInput value={draft.daily} onChange={(v) => updateDraft(m.id, "daily", v)} placeholder="—" />
                  </Td>
                  <Td data-label={t("profile:monthlyLimitLabel")}>
                    <AmountInput value={draft.monthly} onChange={(v) => updateDraft(m.id, "monthly", v)} placeholder="—" />
                  </Td>
                  <Td data-label={t("profile:spentLabel")}>
                    {saved
                      ? t("profile:spentValue", { today: formatCurrency(saved.spentToday), month: formatCurrency(saved.spentThisMonth) })
                      : "—"}
                  </Td>
                  <Td actions>
                    <div className="flex gap-2 justify-end">
                      <Button size="sm" onClick={() => saveLimit(m)} disabled={!changed || !canSaveLimit(draft) || busy === m.id}
                        title={changed && !canSaveLimit(draft) ? t("profile:limitRequired") : undefined}>
                        {t("common:save")}
                      </Button>
                      {hasLimit && (
                        <Button size="sm" variant="secondary" onClick={() => removeLimit(m)} disabled={busy === m.id}>
                          {t("profile:limitRemove")}
                        </Button>
                      )}
                    </div>
                  </Td>
                </tr>
              );
            })}
          </TBody>
        </Table>
      )}

      <h3 className="mt-4">{t("profile:summaryTitle")}</h3>
      <form className="inline-form" onSubmit={sendSummary}>
        <Field>
          {t("profile:summaryMonthLabel")}
          <Input type="month" value={summaryMonth} max={previousYearMonth()} onChange={(e) => setSummaryMonth(e.target.value)} required />
        </Field>
        <Button type="submit" variant="secondary">
          {t("profile:summarySend")}
        </Button>
      </form>
    </div>
  );
}
