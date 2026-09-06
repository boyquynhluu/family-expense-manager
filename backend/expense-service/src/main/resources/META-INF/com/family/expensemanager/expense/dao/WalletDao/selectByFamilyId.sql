SELECT
    id,
    family_id,
    name,
    currency,
    initial_balance
FROM
    WALLETS
WHERE
    family_id = /* familyId */0
ORDER BY
    id
