-- When students were told that an exam is live (the scheduler sends it once at the start time; a reschedule clears it).
ALTER TABLE prep_tests ADD COLUMN live_notified_at DATETIME NULL AFTER results_released;
