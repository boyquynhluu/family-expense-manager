-- Optional receipt photo attached to a transaction (README "4. Không đính kèm ảnh
-- hoá đơn"). The file itself lives on disk (see receipt.storage-path / the
-- receipt-uploads Docker volume) — only the relative path + content type are stored
-- here, so a lost/corrupted volume degrades gracefully to "no receipt" instead of a
-- broken row.
ALTER TABLE TRANSACTIONS
    ADD COLUMN receipt_path         VARCHAR(500) NULL,
    ADD COLUMN receipt_content_type VARCHAR(100) NULL;
