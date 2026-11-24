-- AI proctoring for mock tests (camera + microphone). The browser reports NO_FACE, MULTIPLE_FACES, LOOKING_AWAY, CAMERA_BLOCKED and
-- VOICE_DETECTED into the same proctor event table as tab switches (kind is VARCHAR(20), so the new kinds fit without a change).
-- AI events never submit a test: they are counted apart from tab switches and only flag the attempt for human review.
ALTER TABLE prep_proctor_events ADD COLUMN metadata TEXT NULL AFTER screenshot_path;   -- small JSON from the browser (e.g. faces, yaw)

ALTER TABLE prep_attempts ADD COLUMN consent_at DATETIME NULL AFTER auto_submit_reason;  -- student agreed to camera + mic monitoring
ALTER TABLE prep_attempts ADD COLUMN ai_warning_count INT NOT NULL DEFAULT 0 AFTER consent_at;
