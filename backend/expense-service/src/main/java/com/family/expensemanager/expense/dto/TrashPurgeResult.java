package com.family.expensemanager.expense.dto;

/**
 * What one sweep of the trash removed for good ("Dọn sạch thùng rác" or the nightly retention job).
 *
 * @param skipped wallets/categories left in the trash because something outside it still points at them
 *                (e.g. a transaction restored into a deleted wallet); they go once that reference is gone.
 *
 * @author boyquynhluu
 */
public record TrashPurgeResult(int transactions, int wallets, int categories, int skipped) {

    public int total() {
        return transactions + wallets + categories;
    }
}
