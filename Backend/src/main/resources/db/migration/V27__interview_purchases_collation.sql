-- V25 created interview_purchases with an explicit utf8mb4_unicode_ci collation; every other table uses the server default for
-- utf8mb4. Match them, so SQL joins on ids (e.g. to college_students) work.
ALTER TABLE interview_purchases CONVERT TO CHARACTER SET utf8mb4;
