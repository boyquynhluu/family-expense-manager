-- C6 "Danh mục con": one level of children (a parent is always top-level, same type); reports and budgets on a
-- parent include its children.
ALTER TABLE CATEGORIES
    ADD COLUMN parent_id BIGINT UNSIGNED NULL AFTER family_id,
    ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES CATEGORIES (id);

-- C5 "Tách giao dịch": one receipt over several categories. The parts must add up to the transaction amount;
-- TRANSACTIONS.category_id keeps the first part's category (the "main" one shown in lists).
CREATE TABLE TRANSACTION_SPLITS (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT UNSIGNED NOT NULL,
    category_id    BIGINT UNSIGNED NOT NULL,
    amount         DECIMAL(18, 2)  NOT NULL,
    CONSTRAINT chk_transaction_splits_amount CHECK (amount > 0),
    CONSTRAINT fk_transaction_splits_transaction FOREIGN KEY (transaction_id) REFERENCES TRANSACTIONS (id) ON DELETE CASCADE,
    CONSTRAINT fk_transaction_splits_category FOREIGN KEY (category_id) REFERENCES CATEGORIES (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_transaction_splits_transaction ON TRANSACTION_SPLITS (transaction_id);
CREATE INDEX idx_transaction_splits_category ON TRANSACTION_SPLITS (category_id);

-- Every category-level aggregate reads this view instead of TRANSACTIONS: a split transaction contributes one
-- line per part (its own category and amount), any other transaction one line (itself).
CREATE VIEW TRANSACTION_CATEGORY_LINES AS
SELECT t.id          AS transaction_id,
       t.family_id   AS family_id,
       t.wallet_id   AS wallet_id,
       t.user_id     AS user_id,
       t.type        AS type,
       t.occurred_at AS occurred_at,
       t.deleted_at  AS deleted_at,
       COALESCE(s.category_id, t.category_id) AS category_id,
       COALESCE(s.amount, t.amount)           AS amount
FROM TRANSACTIONS t
         LEFT JOIN TRANSACTION_SPLITS s ON s.transaction_id = t.id;

-- C6 "Tag": free labels to group spending by event ("Du lịch Đà Lạt"), across categories.
CREATE TABLE TAGS (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id  BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(50)     NOT NULL,
    created_at DATETIME        NOT NULL,
    CONSTRAINT uk_tags_family_name UNIQUE (family_id, name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE TRANSACTION_TAGS (
    transaction_id BIGINT UNSIGNED NOT NULL,
    tag_id         BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (transaction_id, tag_id),
    CONSTRAINT fk_transaction_tags_transaction FOREIGN KEY (transaction_id) REFERENCES TRANSACTIONS (id) ON DELETE CASCADE,
    CONSTRAINT fk_transaction_tags_tag FOREIGN KEY (tag_id) REFERENCES TAGS (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_transaction_tags_tag ON TRANSACTION_TAGS (tag_id);

-- C7 "Email tổng kết tháng": one row per family and month once the summary went out (no duplicate emails if the
-- scheduler runs twice or the service restarts mid-run).
CREATE TABLE MONTHLY_SUMMARY_RUNS (
    family_id    BIGINT UNSIGNED NOT NULL,
    period_month CHAR(7)         NOT NULL,
    sent_at      DATETIME        NOT NULL,
    PRIMARY KEY (family_id, period_month)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
