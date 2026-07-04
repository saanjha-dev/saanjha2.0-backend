-- Add the unique handle column to user profiles
ALTER TABLE usr.usr_profiles
    ADD COLUMN unique_handle VARCHAR(50);

-- Enforce uniqueness across active profiles
ALTER TABLE usr.usr_profiles
    ADD CONSTRAINT unique_active_handle UNIQUE (unique_handle);

-- Create a functional lowercase index for case-insensitive URL routing (e.g., @Rahul vs @rahul)
CREATE INDEX idx_usr_profiles_lower_handle ON usr.usr_profiles(LOWER(unique_handle));