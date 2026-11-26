-- Sign-ins made before the password was last changed stop working.
ALTER TABLE users ADD COLUMN password_changed_at DATETIME NULL;
