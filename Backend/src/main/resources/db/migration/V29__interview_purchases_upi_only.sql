-- AI interviews are bought only by UPI QR now (Razorpay removed from this flow). Drop the Razorpay-only parts and give the
-- reference column its real name. Existing UPI rows are kept as they are.
DELETE FROM interview_purchases WHERE method = 'RAZORPAY' OR status = 'FAILED';

ALTER TABLE interview_purchases
    DROP INDEX uk_interview_purchase_order,
    CHANGE COLUMN gateway_order_id reference VARCHAR(20) NOT NULL,   -- e.g. PYAB23CD45, also put in the UPI payment note
    DROP COLUMN gateway_payment_id,
    DROP COLUMN method,
    MODIFY COLUMN status ENUM('PENDING','SUBMITTED','PAID','REJECTED') NOT NULL DEFAULT 'PENDING',
    ADD UNIQUE KEY uk_interview_purchase_reference (reference);
