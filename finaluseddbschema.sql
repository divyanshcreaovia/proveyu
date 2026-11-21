-- =====================================================================================================
-- finaluseddbschema.sql : ProveYu final database schema (MySQL 8, utf8mb4)
-- Only the 53 tables the application code uses (JPA entities and SQL in Backend/src/main/java).
-- Generated from the live dev database proveyu_college (Flyway version 39), structure only, no data.
-- Not included: flyway_schema_history (Flyway creates it) and 44 idle tables no code reads or writes.
-- Change: candidate_scores.exam_id keeps its column but the foreign key to the idle `exams` table is dropped.
-- =====================================================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------------------------------
-- Shared / auth
-- -----------------------------------------------------------------------------------------------------

CREATE TABLE `users` (
  `id` char(36) NOT NULL,
  `email` varchar(190) NOT NULL,
  `phone` varchar(15) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('CANDIDATE','RECRUITER','ORGANIZATION','ADMIN','COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER') NOT NULL DEFAULT 'CANDIDATE',
  `full_name` varchar(150) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `email_verified_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `reset_token` varchar(100) DEFAULT NULL,
  `reset_token_expires_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_users_email` (`email`),
  UNIQUE KEY `uq_users_phone` (`phone`),
  KEY `idx_users_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `email_verification_tokens` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `used_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_evt_token` (`token_hash`),
  KEY `idx_evt_user` (`user_id`),
  CONSTRAINT `fk_evt_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notifications` (
  `id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `type` varchar(50) NOT NULL,
  `message` text NOT NULL,
  `is_read` tinyint(1) DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_notifications_user` (`user_id`),
  CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_messages` (
  `id` char(36) NOT NULL,
  `sender_id` char(36) NOT NULL,
  `receiver_id` char(36) NOT NULL,
  `message` text,
  `message_type` varchar(20) NOT NULL DEFAULT 'TEXT',
  `file_path` varchar(500) DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `file_content_type` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'SENT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chat_sender_id` (`sender_id`),
  KEY `idx_chat_receiver_id` (`receiver_id`),
  KEY `idx_chat_sender_receiver_created` (`sender_id`,`receiver_id`,`created_at`),
  KEY `idx_chat_receiver_sender_created` (`receiver_id`,`sender_id`,`created_at`),
  KEY `idx_chat_receiver_status` (`receiver_id`,`status`),
  CONSTRAINT `fk_chat_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_chat_sender` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `platform_settings` (
  `setting_key` varchar(80) NOT NULL,
  `setting_value` varchar(1000) NOT NULL,
  `updated_by` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `pricing_enquiries` (
  `id` char(36) NOT NULL,
  `full_name` varchar(150) NOT NULL,
  `email` varchar(190) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `organization` varchar(255) NOT NULL,
  `org_type` varchar(20) NOT NULL,
  `designation` varchar(120) DEFAULT NULL,
  `city` varchar(120) DEFAULT NULL,
  `student_count` varchar(20) DEFAULT NULL,
  `message` text,
  `status` varchar(20) NOT NULL DEFAULT 'NEW',
  `admin_note` text,
  `handled_by` varchar(150) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pricing_enquiry_status_time` (`status`,`created_at`),
  KEY `idx_pricing_enquiry_email_time` (`email`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `support_tickets` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `user_name` varchar(150) NOT NULL,
  `user_email` varchar(190) NOT NULL,
  `user_role` varchar(30) NOT NULL,
  `portal` varchar(20) NOT NULL,
  `college_id` char(36) DEFAULT NULL,
  `college_name` varchar(255) DEFAULT NULL,
  `category` varchar(30) NOT NULL,
  `subject` varchar(150) NOT NULL,
  `message` text NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'OPEN',
  `reply` text,
  `replied_by` varchar(150) DEFAULT NULL,
  `replied_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_support_status_time` (`status`,`created_at`),
  KEY `idx_support_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------------------------------
-- Admin
-- -----------------------------------------------------------------------------------------------------

CREATE TABLE `admin_audit_logs` (
  `id` char(36) NOT NULL,
  `admin_id` char(36) DEFAULT NULL,
  `admin_name` varchar(150) DEFAULT NULL,
  `action` varchar(60) NOT NULL,
  `target_type` varchar(40) DEFAULT NULL,
  `target_id` varchar(64) DEFAULT NULL,
  `summary` varchar(500) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_audit_time` (`created_at`),
  KEY `idx_admin_audit_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------------------------------
-- College portal
-- -----------------------------------------------------------------------------------------------------

CREATE TABLE `college_profiles` (
  `college_id` char(36) NOT NULL,
  `college_name` varchar(200) NOT NULL,
  `aicte_code` varchar(50) DEFAULT NULL,
  `university` varchar(200) DEFAULT NULL,
  `city` varchar(100) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `email_domain` varchar(120) DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  `logo_file_id` char(36) DEFAULT NULL,
  `tpo_name` varchar(150) DEFAULT NULL,
  `tpo_email` varchar(190) DEFAULT NULL,
  `tpo_phone` varchar(20) DEFAULT NULL,
  `approval_status` enum('PENDING','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
  `approval_note` varchar(500) DEFAULT NULL,
  `current_academic_year` varchar(20) DEFAULT NULL,
  `policy_mode` enum('OPEN','ONE_OFFER','TIER_UPGRADE') NOT NULL DEFAULT 'TIER_UPGRADE',
  `max_applications` int DEFAULT NULL,
  `max_offers` int NOT NULL DEFAULT '2',
  `upgrade_min_hike_percent` decimal(5,2) NOT NULL DEFAULT '0.00',
  `block_after_accept` tinyint(1) NOT NULL DEFAULT '1',
  `off_campus_policy` enum('ALLOW','BLOCK_PLACED','DISABLED') NOT NULL DEFAULT 'ALLOW',
  `brand_color` varchar(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`college_id`),
  UNIQUE KEY `uq_college_aicte` (`aicte_code`),
  KEY `idx_college_approval` (`approval_status`),
  CONSTRAINT `fk_cp_org` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_members` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `role` enum('COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER') NOT NULL,
  `designation` varchar(150) DEFAULT NULL,
  `branch_scope` text,
  `status` enum('ACTIVE','REVOKED') NOT NULL DEFAULT 'ACTIVE',
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_member_user` (`user_id`),
  KEY `idx_member_college` (`college_id`),
  CONSTRAINT `fk_member_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_member_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_invites` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `email` varchar(190) NOT NULL,
  `full_name` varchar(150) NOT NULL,
  `role` enum('COLLEGE_ADMIN','COLLEGE_COORDINATOR','COLLEGE_VIEWER') NOT NULL,
  `designation` varchar(150) DEFAULT NULL,
  `branch_scope` text,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `accepted_at` datetime DEFAULT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `invited_by` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_invite_token` (`token_hash`),
  KEY `idx_invite_college` (`college_id`),
  CONSTRAINT `fk_invite_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_branches` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `code` varchar(30) NOT NULL,
  `name` varchar(150) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cb_code` (`college_id`,`code`),
  KEY `idx_cb_college` (`college_id`),
  CONSTRAINT `fk_cb_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_batches` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `grad_year` int NOT NULL,
  `label` varchar(50) NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_batch_year` (`college_id`,`grad_year`),
  KEY `idx_batch_college` (`college_id`),
  CONSTRAINT `fk_batch_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_tiers` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `name` varchar(50) NOT NULL,
  `tier_rank` int NOT NULL,
  `min_ctc_lpa` decimal(8,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tier_rank` (`college_id`,`tier_rank`),
  UNIQUE KEY `uq_tier_name` (`college_id`,`name`),
  KEY `idx_tier_college` (`college_id`),
  CONSTRAINT `fk_tier2_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_students` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `user_id` char(36) DEFAULT NULL,
  `roll_number` varchar(50) NOT NULL,
  `first_name` varchar(100) NOT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `email` varchar(190) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `dob` date DEFAULT NULL,
  `branch_id` char(36) DEFAULT NULL,
  `batch_year` int NOT NULL,
  `degree` varchar(80) DEFAULT NULL,
  `cgpa` decimal(4,2) DEFAULT NULL,
  `tenth_percent` decimal(5,2) DEFAULT NULL,
  `twelfth_percent` decimal(5,2) DEFAULT NULL,
  `active_backlogs` int NOT NULL DEFAULT '0',
  `gap_years` int NOT NULL DEFAULT '0',
  `skills` text,
  `linkedin_url` varchar(255) DEFAULT NULL,
  `github_url` varchar(255) DEFAULT NULL,
  `status` enum('ACTIVE','PLACED','DEBARRED','OPTED_OUT') NOT NULL DEFAULT 'ACTIVE',
  `highest_ctc_lpa` decimal(8,2) DEFAULT NULL,
  `highest_tier_rank` int DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cs_roll` (`college_id`,`roll_number`),
  UNIQUE KEY `uq_cs_email` (`college_id`,`email`),
  KEY `idx_cs_college` (`college_id`),
  KEY `idx_cs_branch` (`college_id`,`branch_id`),
  KEY `idx_cs_batch` (`college_id`,`batch_year`),
  KEY `idx_cs_status` (`college_id`,`status`),
  KEY `idx_cs_user` (`user_id`),
  KEY `fk_cs_branch` (`branch_id`),
  CONSTRAINT `fk_cs_branch` FOREIGN KEY (`branch_id`) REFERENCES `college_branches` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_cs_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_student_documents` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `student_id` char(36) NOT NULL,
  `doc_type` enum('RESUME','MARKSHEET_10','MARKSHEET_12','MARKSHEET_SEM','ID_PROOF','OTHER','PHOTO','CERTIFICATE','INTERNSHIP_CERTIFICATE','COVER_LETTER','OFFER_LETTER') NOT NULL,
  `label` varchar(150) DEFAULT NULL,
  `file_id` char(36) NOT NULL,
  `verification_status` enum('PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `remarks` varchar(500) DEFAULT NULL,
  `verified_by` char(36) DEFAULT NULL,
  `verified_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_csd_college` (`college_id`),
  KEY `idx_csd_student` (`student_id`),
  KEY `fk_csd_file` (`file_id`),
  CONSTRAINT `fk_csd_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_csd_file` FOREIGN KEY (`file_id`) REFERENCES `college_files` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_csd_student` FOREIGN KEY (`student_id`) REFERENCES `college_students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_files` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `original_name` varchar(255) NOT NULL,
  `stored_name` varchar(255) NOT NULL,
  `content_type` varchar(120) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `category` varchar(40) NOT NULL,
  `uploaded_by` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_file_stored` (`stored_name`),
  KEY `idx_file_college` (`college_id`),
  CONSTRAINT `fk_file_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_import_jobs` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `dry_run` tinyint(1) NOT NULL DEFAULT '0',
  `total_rows` int NOT NULL DEFAULT '0',
  `created_rows` int NOT NULL DEFAULT '0',
  `updated_rows` int NOT NULL DEFAULT '0',
  `error_rows` int NOT NULL DEFAULT '0',
  `duplicate_rows` int NOT NULL DEFAULT '0',
  `errors_json` mediumtext,
  `created_by` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cij_college` (`college_id`,`created_at`),
  CONSTRAINT `fk_cij_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_companies` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `name` varchar(200) NOT NULL,
  `industry` varchar(100) DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  `hq_location` varchar(150) DEFAULT NULL,
  `mou_status` enum('NONE','IN_PROGRESS','SIGNED','EXPIRED') NOT NULL DEFAULT 'NONE',
  `mou_expiry` date DEFAULT NULL,
  `linked_recruiter_id` char(36) DEFAULT NULL,
  `notes` text,
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cc_name` (`college_id`,`name`),
  KEY `idx_cc_college` (`college_id`),
  KEY `fk_cc_recruiter` (`linked_recruiter_id`),
  CONSTRAINT `fk_cc_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cc_recruiter` FOREIGN KEY (`linked_recruiter_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_company_contacts` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `company_id` char(36) NOT NULL,
  `name` varchar(150) NOT NULL,
  `designation` varchar(150) DEFAULT NULL,
  `email` varchar(190) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ccc_college` (`college_id`),
  KEY `idx_ccc_company` (`company_id`),
  CONSTRAINT `fk_ccc_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ccc_company` FOREIGN KEY (`company_id`) REFERENCES `college_companies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_company_feedback` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `company_id` char(36) NOT NULL,
  `drive_id` char(36) DEFAULT NULL,
  `rating` tinyint NOT NULL,
  `comment` varchar(1000) DEFAULT NULL,
  `created_by` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ccf_college` (`college_id`),
  KEY `idx_ccf_company` (`company_id`),
  KEY `fk_ccf_drive` (`drive_id`),
  CONSTRAINT `fk_ccf_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ccf_company` FOREIGN KEY (`company_id`) REFERENCES `college_companies` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ccf_drive` FOREIGN KEY (`drive_id`) REFERENCES `college_drives` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_ccf_rating` CHECK ((`rating` between 1 and 5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_drives` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `company_id` char(36) NOT NULL,
  `title` varchar(200) NOT NULL,
  `role_title` varchar(150) NOT NULL,
  `drive_type` enum('ON_CAMPUS','OFF_CAMPUS','POOL','INTERNSHIP','PPO') NOT NULL DEFAULT 'ON_CAMPUS',
  `ctc_lpa` decimal(8,2) DEFAULT NULL,
  `stipend_monthly` decimal(10,2) DEFAULT NULL,
  `ctc_breakup` text,
  `location` varchar(200) DEFAULT NULL,
  `bond_months` int NOT NULL DEFAULT '0',
  `bond_details` varchar(500) DEFAULT NULL,
  `job_description` text,
  `skills` text,
  `mode` enum('OFFLINE','ONLINE','HYBRID') NOT NULL DEFAULT 'OFFLINE',
  `venue` varchar(255) DEFAULT NULL,
  `registration_deadline` datetime DEFAULT NULL,
  `drive_date` date DEFAULT NULL,
  `tier_id` char(36) DEFAULT NULL,
  `status` enum('DRAFT','PUBLISHED','ONGOING','COMPLETED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  `min_cgpa` decimal(4,2) DEFAULT NULL,
  `min_tenth` decimal(5,2) DEFAULT NULL,
  `min_twelfth` decimal(5,2) DEFAULT NULL,
  `max_active_backlogs` int DEFAULT NULL,
  `max_gap_years` int DEFAULT NULL,
  `batch_years` varchar(100) DEFAULT NULL,
  `branch_ids` text,
  `required_skills` text,
  `published_at` datetime DEFAULT NULL,
  `created_by` char(36) DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cd_college` (`college_id`),
  KEY `idx_cd_status` (`college_id`,`status`),
  KEY `idx_cd_company` (`company_id`),
  KEY `idx_cd_date` (`college_id`,`drive_date`),
  KEY `fk_cd_tier` (`tier_id`),
  CONSTRAINT `fk_cd_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cd_company` FOREIGN KEY (`company_id`) REFERENCES `college_companies` (`id`),
  CONSTRAINT `fk_cd_tier` FOREIGN KEY (`tier_id`) REFERENCES `college_tiers` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_drive_rounds` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `drive_id` char(36) NOT NULL,
  `seq` int NOT NULL,
  `name` varchar(120) NOT NULL,
  `round_type` enum('APTITUDE','CODING','GD','TECHNICAL','HR','CUSTOM') NOT NULL,
  `scheduled_at` datetime DEFAULT NULL,
  `status` enum('PENDING','ONGOING','COMPLETED') NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cdr_seq` (`drive_id`,`seq`),
  KEY `idx_cdr_college` (`college_id`),
  CONSTRAINT `fk_cdr_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cdr_drive` FOREIGN KEY (`drive_id`) REFERENCES `college_drives` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_drive_recruiters` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `drive_id` char(36) NOT NULL,
  `email` varchar(190) NOT NULL,
  `full_name` varchar(150) NOT NULL,
  `designation` varchar(150) DEFAULT NULL,
  `user_id` char(36) DEFAULT NULL,
  `token_hash` char(64) DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `status` enum('INVITED','ACTIVE','REVOKED') NOT NULL DEFAULT 'INVITED',
  `share_details` tinyint(1) NOT NULL DEFAULT '0',
  `invited_by` char(36) DEFAULT NULL,
  `accepted_at` datetime DEFAULT NULL,
  `last_access_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cdrec_drive_email` (`drive_id`,`email`),
  UNIQUE KEY `uq_cdrec_token` (`token_hash`),
  KEY `idx_cdrec_user` (`user_id`),
  KEY `idx_cdrec_college` (`college_id`),
  CONSTRAINT `fk_cdrec_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cdrec_drive` FOREIGN KEY (`drive_id`) REFERENCES `college_drives` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cdrec_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_eligibility_overrides` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `drive_id` char(36) NOT NULL,
  `student_id` char(36) NOT NULL,
  `action` enum('INCLUDE','EXCLUDE') NOT NULL,
  `reason` varchar(500) NOT NULL,
  `created_by` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ceo` (`drive_id`,`student_id`),
  KEY `idx_ceo_college` (`college_id`),
  KEY `fk_ceo_student` (`student_id`),
  CONSTRAINT `fk_ceo_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ceo_drive` FOREIGN KEY (`drive_id`) REFERENCES `college_drives` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ceo_student` FOREIGN KEY (`student_id`) REFERENCES `college_students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_applications` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `drive_id` char(36) NOT NULL,
  `student_id` char(36) NOT NULL,
  `status` enum('APPLIED','SHORTLISTED','WAITLISTED','IN_PROCESS','SELECTED','REJECTED','WITHDRAWN') NOT NULL DEFAULT 'APPLIED',
  `source` enum('SELF','TPO','OVERRIDE') NOT NULL DEFAULT 'SELF',
  `current_round` int NOT NULL DEFAULT '0',
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ca` (`drive_id`,`student_id`),
  KEY `idx_ca_college` (`college_id`),
  KEY `idx_ca_student` (`student_id`),
  KEY `idx_ca_status` (`drive_id`,`status`),
  CONSTRAINT `fk_ca_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ca_drive` FOREIGN KEY (`drive_id`) REFERENCES `college_drives` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ca_student` FOREIGN KEY (`student_id`) REFERENCES `college_students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_round_results` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `round_id` char(36) NOT NULL,
  `application_id` char(36) NOT NULL,
  `result` enum('PENDING','PASS','FAIL','ABSENT','HOLD') NOT NULL DEFAULT 'PENDING',
  `score` decimal(6,2) DEFAULT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_crr` (`round_id`,`application_id`),
  KEY `idx_crr_college` (`college_id`),
  KEY `fk_crr_app` (`application_id`),
  CONSTRAINT `fk_crr_app` FOREIGN KEY (`application_id`) REFERENCES `college_applications` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crr_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crr_round` FOREIGN KEY (`round_id`) REFERENCES `college_drive_rounds` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_round_scorecards` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `round_id` char(36) NOT NULL,
  `application_id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `interviewer_name` varchar(150) NOT NULL,
  `technical` tinyint DEFAULT NULL,
  `communication` tinyint DEFAULT NULL,
  `attitude` tinyint DEFAULT NULL,
  `result` enum('PASS','FAIL','HOLD','ABSENT') NOT NULL,
  `remarks` varchar(1000) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_crs` (`round_id`,`application_id`,`user_id`),
  KEY `idx_crs_college` (`college_id`),
  KEY `fk_crs_app` (`application_id`),
  CONSTRAINT `fk_crs_app` FOREIGN KEY (`application_id`) REFERENCES `college_applications` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crs_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crs_round` FOREIGN KEY (`round_id`) REFERENCES `college_drive_rounds` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_interview_slots` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `drive_id` char(36) NOT NULL,
  `round_id` char(36) DEFAULT NULL,
  `application_id` char(36) NOT NULL,
  `panel_name` varchar(150) DEFAULT NULL,
  `panel_members` varchar(500) DEFAULT NULL,
  `starts_at` datetime NOT NULL,
  `ends_at` datetime NOT NULL,
  `room` varchar(100) DEFAULT NULL,
  `meeting_link` varchar(500) DEFAULT NULL,
  `status` enum('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW') NOT NULL DEFAULT 'SCHEDULED',
  `reschedule_count` int NOT NULL DEFAULT '0',
  `reminder_sent_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cis_college_time` (`college_id`,`starts_at`),
  KEY `idx_cis_app` (`application_id`),
  KEY `fk_cis_drive` (`drive_id`),
  KEY `fk_cis_round` (`round_id`),
  CONSTRAINT `fk_cis_app` FOREIGN KEY (`application_id`) REFERENCES `college_applications` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cis_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cis_drive` FOREIGN KEY (`drive_id`) REFERENCES `college_drives` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cis_round` FOREIGN KEY (`round_id`) REFERENCES `college_drive_rounds` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_offers` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `student_id` char(36) NOT NULL,
  `drive_id` char(36) DEFAULT NULL,
  `company_id` char(36) NOT NULL,
  `role_title` varchar(150) NOT NULL,
  `ctc_lpa` decimal(8,2) NOT NULL,
  `ctc_breakup` text,
  `tier_rank` int DEFAULT NULL,
  `offer_date` date DEFAULT NULL,
  `joining_date` date DEFAULT NULL,
  `status` enum('OFFERED','ACCEPTED','DECLINED','JOINED','REVOKED') NOT NULL DEFAULT 'OFFERED',
  `offer_letter_file_id` char(36) DEFAULT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_co_college` (`college_id`),
  KEY `idx_co_student` (`student_id`),
  KEY `idx_co_status` (`college_id`,`status`),
  KEY `fk_co_drive` (`drive_id`),
  KEY `fk_co_company` (`company_id`),
  KEY `fk_co_file` (`offer_letter_file_id`),
  CONSTRAINT `fk_co_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_co_company` FOREIGN KEY (`company_id`) REFERENCES `college_companies` (`id`),
  CONSTRAINT `fk_co_drive` FOREIGN KEY (`drive_id`) REFERENCES `college_drives` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_co_file` FOREIGN KEY (`offer_letter_file_id`) REFERENCES `college_files` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_co_student` FOREIGN KEY (`student_id`) REFERENCES `college_students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_announcements` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `title` varchar(200) NOT NULL,
  `body` text NOT NULL,
  `filter_json` text,
  `recipient_count` int NOT NULL DEFAULT '0',
  `send_email` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_can_college` (`college_id`,`created_at`),
  CONSTRAINT `fk_can_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_announcement_recipients` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `announcement_id` char(36) NOT NULL,
  `student_id` char(36) NOT NULL,
  `read_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_car_ann_student` (`announcement_id`,`student_id`),
  KEY `idx_car_student` (`student_id`,`created_at`),
  KEY `fk_car_college` (`college_id`),
  CONSTRAINT `fk_car_announcement` FOREIGN KEY (`announcement_id`) REFERENCES `college_announcements` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_car_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_car_student` FOREIGN KEY (`student_id`) REFERENCES `college_students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_email_templates` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `template_key` varchar(60) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `body` text NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cet` (`college_id`,`template_key`),
  CONSTRAINT `fk_cet_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_scheduled_reports` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `report_type` varchar(40) NOT NULL,
  `format` enum('XLSX','PDF','CSV') NOT NULL DEFAULT 'XLSX',
  `frequency` enum('DAILY','WEEKLY','MONTHLY') NOT NULL,
  `recipients` varchar(1000) NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `last_run_at` datetime DEFAULT NULL,
  `next_run_at` datetime NOT NULL,
  `created_by` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_csr_college` (`college_id`),
  KEY `idx_csr_next` (`active`,`next_run_at`),
  CONSTRAINT `fk_csr_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `college_audit_logs` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `actor_user_id` char(36) DEFAULT NULL,
  `actor_name` varchar(150) DEFAULT NULL,
  `action` varchar(60) NOT NULL,
  `entity_type` varchar(60) NOT NULL,
  `entity_id` char(36) DEFAULT NULL,
  `summary` varchar(500) NOT NULL,
  `details` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cal_college` (`college_id`,`created_at`),
  KEY `idx_cal_entity` (`college_id`,`entity_type`,`entity_id`),
  CONSTRAINT `fk_cal_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------------------------------
-- Student: AI interview
-- -----------------------------------------------------------------------------------------------------

CREATE TABLE `ai_interview_sessions` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `student_id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `drive_id` char(36) DEFAULT NULL,
  `interview_type` enum('TECHNICAL','HR','MIXED') NOT NULL,
  `difficulty` enum('EASY','MEDIUM','HARD') NOT NULL DEFAULT 'MEDIUM',
  `role_title` varchar(200) NOT NULL,
  `status` enum('IN_PROGRESS','EVALUATING','COMPLETED','FAILED') NOT NULL DEFAULT 'IN_PROGRESS',
  `planned_questions` int NOT NULL,
  `job_description` text,
  `focus_skills` varchar(500) DEFAULT NULL,
  `credit_source` enum('FREE','PAID') NOT NULL,
  `consent_at` datetime NOT NULL,
  `started_at` datetime NOT NULL,
  `ended_at` datetime DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_aisess_user` (`user_id`,`started_at`),
  KEY `idx_aisess_college` (`college_id`),
  KEY `idx_aisess_status` (`status`),
  KEY `fk_aisess_drive` (`drive_id`),
  KEY `idx_ai_sessions_student_source` (`student_id`,`credit_source`,`started_at`),
  CONSTRAINT `fk_aisess_college` FOREIGN KEY (`college_id`) REFERENCES `organizations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_aisess_drive` FOREIGN KEY (`drive_id`) REFERENCES `college_drives` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_aisess_student` FOREIGN KEY (`student_id`) REFERENCES `college_students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_interview_questions` (
  `id` char(36) NOT NULL,
  `session_id` char(36) NOT NULL,
  `seq` int NOT NULL,
  `question_text` varchar(1000) NOT NULL,
  `topic` varchar(120) DEFAULT NULL,
  `parent_question_id` char(36) DEFAULT NULL,
  `asked_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_aiq_session_seq` (`session_id`,`seq`),
  CONSTRAINT `fk_aiq_session` FOREIGN KEY (`session_id`) REFERENCES `ai_interview_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_interview_answers` (
  `id` char(36) NOT NULL,
  `session_id` char(36) NOT NULL,
  `question_id` char(36) NOT NULL,
  `transcript` text NOT NULL,
  `input_mode` enum('VOICE','TEXT') NOT NULL,
  `audio_path` varchar(300) DEFAULT NULL,
  `duration_seconds` int DEFAULT NULL,
  `words_per_minute` int DEFAULT NULL,
  `answered_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_aians_question` (`question_id`),
  KEY `idx_aians_session` (`session_id`),
  CONSTRAINT `fk_aians_question` FOREIGN KEY (`question_id`) REFERENCES `ai_interview_questions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_aians_session` FOREIGN KEY (`session_id`) REFERENCES `ai_interview_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_answer_evaluations` (
  `id` char(36) NOT NULL,
  `answer_id` char(36) NOT NULL,
  `content` tinyint NOT NULL,
  `communication` tinyint NOT NULL,
  `confidence` tinyint NOT NULL,
  `relevance` tinyint NOT NULL,
  `feedback` varchar(1000) DEFAULT NULL,
  `better_answer` varchar(1500) DEFAULT NULL,
  `model` varchar(80) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_aieval_answer` (`answer_id`),
  CONSTRAINT `fk_aieval_answer` FOREIGN KEY (`answer_id`) REFERENCES `ai_interview_answers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_interview_reports` (
  `id` char(36) NOT NULL,
  `session_id` char(36) NOT NULL,
  `overall_score` decimal(5,2) NOT NULL,
  `recommendation` enum('READY','ALMOST_READY','NEEDS_PRACTICE') NOT NULL,
  `summary` varchar(2000) DEFAULT NULL,
  `strengths_json` text,
  `improvements_json` text,
  `model` varchar(80) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_airep_session` (`session_id`),
  CONSTRAINT `fk_airep_session` FOREIGN KEY (`session_id`) REFERENCES `ai_interview_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `interview_credits` (
  `student_id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `free_used` int NOT NULL DEFAULT '0',
  `paid_balance` int NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`student_id`),
  KEY `idx_icred_college` (`college_id`),
  CONSTRAINT `fk_icred_student` FOREIGN KEY (`student_id`) REFERENCES `college_students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `interview_purchases` (
  `id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `student_id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `quantity` int NOT NULL,
  `amount_paise` int NOT NULL,
  `status` enum('PENDING','SUBMITTED','PAID','REJECTED') NOT NULL DEFAULT 'PENDING',
  `reference` varchar(20) NOT NULL,
  `utr` varchar(30) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `paid_at` datetime DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `reviewed_by` char(36) DEFAULT NULL,
  `review_note` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_purchase_reference` (`reference`),
  UNIQUE KEY `uk_interview_purchase_utr` (`utr`),
  KEY `idx_interview_purchase_student` (`student_id`,`created_at`),
  KEY `idx_interview_purchase_status` (`status`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------------------------------
-- Student: mock tests / proctoring
-- -----------------------------------------------------------------------------------------------------

CREATE TABLE `prep_tests` (
  `id` char(36) NOT NULL,
  `college_id` char(36) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `description` text,
  `company_tag` varchar(100) DEFAULT NULL,
  `level` varchar(10) DEFAULT NULL,
  `level_order` int DEFAULT NULL,
  `duration_minutes` int NOT NULL,
  `question_count` int NOT NULL DEFAULT '0',
  `coding_count` int NOT NULL DEFAULT '0',
  `total_marks` int NOT NULL DEFAULT '0',
  `definition_json` mediumtext NOT NULL,
  `published` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_prep_tests_published` (`published`,`created_at`),
  KEY `idx_prep_tests_order` (`published`,`level_order`),
  KEY `idx_prep_tests_college` (`college_id`,`published`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prep_attempts` (
  `id` char(36) NOT NULL,
  `test_id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `started_at` datetime NOT NULL,
  `deadline_at` datetime NOT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `auto_submit_reason` varchar(30) DEFAULT NULL,
  `score` decimal(8,2) DEFAULT NULL,
  `max_score` int NOT NULL DEFAULT '0',
  `answers_json` mediumtext,
  `result_json` mediumtext,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_prep_attempts_user` (`user_id`,`test_id`,`started_at`),
  KEY `fk_prep_attempt_test` (`test_id`),
  CONSTRAINT `fk_prep_attempt_test` FOREIGN KEY (`test_id`) REFERENCES `prep_tests` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_prep_attempt_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prep_proctor_events` (
  `id` char(36) NOT NULL,
  `attempt_id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `kind` varchar(20) NOT NULL,
  `seq` int NOT NULL,
  `occurred_at` datetime(3) NOT NULL,
  `client_at` datetime(3) DEFAULT NULL,
  `screenshot_path` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prep_proctor_seq` (`attempt_id`,`seq`),
  KEY `idx_prep_proctor_attempt` (`attempt_id`,`occurred_at`),
  CONSTRAINT `fk_prep_proctor_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `prep_attempts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `off_campus_jobs` (
  `id` char(36) NOT NULL,
  `company_name` varchar(150) NOT NULL,
  `role_title` varchar(150) NOT NULL,
  `job_type` varchar(20) NOT NULL,
  `work_mode` varchar(20) NOT NULL,
  `field` varchar(30) DEFAULT NULL,
  `location` varchar(150) DEFAULT NULL,
  `ctc_min_lpa` decimal(8,2) DEFAULT NULL,
  `ctc_max_lpa` decimal(8,2) DEFAULT NULL,
  `stipend_per_month` int DEFAULT NULL,
  `description` text,
  `skills` varchar(500) DEFAULT NULL,
  `apply_url` varchar(1000) NOT NULL,
  `apply_deadline` datetime DEFAULT NULL,
  `batch_years` varchar(100) DEFAULT NULL,
  `degree_keywords` varchar(300) DEFAULT NULL,
  `branch_keywords` varchar(300) DEFAULT NULL,
  `min_cgpa` decimal(4,2) DEFAULT NULL,
  `max_backlogs` int DEFAULT NULL,
  `college_ids` text,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `source` varchar(20) NOT NULL DEFAULT 'MANUAL',
  `source_id` varchar(100) DEFAULT NULL,
  `source_posted_at` datetime DEFAULT NULL,
  `created_by` char(36) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ocj_source` (`source`,`source_id`),
  KEY `idx_ocj_status_deadline` (`status`,`apply_deadline`),
  KEY `idx_ocj_source_status` (`source`,`status`,`source_posted_at`),
  KEY `idx_ocj_field` (`field`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `off_campus_applications` (
  `id` char(36) NOT NULL,
  `job_id` char(36) NOT NULL,
  `student_id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `status` varchar(20) DEFAULT NULL,
  `saved` tinyint(1) NOT NULL DEFAULT '0',
  `offer_ctc_lpa` decimal(8,2) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `clicked_at` datetime DEFAULT NULL,
  `applied_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_oca_job_student` (`job_id`,`student_id`),
  KEY `idx_oca_college_status` (`college_id`,`status`),
  KEY `idx_oca_user` (`user_id`),
  KEY `fk_oca_student` (`student_id`),
  CONSTRAINT `fk_oca_job` FOREIGN KEY (`job_id`) REFERENCES `off_campus_jobs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_oca_student` FOREIGN KEY (`student_id`) REFERENCES `college_students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `off_campus_noc_requests` (
  `id` char(36) NOT NULL,
  `application_id` char(36) NOT NULL,
  `job_id` char(36) NOT NULL,
  `student_id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `college_id` char(36) NOT NULL,
  `offer_file_id` char(36) NOT NULL,
  `student_note` varchar(1000) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `decision_note` varchar(1000) DEFAULT NULL,
  `noc_file_id` char(36) DEFAULT NULL,
  `noc_generated` tinyint(1) NOT NULL DEFAULT '0',
  `college_offer_id` char(36) DEFAULT NULL,
  `decided_by` char(36) DEFAULT NULL,
  `decided_by_name` varchar(150) DEFAULT NULL,
  `decided_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_noc_college_status` (`college_id`,`status`,`created_at`),
  KEY `idx_noc_application` (`application_id`),
  CONSTRAINT `fk_noc_application` FOREIGN KEY (`application_id`) REFERENCES `off_campus_applications` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `off_campus_interests` (
  `student_id` char(36) NOT NULL,
  `fields` varchar(300) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`student_id`),
  CONSTRAINT `fk_oci_student` FOREIGN KEY (`student_id`) REFERENCES `college_students` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------------------------------
-- Older platform tables still read by the code
-- -----------------------------------------------------------------------------------------------------

CREATE TABLE `organizations` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `legal_name` varchar(200) NOT NULL,
  `org_type` enum('COLLEGE','UNIVERSITY','TRAINING_INSTITUTE','CONSULTANCY','COMPANY') NOT NULL DEFAULT 'COLLEGE',
  `code_or_gstin` varchar(50) DEFAULT NULL,
  `contact_email` varchar(190) DEFAULT NULL,
  `contact_phone` varchar(20) DEFAULT NULL,
  `address` text,
  `is_verified` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_org_user` (`user_id`),
  KEY `idx_org_type` (`org_type`),
  CONSTRAINT `fk_org_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `candidate_profiles` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL,
  `domain_id` char(36) DEFAULT NULL,
  `experience_track` varchar(20) NOT NULL DEFAULT 'FRESHER',
  `years_of_experience` int DEFAULT '0',
  `uan_number` varchar(50) DEFAULT NULL,
  `verified_backend_experience_months` int DEFAULT '0',
  `college_name` varchar(150) DEFAULT NULL,
  `degree_branch` varchar(100) DEFAULT NULL,
  `passout_year` int DEFAULT NULL,
  `skills_list` varchar(255) DEFAULT NULL,
  `resume_url` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `location` varchar(150) DEFAULT NULL,
  `headline` varchar(255) DEFAULT NULL,
  `linkedin_url` varchar(255) DEFAULT NULL,
  `github_url` varchar(255) DEFAULT NULL,
  `portfolio_url` varchar(255) DEFAULT NULL,
  `bio` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_cand_profile_track` (`experience_track`),
  KEY `idx_candidate_profiles_domain` (`domain_id`),
  CONSTRAINT `fk_cand_profile_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_candidate_profile_domain` FOREIGN KEY (`domain_id`) REFERENCES `domains` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `domains` (
  `id` char(36) NOT NULL,
  `name` varchar(100) NOT NULL,
  `code` varchar(100) NOT NULL,
  `description` text,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_domains_code` (`code`),
  KEY `idx_domains_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `skills` (
  `id` char(36) NOT NULL,
  `name` varchar(100) NOT NULL,
  `category` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_skills_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `candidate_scores` (
  `id` char(36) NOT NULL,
  `candidate_id` char(36) NOT NULL,
  `exam_id` char(36) NOT NULL,
  `skill_id` char(36) NOT NULL,
  `score` decimal(5,2) NOT NULL,
  `max_score` decimal(5,2) NOT NULL,
  `passed` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_cand_score_user` (`candidate_id`),
  KEY `fk_cand_score_exam` (`exam_id`),
  KEY `fk_cand_score_skill` (`skill_id`),
  CONSTRAINT `fk_cand_score_skill` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`id`),
  CONSTRAINT `fk_cand_score_user` FOREIGN KEY (`candidate_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
