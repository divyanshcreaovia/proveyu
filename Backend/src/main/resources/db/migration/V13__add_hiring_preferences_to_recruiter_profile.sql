ALTER TABLE recruiter_profiles
ADD COLUMN hiring_volume VARCHAR(255),
ADD COLUMN candidate_level VARCHAR(255),
ADD COLUMN primary_track VARCHAR(255),
ADD COLUMN preferred_cities VARCHAR(255),
ADD COLUMN custom_notes TEXT;
