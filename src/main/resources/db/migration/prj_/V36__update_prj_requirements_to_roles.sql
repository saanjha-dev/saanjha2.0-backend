-- ---------------------------------------------------------------------------
-- 1. Rename skill_name to role_name
-- ---------------------------------------------------------------------------
ALTER TABLE prj.prj_requirements RENAME COLUMN skill_name TO role_name;

-- ---------------------------------------------------------------------------
-- 2. Update unique constraint
-- ---------------------------------------------------------------------------
ALTER TABLE prj.prj_requirements DROP CONSTRAINT uq_prj_req_skill;
ALTER TABLE prj.prj_requirements ADD CONSTRAINT uq_prj_req_role UNIQUE (project_id, role_name);

-- ---------------------------------------------------------------------------
-- 3. Update index
-- ---------------------------------------------------------------------------
DROP INDEX prj.idx_prj_requirements_skill_name;
CREATE INDEX idx_prj_requirements_role_name ON prj.prj_requirements (LOWER(role_name));

-- ---------------------------------------------------------------------------
-- 4. Create table for requirement skills
-- ---------------------------------------------------------------------------
CREATE TABLE prj.prj_requirement_skills (
    requirement_id UUID NOT NULL REFERENCES prj.prj_requirements(id) ON DELETE CASCADE,
    skill_name VARCHAR(100) NOT NULL
);

CREATE INDEX idx_prj_requirement_skills_name ON prj.prj_requirement_skills (LOWER(skill_name));
