-- ============================================================
-- PROVEYU Database Schema (MySQL 8.0)
-- Initial Migration: V1__init.sql
-- ============================================================

-- 1. Users & Profiles
CREATE TABLE users (
    id                  CHAR(36) PRIMARY KEY,
    email               VARCHAR(190) NOT NULL,
    phone               VARCHAR(15),
    password_hash       VARCHAR(255) NOT NULL,
    role                ENUM('CANDIDATE','RECRUITER','ADMIN','PROCTOR') NOT NULL DEFAULT 'CANDIDATE',
    full_name           VARCHAR(150) NOT NULL,
    is_active           TINYINT(1) NOT NULL DEFAULT 1,
    email_verified_at   DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_phone (phone),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE candidate_profiles (
    user_id               CHAR(36) PRIMARY KEY,
    experience_track       ENUM('FRESHER','MID','SENIOR') NOT NULL DEFAULT 'FRESHER',
    years_of_experience    DECIMAL(4,1) DEFAULT 0,
    current_location       VARCHAR(120),
    resume_url              VARCHAR(500),
    verification_status     ENUM('UNVERIFIED','PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'UNVERIFIED',
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_candidate_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cities (
    id     CHAR(36) PRIMARY KEY,
    name   VARCHAR(80) NOT NULL,
    state  VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE candidate_preferred_locations (
    candidate_id  CHAR(36) NOT NULL,
    city_id       CHAR(36) NOT NULL,
    PRIMARY KEY (candidate_id, city_id),
    CONSTRAINT fk_cpl_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_cpl_city FOREIGN KEY (city_id) REFERENCES cities(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Simplified Companies & Recruiters (GST Number focus)
CREATE TABLE companies (
    id                       CHAR(36) PRIMARY KEY,
    legal_name               VARCHAR(200) NOT NULL,
    trade_name               VARCHAR(200),
    gstin                    CHAR(15) NOT NULL,
    registered_address       TEXT,
    state                    VARCHAR(80),
    website_domain           VARCHAR(255),
    gst_status               ENUM('ACTIVE','CANCELLED','SUSPENDED','UNKNOWN') NOT NULL DEFAULT 'ACTIVE',
    verification_status      ENUM('UNVERIFIED','PENDING','VERIFIED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'UNVERIFIED',
    verified_at              DATETIME NULL,
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_companies_gstin (gstin),
    INDEX idx_companies_verification_status (verification_status),
    INDEX idx_companies_gst_status (gst_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recruiter_profiles (
    user_id             CHAR(36) PRIMARY KEY,
    company_id          CHAR(36) NOT NULL,
    designation         VARCHAR(100),
    work_email          VARCHAR(190),
    work_email_verified_at DATETIME NULL,
    seats_purchased     INT NOT NULL DEFAULT 0,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recruiter_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_recruiter_company FOREIGN KEY (company_id) REFERENCES companies(id),
    INDEX idx_recruiter_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE company_documents (
    id            CHAR(36) PRIMARY KEY,
    company_id    CHAR(36) NOT NULL,
    doc_type      ENUM('GST_CERTIFICATE','ADDRESS_PROOF','OTHER') NOT NULL,
    document_url  VARCHAR(500) NOT NULL,
    uploaded_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at   DATETIME NULL,
    CONSTRAINT fk_company_doc FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    INDEX idx_company_doc_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE company_verification_logs (
    id               CHAR(36) PRIMARY KEY,
    company_id       CHAR(36) NOT NULL,
    verification_type ENUM('GSTIN','WORK_EMAIL_DOMAIN') NOT NULL,
    provider         VARCHAR(50) NOT NULL,
    request_payload   JSON NULL,
    response_payload  JSON NULL,
    status            ENUM('SUBMITTED','IN_REVIEW','VERIFIED','REJECTED') NOT NULL DEFAULT 'SUBMITTED',
    reviewed_by        CHAR(36) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_companyverlog_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_companyverlog_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    INDEX idx_company_verlog_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Exams, Skills & Question Banks
CREATE TABLE skills (
    id           CHAR(36) PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    category     VARCHAR(60),
    UNIQUE KEY uq_skills_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE exams (
    id                CHAR(36) PRIMARY KEY,
    skill_id          CHAR(36) NOT NULL,
    level             TINYINT NOT NULL,
    title             VARCHAR(150) NOT NULL,
    duration_minutes  SMALLINT NOT NULL DEFAULT 60,
    total_marks       INT NOT NULL DEFAULT 100,
    passing_marks     INT NOT NULL DEFAULT 40,
    price_cents       INT NOT NULL,
    currency          CHAR(3) NOT NULL DEFAULT 'INR',
    is_active         TINYINT(1) NOT NULL DEFAULT 1,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_exams_skill_level (skill_id, level),
    CONSTRAINT chk_exam_level CHECK (level BETWEEN 1 AND 3),
    CONSTRAINT fk_exam_skill FOREIGN KEY (skill_id) REFERENCES skills(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE question_banks (
    id           CHAR(36) PRIMARY KEY,
    exam_id      CHAR(36) NOT NULL,
    version      INT NOT NULL DEFAULT 1,
    is_active    TINYINT(1) NOT NULL DEFAULT 1,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_qbank_exam_version (exam_id, version),
    CONSTRAINT fk_qbank_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE questions (
    id                CHAR(36) PRIMARY KEY,
    question_bank_id  CHAR(36) NOT NULL,
    sub_skill_tag     VARCHAR(80),
    question_type     ENUM('MCQ','CODE','DESCRIPTIVE') NOT NULL,
    body_encrypted    VARBINARY(8000) NOT NULL,
    marks             SMALLINT NOT NULL DEFAULT 1,
    difficulty        TINYINT,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_question_difficulty CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT fk_question_bank FOREIGN KEY (question_bank_id) REFERENCES question_banks(id) ON DELETE CASCADE,
    INDEX idx_questions_bank (question_bank_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Test Centers, Rooms & Date Slots
CREATE TABLE test_centers (
    id           CHAR(36) PRIMARY KEY,
    city_id      CHAR(36) NOT NULL,
    name         VARCHAR(150) NOT NULL,
    address      TEXT NOT NULL,
    total_rooms  SMALLINT NOT NULL DEFAULT 1,
    is_active    TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT fk_center_city FOREIGN KEY (city_id) REFERENCES cities(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rooms (
    id            CHAR(36) PRIMARY KEY,
    center_id     CHAR(36) NOT NULL,
    room_code     VARCHAR(20) NOT NULL,
    capacity      SMALLINT NOT NULL,
    UNIQUE KEY uq_room_center_code (center_id, room_code),
    CONSTRAINT fk_room_center FOREIGN KEY (center_id) REFERENCES test_centers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE date_slots (
    id            CHAR(36) PRIMARY KEY,
    room_id       CHAR(36) NOT NULL,
    exam_id       CHAR(36) NOT NULL,
    slot_date     DATE NOT NULL,
    start_time    TIME NOT NULL,
    end_time      TIME NOT NULL,
    UNIQUE KEY uq_slot_room_date_time (room_id, slot_date, start_time),
    CONSTRAINT fk_slot_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_slot_exam FOREIGN KEY (exam_id) REFERENCES exams(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE slot_inventory (
    slot_id          CHAR(36) PRIMARY KEY,
    total_seats       SMALLINT NOT NULL,
    seats_available   SMALLINT NOT NULL,
    version           INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_slot FOREIGN KEY (slot_id) REFERENCES date_slots(id) ON DELETE CASCADE,
    INDEX idx_slot_inventory_avail (seats_available)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Bookings, Payments & Admit Cards
CREATE TABLE bookings (
    id             CHAR(36) PRIMARY KEY,
    candidate_id   CHAR(36) NOT NULL,
    slot_id        CHAR(36) NOT NULL,
    status         ENUM('HELD','CONFIRMED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'HELD',
    held_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at     DATETIME NOT NULL,
    confirmed_at   DATETIME NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_candidate FOREIGN KEY (candidate_id) REFERENCES users(id),
    CONSTRAINT fk_booking_slot FOREIGN KEY (slot_id) REFERENCES date_slots(id),
    INDEX idx_bookings_candidate (candidate_id),
    INDEX idx_bookings_status_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    id                CHAR(36) PRIMARY KEY,
    booking_id        CHAR(36) NOT NULL,
    gateway           VARCHAR(30) NOT NULL DEFAULT 'RAZORPAY',
    gateway_order_id  VARCHAR(100) NOT NULL,
    gateway_payment_id VARCHAR(100) NULL,
    upi_transaction_id VARCHAR(100) NULL,
    amount_cents      INT NOT NULL,
    currency          CHAR(3) NOT NULL DEFAULT 'INR',
    status            ENUM('PENDING','SUCCESS','FAILED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    idempotency_key   VARCHAR(100) NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_payments_idempotency (idempotency_key),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    INDEX idx_payments_order (gateway_order_id),
    INDEX idx_payments_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admit_cards (
    id              CHAR(36) PRIMARY KEY,
    booking_id      CHAR(36) NOT NULL,
    qr_code_token   VARCHAR(200) NOT NULL,
    barcode_value   VARCHAR(100) NOT NULL,
    issued_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pdf_url         VARCHAR(500) NULL,
    UNIQUE KEY uq_admit_booking (booking_id),
    UNIQUE KEY uq_admit_qr (qr_code_token),
    UNIQUE KEY uq_admit_barcode (barcode_value),
    CONSTRAINT fk_admit_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Exam Sessions, Answers & Results
CREATE TABLE exam_sessions (
    id                 CHAR(36) PRIMARY KEY,
    booking_id         CHAR(36) NOT NULL,
    candidate_id       CHAR(36) NOT NULL,
    exam_id            CHAR(36) NOT NULL,
    question_bank_id   CHAR(36) NOT NULL,
    status             ENUM('SCHEDULED','CHECKED_IN','IN_PROGRESS','SUBMITTED','GRADED','FLAGGED') NOT NULL DEFAULT 'SCHEDULED',
    checked_in_at      DATETIME NULL,
    started_at         DATETIME NULL,
    submitted_at       DATETIME NULL,
    proctor_flags      JSON NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_session_booking (booking_id),
    CONSTRAINT fk_session_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_session_candidate FOREIGN KEY (candidate_id) REFERENCES users(id),
    CONSTRAINT fk_session_exam FOREIGN KEY (exam_id) REFERENCES exams(id),
    CONSTRAINT fk_session_qbank FOREIGN KEY (question_bank_id) REFERENCES question_banks(id),
    INDEX idx_exam_sessions_candidate (candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE exam_answers (
    id               CHAR(36) PRIMARY KEY,
    exam_session_id  CHAR(36) NOT NULL,
    question_id      CHAR(36) NOT NULL,
    answer_payload   JSON NOT NULL,
    marks_awarded    DECIMAL(5,2) NULL,
    answered_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_answer_session_question (exam_session_id, question_id),
    CONSTRAINT fk_answer_session FOREIGN KEY (exam_session_id) REFERENCES exam_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES questions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE exam_results (
    id                CHAR(36) PRIMARY KEY,
    exam_session_id   CHAR(36) NOT NULL,
    total_marks       DECIMAL(6,2) NOT NULL,
    marks_obtained    DECIMAL(6,2) NOT NULL,
    percentile        DECIMAL(5,2) NULL,
    passed            TINYINT(1) NOT NULL,
    graded_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_result_session (exam_session_id),
    CONSTRAINT fk_result_session FOREIGN KEY (exam_session_id) REFERENCES exam_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE skill_score_breakdown (
    id               CHAR(36) PRIMARY KEY,
    exam_result_id   CHAR(36) NOT NULL,
    skill_id         CHAR(36) NOT NULL,
    sub_skill_tag    VARCHAR(80) NULL,
    score_percent    DECIMAL(5,2) NOT NULL,
    CONSTRAINT fk_breakdown_result FOREIGN KEY (exam_result_id) REFERENCES exam_results(id) ON DELETE CASCADE,
    CONSTRAINT fk_breakdown_skill FOREIGN KEY (skill_id) REFERENCES skills(id),
    INDEX idx_skill_breakdown_result (exam_result_id),
    INDEX idx_skill_breakdown_skill_score (skill_id, score_percent)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Verification Logs & Candidate Search Index
CREATE TABLE employment_records (
    id              CHAR(36) PRIMARY KEY,
    candidate_id    CHAR(36) NOT NULL,
    employer_name   VARCHAR(150) NOT NULL,
    designation     VARCHAR(100),
    start_date      DATE NOT NULL,
    end_date        DATE NULL,
    uan_number      VARCHAR(20) NULL,
    document_url    VARCHAR(500) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_employment_candidate FOREIGN KEY (candidate_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE verification_logs (
    id                     CHAR(36) PRIMARY KEY,
    employment_record_id  CHAR(36) NOT NULL,
    provider               VARCHAR(50) NOT NULL,
    request_payload         JSON NULL,
    response_payload        JSON NULL,
    status                   ENUM('SUBMITTED','IN_REVIEW','VERIFIED','REJECTED') NOT NULL DEFAULT 'SUBMITTED',
    reviewed_by              CHAR(36) NULL,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_verlog_record FOREIGN KEY (employment_record_id) REFERENCES employment_records(id) ON DELETE CASCADE,
    CONSTRAINT fk_verlog_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    INDEX idx_verification_logs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE candidate_search_index (
    candidate_id         CHAR(36) NOT NULL,
    skill_id             CHAR(36) NOT NULL,
    score_percent        DECIMAL(5,2) NOT NULL,
    full_name            VARCHAR(150) NOT NULL,
    experience_track      ENUM('FRESHER','MID','SENIOR') NULL,
    verification_status   ENUM('UNVERIFIED','PENDING','VERIFIED','REJECTED') NULL,
    last_updated           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (candidate_id, skill_id),
    CONSTRAINT fk_search_candidate FOREIGN KEY (candidate_id) REFERENCES users(id),
    CONSTRAINT fk_search_skill FOREIGN KEY (skill_id) REFERENCES skills(id),
    INDEX idx_search_skill_score (skill_id, score_percent),
    INDEX idx_search_experience (experience_track, verification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
