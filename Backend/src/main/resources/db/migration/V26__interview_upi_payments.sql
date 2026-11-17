-- Buying AI interviews by UPI QR (the platform's own UPI ID): the student pays the exact amount shown in the QR, enters the
-- 12-digit UPI reference (UTR), and a platform admin approves it after checking the bank statement.
-- PENDING = order made, not paid yet; SUBMITTED = UTR entered, waiting for the admin; PAID = interviews added;
-- REJECTED = admin could not find the payment; FAILED = Razorpay signature check failed.

ALTER TABLE interview_purchases
    MODIFY COLUMN status ENUM('PENDING','SUBMITTED','PAID','FAILED','REJECTED') NOT NULL DEFAULT 'PENDING',
    ADD COLUMN method        ENUM('RAZORPAY','UPI') NOT NULL DEFAULT 'RAZORPAY' AFTER amount_paise,
    ADD COLUMN utr           VARCHAR(30)  NULL AFTER gateway_payment_id,      -- UPI reference number the student typed
    ADD COLUMN submitted_at  DATETIME     NULL,
    ADD COLUMN reviewed_at   DATETIME     NULL,
    ADD COLUMN reviewed_by   CHAR(36)     NULL,
    ADD COLUMN review_note   VARCHAR(300) NULL,
    ADD UNIQUE KEY uk_interview_purchase_utr (utr),                       -- one bank payment can be claimed only once
    ADD KEY idx_interview_purchase_status (status, submitted_at);
