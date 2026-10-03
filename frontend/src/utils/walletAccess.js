// Mirrors WalletService.canUse on the backend: the family OWNER may use any wallet; everyone
// else only a shared wallet (no owner) or their own. "Use" = record a transaction in it /
// move money out of it. Every member can still SEE every wallet (lists, balances, filters).
export function canUseWallet(wallet, { role, userId }) {
  if (!wallet) return false;
  return role === "OWNER" || wallet.ownerUserId == null || String(wallet.ownerUserId) === String(userId);
}

/**
 * Wallets to offer in a "pick a wallet" dropdown. `keepId` keeps one extra wallet in the list
 * (the one an entry being edited already uses) so editing an old entry in a wallet that has
 * since become someone else's doesn't show an empty/wrong selection.
 */
export function usableWallets(wallets, auth, keepId) {
  return wallets.filter((w) => canUseWallet(w, auth) || (keepId != null && String(w.id) === String(keepId)));
}
