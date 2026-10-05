import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import AmountInput from "../components/AmountInput";
import { EditIcon, TrashIcon } from "../components/AppIcons";
import Modal from "../components/Modal";
import Pagination from "../components/Pagination";
import { Button, IconButton } from "../components/ui/Button";
import { Field } from "../components/ui/Field";
import { Input, Select } from "../components/ui/Input";
import { Table, TBody, Td, Th, THead } from "../components/ui/Table";
import { useAuth } from "../hooks/useAuth";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { maxDateTime } from "../utils/dateLimits";
import { formatCurrency, formatServerDateTime } from "../utils/format";
import { useCleanText } from "../utils/textQuality";
import { usableWallets } from "../utils/walletAccess";

function today() {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 10);
}

function nowForDateTimeInput() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

function emptyForm() {
  // counterpartyKind FAMILY: the other side is one of the family's wallets (money moves between the two wallets);
  // OUTSIDE: a person outside the family, by name.
  return { memberUserId: "", direction: "BORROWED", counterpartyKind: "FAMILY", counterpartyWalletId: "", counterpartyName: "", counterpartyContact: "", principal: "", walletId: "",
    startDate: today(), dueDate: "", note: "" };
}

/**
 * README B3 "Vay / cho vay / nợ": money the family borrowed or lent, repaid in instalments through wallets. It only
 * moves wallet balances — never income or expense — and the daily reminder warns 3 days before the due date.
 */
export default function Loans() {
  const { t } = useTranslation(["loans", "common"]);
  const cleanText = useCleanText();
  const { role, userId } = useAuth();
  const isOwner = role === "OWNER";
  const [status, setStatus] = useState("OPEN");
  const [memberFilter, setMemberFilter] = useState("");
  const { pageData, setPage, reload } = usePagedList("/expenses/loans", {
    status: status || undefined,
    memberUserId: memberFilter || undefined,
  });
  const [members, setMembers] = useState([]);
  const [wallets, setWallets] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState("");
  const [detail, setDetail] = useState(null);
  const [payment, setPayment] = useState({ amount: "", walletId: "", paidAt: nowForDateTimeInput(), note: "" });

  const formWallets = usableWallets(wallets, { role, userId });

  useEffect(() => {
    client.get("/expenses/wallets").then((res) => setWallets(res.data.data));
    client
      .get("/auth/family/members", { params: { page: 0, size: 100 } })
      .then((res) => setMembers(res.data.data.content))
      .catch(() => {});
  }, []);

  function memberName(id) {
    return members.find((m) => String(m.id) === String(id))?.displayName ?? t("loans:formerMember");
  }

  // The other side of a loan inside the family: any wallet except the loan's own wallet and the member's own private
  // wallets (a member can't borrow from themself). Shared wallets count.
  const loanMember = form.memberUserId || String(userId);
  const counterpartyWallets = wallets.filter(
    (w) => String(w.id) !== String(form.walletId) && String(w.ownerUserId) !== String(loanMember),
  );

  // The family member the loan belongs to: oneself, or anyone when the OWNER records it.
  const memberChoices = isOwner ? members : members.filter((m) => String(m.id) === String(userId));

  useEffect(() => {
    if (formWallets[0] && !form.walletId) setForm((f) => ({ ...f, walletId: String(formWallets[0].id) }));
  }, [formWallets, form.walletId]);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function walletName(id) {
    return wallets.find((w) => w.id === id)?.name ?? `#${id}`;
  }

  function canModify(loan) {
    return isOwner || String(loan.createdByUserId) === String(userId);
  }

  function startEdit(loan) {
    setEditingId(loan.id);
    setForm({
      memberUserId: String(loan.memberUserId),
      direction: loan.direction,
      counterpartyKind: loan.counterpartyWalletId != null ? "FAMILY" : "OUTSIDE",
      counterpartyWalletId: loan.counterpartyWalletId != null ? String(loan.counterpartyWalletId) : "",
      counterpartyName: loan.counterpartyName,
      counterpartyContact: loan.counterpartyContact ?? "",
      principal: String(loan.principal),
      walletId: String(loan.walletId),
      startDate: loan.startDate,
      dueDate: loan.dueDate ?? "",
      note: loan.note ?? "",
    });
  }

  function cancelEdit() {
    setEditingId(null);
    setForm((f) => ({ ...emptyForm(), walletId: f.walletId }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    const family = form.counterpartyKind === "FAMILY";
    const payload = {
      ...form,
      counterpartyWalletId: family ? Number(form.counterpartyWalletId) : null,
      counterpartyName: family ? null : form.counterpartyName,
      principal: Number(form.principal),
      walletId: Number(form.walletId),
      counterpartyContact: form.counterpartyContact || null,
      memberUserId: form.memberUserId ? Number(form.memberUserId) : Number(userId),
      dueDate: form.dueDate || null,
      note: form.note || null,
    };
    const question = editingId
      ? t("loans:updateConfirm")
      : t(form.direction === "BORROWED" ? "loans:borrowConfirm" : "loans:lendConfirm", {
          amount: formatCurrency(payload.principal),
          name: family ? walletName(payload.counterpartyWalletId) : form.counterpartyName,
          wallet: walletName(payload.walletId),
          member: memberName(payload.memberUserId),
        });
    if (!(await confirmDialog(question, { tone: "primary", icon: "question" }))) return;
    try {
      if (editingId) {
        await client.put(`/expenses/loans/${editingId}`, payload);
      } else {
        await client.post("/expenses/loans", payload);
      }
      toast.success(t("loans:saved"));
      cancelEdit();
      reload();
    } catch (err) {
      setError(err.response?.data?.message || t("loans:saveFailed"));
    }
  }

  async function handleDelete(loan) {
    if (!(await confirmDialog(t("loans:deleteConfirm")))) return;
    try {
      await client.delete(`/expenses/loans/${loan.id}`);
      toast.success(t("loans:deleted"));
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("loans:saveFailed"));
    }
  }

  async function openDetail(loan) {
    const res = await client.get(`/expenses/loans/${loan.id}`);
    setDetail(res.data.data);
    setPayment({ amount: String(res.data.data.remaining), walletId: String(loan.walletId), paidAt: nowForDateTimeInput(), note: "" });
  }

  async function addPayment(e) {
    e.preventDefault();
    const question = t(detail.direction === "BORROWED" ? "loans:repayConfirm" : "loans:collectConfirm", {
      amount: formatCurrency(Number(payment.amount)),
      wallet: walletName(Number(payment.walletId)),
    });
    if (!(await confirmDialog(question, { tone: "primary", icon: "question" }))) return;
    try {
      const res = await client.post(`/expenses/loans/${detail.id}/payments`, {
        amount: Number(payment.amount),
        walletId: Number(payment.walletId),
        paidAt: payment.paidAt,
        note: payment.note || null,
      });
      setDetail(res.data.data);
      toast.success(t("loans:paymentSaved"));
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("loans:saveFailed"));
    }
  }

  async function deletePayment(p) {
    if (!(await confirmDialog(t("loans:paymentDeleteConfirm")))) return;
    try {
      const res = await client.delete(`/expenses/loans/${detail.id}/payments/${p.id}`);
      setDetail(res.data.data);
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("loans:saveFailed"));
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("loans:title")}</h1>
          <p className="page-header-subtitle">{t("loans:subtitle")}</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{editingId ? t("loans:editTitle") : t("loans:addTitle")}</h2>
        <form className="inline-form" onSubmit={handleSubmit}>
          <Field>
            {t("loans:memberLabel")}
            <Select value={form.memberUserId || String(userId)} onChange={(e) => update("memberUserId", e.target.value)}
              disabled={!isOwner}>
              {memberChoices.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.displayName}
                </option>
              ))}
            </Select>
          </Field>
          <Field>
            {t("loans:directionLabel")}
            <Select value={form.direction} onChange={(e) => update("direction", e.target.value)} disabled={editingId != null}>
              <option value="BORROWED">{t("loans:direction.BORROWED")}</option>
              <option value="LENT">{t("loans:direction.LENT")}</option>
            </Select>
          </Field>
          <Field>
            {form.direction === "BORROWED" ? t("loans:lenderKindLabel") : t("loans:borrowerKindLabel")}
            <Select value={form.counterpartyKind} onChange={(e) => update("counterpartyKind", e.target.value)}
              disabled={editingId != null}>
              <option value="FAMILY">{t("loans:kindFamily")}</option>
              <option value="OUTSIDE">{t("loans:kindOutside")}</option>
            </Select>
          </Field>
          {form.counterpartyKind === "FAMILY" ? (
            <Field>
              <span>
                {form.direction === "BORROWED" ? t("loans:lenderWalletLabel") : t("loans:borrowerWalletLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <Select value={form.counterpartyWalletId} onChange={(e) => update("counterpartyWalletId", e.target.value)}
                required disabled={editingId != null}>
                <option value="">{t("loans:chooseWallet")}</option>
                {counterpartyWallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name} — {w.ownerUserId == null ? t("loans:sharedWallet") : memberName(w.ownerUserId)}
                  </option>
                ))}
              </Select>
            </Field>
          ) : (
            <>
              <Field>
                <span>
                  {form.direction === "BORROWED" ? t("loans:lenderLabel") : t("loans:borrowerLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <Input value={form.counterpartyName} validate={cleanText} maxLength={100}
                  onChange={(e) => update("counterpartyName", e.target.value)} required />
              </Field>
              <Field>
                {t("loans:contactLabel")}
                <Input value={form.counterpartyContact} maxLength={100} onChange={(e) => update("counterpartyContact", e.target.value)} />
              </Field>
            </>
          )}
          <Field>
            <span>
              {t("loans:principalLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <AmountInput value={form.principal} onChange={(v) => update("principal", v)} required positive disabled={editingId != null} />
          </Field>
          <Field>
            {form.direction === "BORROWED" ? t("loans:walletInLabel") : t("loans:walletOutLabel")}
            <Select value={form.walletId} onChange={(e) => update("walletId", e.target.value)} required disabled={editingId != null}>
              {(editingId ? wallets : formWallets).map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field>
            {t("loans:startDateLabel")}
            <Input type="date" value={form.startDate} max={today()} onChange={(e) => update("startDate", e.target.value)}
              required disabled={editingId != null} />
          </Field>
          <Field>
            {t("loans:dueDateLabel")}
            <Input type="date" value={form.dueDate} min={form.startDate} onChange={(e) => update("dueDate", e.target.value)} />
          </Field>
          <Field>
            {t("loans:noteLabel")}
            <Input value={form.note} validate={cleanText} maxLength={255} onChange={(e) => update("note", e.target.value)} />
          </Field>
          <Button type="submit">{editingId ? t("common:save") : t("loans:addButton")}</Button>
          {editingId && (
            <Button variant="secondary" onClick={cancelEdit}>
              {t("common:cancel")}
            </Button>
          )}
        </form>
        {form.counterpartyKind === "FAMILY" && <p className="page-header-subtitle">{t("loans:familyHint")}</p>}
        {error && <p className="error-text">{error}</p>}
      </div>

      <div className="section-card">
        <div className="page-header">
          <h2>{t("loans:listTitle")}</h2>
          <div className="flex gap-2">
          <Select value={memberFilter} onChange={(e) => setMemberFilter(e.target.value)}>
            <option value="">{t("loans:allMembers")}</option>
            {members.map((m) => (
              <option key={m.id} value={m.id}>
                {m.displayName}
              </option>
            ))}
          </Select>
          <Select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="OPEN">{t("loans:status.OPEN")}</option>
            <option value="CLOSED">{t("loans:status.CLOSED")}</option>
            <option value="">{t("loans:statusAll")}</option>
          </Select>
          </div>
        </div>
        {pageData.content.length === 0 ? (
          <p className="empty-state">{t("loans:empty")}</p>
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>{t("loans:memberLabel")}</Th>
                <Th>{t("loans:directionLabel")}</Th>
                <Th>{t("loans:counterpartyLabel")}</Th>
                <Th align="right">{t("loans:principalLabel")}</Th>
                <Th align="right">{t("loans:remainingLabel")}</Th>
                <Th>{t("loans:dueDateLabel")}</Th>
                <Th></Th>
              </tr>
            </THead>
            <TBody>
              {pageData.content.map((l) => (
                <tr key={l.id}>
                  <Td data-label={t("loans:memberLabel")}>
                    <strong>{memberName(l.memberUserId)}</strong>
                  </Td>
                  <Td data-label={t("loans:directionLabel")}>
                    <span className={`badge ${l.direction === "BORROWED" ? "badge-expense" : "badge-income"}`}>
                      {t(`loans:direction.${l.direction}`)}
                    </span>
                  </Td>
                  <Td data-label={t("loans:counterpartyLabel")}>
                    <span className="text-xs text-slate-500">
                      {l.direction === "BORROWED" ? t("loans:lenderLabel") : t("loans:borrowerLabel")}:
                    </span>{" "}
                    {l.counterpartyName}
                    {l.counterpartyWalletId != null && <span className="badge badge-neutral ml-1">{t("loans:kindFamily")}</span>}
                    {l.counterpartyContact && <div className="text-xs text-slate-500">{l.counterpartyContact}</div>}
                  </Td>
                  <Td data-label={t("loans:principalLabel")} align="right">{formatCurrency(l.principal)}</Td>
                  <Td data-label={t("loans:remainingLabel")} align="right">
                    <strong>{formatCurrency(l.remaining)}</strong>
                    {l.status === "CLOSED" && <span className="badge badge-income ml-1">{t("loans:status.CLOSED")}</span>}
                  </Td>
                  <Td data-label={t("loans:dueDateLabel")}>{l.dueDate || "-"}</Td>
                  <Td actions>
                    <Button size="sm" variant="secondary" onClick={() => openDetail(l)}>
                      {t("loans:paymentsButton")}
                    </Button>
                    {canModify(l) && (
                      <>
                        <IconButton onClick={() => startEdit(l)} aria-label={t("common:edit")}>
                          <EditIcon />
                        </IconButton>
                        <IconButton variant="danger" onClick={() => handleDelete(l)} aria-label={t("common:delete")}>
                          <TrashIcon />
                        </IconButton>
                      </>
                    )}
                  </Td>
                </tr>
              ))}
            </TBody>
          </Table>
        )}
        <Pagination pageData={pageData} onPageChange={setPage} />
      </div>

      {detail && (
        <Modal open onClose={() => setDetail(null)}
          title={t("loans:paymentsTitle", { member: memberName(detail.memberUserId), name: detail.counterpartyName })}
          subtitle={t("loans:paymentsSubtitle", { paid: formatCurrency(detail.paid), remaining: formatCurrency(detail.remaining) })}>
          {detail.payments.length === 0 ? (
            <p className="empty-state">{t("loans:noPayments")}</p>
          ) : (
            <ul className="mb-3 space-y-1 text-sm">
              {detail.payments.map((p) => (
                <li key={p.id} className="flex items-center justify-between gap-2">
                  <span>
                    {formatServerDateTime(p.paidAt)} · <strong>{formatCurrency(p.amount)}</strong> · {walletName(p.walletId)}
                    {p.note ? ` · ${p.note}` : ""}
                  </span>
                  {(isOwner || String(p.createdByUserId) === String(userId)) && (
                    <IconButton variant="danger" size="sm" onClick={() => deletePayment(p)} aria-label={t("common:delete")}>
                      <TrashIcon />
                    </IconButton>
                  )}
                </li>
              ))}
            </ul>
          )}
          {detail.status === "OPEN" && (
            <form className="space-y-2" onSubmit={addPayment}>
              <Field>
                {detail.direction === "BORROWED" ? t("loans:repayLabel") : t("loans:collectLabel")}
                <AmountInput value={payment.amount} onChange={(v) => setPayment((p) => ({ ...p, amount: v }))}
                  required positive max={Number(detail.remaining)} />
              </Field>
              <Field>
                {detail.direction === "BORROWED" ? t("loans:walletOutLabel") : t("loans:walletInLabel")}
                <Select value={payment.walletId} onChange={(e) => setPayment((p) => ({ ...p, walletId: e.target.value }))}>
                  {formWallets.filter((w) => w.id !== detail.counterpartyWalletId).map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field>
                {t("loans:paidAtLabel")}
                <Input type="datetime-local" value={payment.paidAt} max={maxDateTime()}
                  onChange={(e) => setPayment((p) => ({ ...p, paidAt: e.target.value }))} required />
              </Field>
              <Button type="submit">{t("loans:addPaymentButton")}</Button>
            </form>
          )}
        </Modal>
      )}
    </div>
  );
}
