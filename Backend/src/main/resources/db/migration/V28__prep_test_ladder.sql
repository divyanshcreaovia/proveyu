-- Mock test ladder: tests with a level_order form a path from easy (service-company patterns) to hard (product companies).
-- The first is open; each next one opens once the student scores more than prep.unlock-percent (default 50) in the one before.
-- Tests without a level_order stay open as before.
ALTER TABLE prep_tests
    ADD COLUMN level       VARCHAR(10) NULL AFTER company_tag,     -- EASY / MEDIUM / HARD (label only)
    ADD COLUMN level_order INT         NULL AFTER level,
    ADD KEY idx_prep_tests_order (published, level_order);
