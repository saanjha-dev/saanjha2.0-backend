-- ===========================================================================
-- SAANJHA 2.0: V3 MIGRATION (USER MODULE)
-- 1. Create separate 'usr' schema to decouple from 'auth'
-- 2. Create Profiles, Skills, Interests, Social Links, and Preferences
-- 3. Apply high-performance indexes for Discovery Module queries
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS usr;

CREATE TABLE usr.usr_profiles (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  user_id UUID UNIQUE NOT NULL, -- Logical link to auth.auth_users
                                  display_name VARCHAR(100),
                                  headline VARCHAR(150),
                                  bio TEXT,
                                  location VARCHAR(100),
                                  college VARCHAR(200),
                                  experience_level VARCHAR(50),
                                  profile_image_url VARCHAR(500),
                                  profile_score INT DEFAULT 0,
                                  projects_completed INT DEFAULT 0,

    -- BaseAuditEntity fields
                                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                  created_by VARCHAR(255),
                                  updated_by VARCHAR(255),
                                  is_deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE usr.usr_skills (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                profile_id UUID REFERENCES usr.usr_profiles(id) ON DELETE CASCADE,
                                skill_name VARCHAR(100) NOT NULL,
                                skill_level VARCHAR(20) NOT NULL,
                                is_verified BOOLEAN DEFAULT FALSE,
                                verified_by UUID,
                                verified_at TIMESTAMP WITH TIME ZONE,

    -- BaseAuditEntity fields
                                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                created_by VARCHAR(255),
                                updated_by VARCHAR(255),
                                is_deleted BOOLEAN DEFAULT FALSE,

    -- Prevent duplicate skills for the same user
                                UNIQUE(profile_id, skill_name)
);

CREATE TABLE usr.usr_interests (
                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   profile_id UUID REFERENCES usr.usr_profiles(id) ON DELETE CASCADE,
                                   interest_name VARCHAR(100) NOT NULL,

    -- BaseAuditEntity fields
                                   created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                   updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                   created_by VARCHAR(255),
                                   updated_by VARCHAR(255),
                                   is_deleted BOOLEAN DEFAULT FALSE,

    -- Prevent duplicate interests for the same user
                                   UNIQUE(profile_id, interest_name)
);

CREATE TABLE usr.usr_social_links (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      profile_id UUID REFERENCES usr.usr_profiles(id) ON DELETE CASCADE,
                                      platform_name VARCHAR(50) NOT NULL, -- e.g., 'GITHUB', 'LINKEDIN', 'LEETCODE'
                                      url VARCHAR(500) NOT NULL,

    -- BaseAuditEntity fields
                                      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                      updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                      created_by VARCHAR(255),
                                      updated_by VARCHAR(255),
                                      is_deleted BOOLEAN DEFAULT FALSE,

    -- THE MAGIC RULE: One specific platform per user
                                      UNIQUE(profile_id, platform_name)
);

CREATE TABLE usr.usr_preferences (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     profile_id UUID UNIQUE REFERENCES usr.usr_profiles(id) ON DELETE CASCADE,
                                     theme VARCHAR(20) DEFAULT 'DARK',
                                     email_notifications BOOLEAN DEFAULT TRUE,
                                     profile_visibility VARCHAR(20) DEFAULT 'PUBLIC',

    -- BaseAuditEntity fields
                                     created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                     updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                     created_by VARCHAR(255),
                                     updated_by VARCHAR(255),
                                     is_deleted BOOLEAN DEFAULT FALSE
);

-- ===========================================================================
-- MASTER ENGINEERED INDEXES
-- ===========================================================================

-- 1. Identity Index
CREATE INDEX idx_usr_profiles_user_id ON usr.usr_profiles(user_id);

-- 2. Foreign Key Indexes (Critical for fast JOINs when loading a profile)
CREATE INDEX idx_usr_skills_profile_id ON usr.usr_skills(profile_id);
CREATE INDEX idx_usr_interests_profile_id ON usr.usr_interests(profile_id);
CREATE INDEX idx_usr_socials_profile_id ON usr.usr_social_links(profile_id);

-- 3. Search & Discovery Indexes
CREATE INDEX idx_usr_skills_name ON usr.usr_skills(LOWER(skill_name));
CREATE INDEX idx_usr_interests_name ON usr.usr_interests(LOWER(interest_name));