-- V21: Student (candidate) portal.
--   1. Per-student announcement inbox (who received which announcement, read receipts).
--   2. Document vault: more document types and an optional student-given label.
-- Additive only: no existing column or row is changed in a way that loses data.

CREATE TABLE IF NOT EXISTS college_announcement_recipients (
    id               CHAR(36)  NOT NULL,
    college_id       CHAR(36)  NOT NULL,
    announcement_id  CHAR(36)  NOT NULL,
    student_id       CHAR(36)  NOT NULL,
    read_at          DATETIME  NULL,
    created_at       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_car_ann_student (announcement_id, student_id),
    INDEX idx_car_student (student_id, created_at),
    CONSTRAINT fk_car_college FOREIGN KEY (college_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_car_announcement FOREIGN KEY (announcement_id) REFERENCES college_announcements(id) ON DELETE CASCADE,
    CONSTRAINT fk_car_student FOREIGN KEY (student_id) REFERENCES college_students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE college_student_documents
    MODIFY doc_type ENUM('RESUME','MARKSHEET_10','MARKSHEET_12','MARKSHEET_SEM','ID_PROOF','OTHER',
                         'PHOTO','CERTIFICATE','INTERNSHIP_CERTIFICATE','COVER_LETTER','OFFER_LETTER') NOT NULL;

ALTER TABLE college_student_documents ADD COLUMN label VARCHAR(150) NULL AFTER doc_type;
