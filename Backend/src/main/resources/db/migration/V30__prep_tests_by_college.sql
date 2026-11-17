-- Mock tests uploaded by a college's placement team (TPO). college_id NULL = platform test, visible to every student;
-- otherwise only that college's students see it. College tests are always standalone (never part of the unlock path).
ALTER TABLE prep_tests
    ADD COLUMN college_id CHAR(36) NULL AFTER id,
    ADD KEY idx_prep_tests_college (college_id, published);
