import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { maxDateTime } from "../utils/dateLimits";
import { confirmDialog } from "../utils/confirm";
import { formatCurrency } from "../utils/format";
import { LIMITS } from "../utils/inputLimits";
import { useCleanText } from "../utils/textQuality";
import AmountInput from "./AmountInput";
import Modal from "./Modal";
import { Button } from "./ui/Button";
import { Field } from "./ui/Field";
import { Input } from "./ui/Input";

function nowForDateTimeInput() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

/**
 * README C4 "Hoàn tiền / trả hàng": money that came back for an expense. Recorded as a negative expense in the same
 * category and wallet, so the category's spending and the budgets go down by it — income is untouched.
 */
export default function RefundModal({ transaction, onClose, onDone }) {
  const { t } = useTranslation(["transactions", "common"]);
  const cleanText = useCleanText();
  const [amount, setAmount] = useState("");
  const [occurredAt, setOccurredAt] = useState(nowForDateTimeInput);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    setAmount(transaction ? String(transaction.amount) : "");
    setOccurredAt(nowForDateTimeInput());
    setNote("");
    setError("");
  }, [transaction]);

  if (!transaction) return null;

  async function handleSubmit(e) {
    e.preventDefault();
    if (!(await confirmDialog(t("refundConfirm", { amount: formatCurrency(Number(amount)) }), { tone: "primary", icon: "question" }))) return;
    setError("");
    try {
      await client.post(`/expenses/transactions/${transaction.id}/refunds`, {
        amount: Number(amount),
        occurredAt,
        note: note || null,
      });
      toast.success(t("refundSaved"));
      onDone();
    } catch (err) {
      setError(err.response?.data?.message || t("refundFailed"));
    }
  }

  return (
    <Modal open onClose={onClose} title={t("refundTitle")} subtitle={t("refundSubtitle", { amount: formatCurrency(transaction.amount) })}>
      <form className="space-y-3" onSubmit={handleSubmit}>
        <Field>
          <span>
            {t("refundAmountLabel")}
            <span className="required-mark" aria-hidden="true"> *</span>
          </span>
          <AmountInput value={amount} onChange={setAmount} required positive max={Number(transaction.amount)} />
        </Field>
        <Field>
          <span>
            {t("timeLabel")}
            <span className="required-mark" aria-hidden="true"> *</span>
          </span>
          <Input type="datetime-local" value={occurredAt} min={transaction.occurredAt.slice(0, 16)} max={maxDateTime()}
            onChange={(e) => setOccurredAt(e.target.value)} required />
        </Field>
        <Field>
          {t("noteLabel")}
          <Input value={note} validate={cleanText} maxLength={LIMITS.transactionNote} placeholder={t("refundNotePlaceholder")}
            onChange={(e) => setNote(e.target.value)} />
        </Field>
        {error && <p className="error-text">{error}</p>}
        <div className="flex gap-2">
          <Button type="submit">{t("refundSubmit")}</Button>
          <Button variant="secondary" onClick={onClose}>
            {t("common:cancel")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
