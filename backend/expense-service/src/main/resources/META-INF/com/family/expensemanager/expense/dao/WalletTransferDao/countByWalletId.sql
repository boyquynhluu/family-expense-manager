SELECT
    COUNT(*)
FROM
    WALLET_TRANSFERS
WHERE
    from_wallet_id = /* walletId */0
    OR to_wallet_id = /* walletId */0
