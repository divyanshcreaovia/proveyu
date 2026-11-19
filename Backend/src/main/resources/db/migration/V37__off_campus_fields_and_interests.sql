-- Off-campus jobs get a field (software, data, mechanical, ...). Imports set it from the search they came from; the team sets it on
-- jobs they post. Students pick the fields they are interested in and see those jobs first.
ALTER TABLE off_campus_jobs
    ADD COLUMN field VARCHAR(30) NULL AFTER work_mode,
    ADD INDEX idx_ocj_field (field, status);

CREATE TABLE IF NOT EXISTS off_campus_interests (
    student_id  CHAR(36)     NOT NULL,
    fields      VARCHAR(300) NOT NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id),
    CONSTRAINT fk_oci_student FOREIGN KEY (student_id) REFERENCES college_students (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
