-- Add task sequence table
CREATE TABLE tsk.tsk_project_sequences (
    project_id UUID PRIMARY KEY,
    task_prefix VARCHAR(10) NOT NULL,
    current_sequence INT NOT NULL DEFAULT 0
);

-- Add task_key to tasks
ALTER TABLE tsk.tsk_tasks ADD COLUMN task_key VARCHAR(20);

-- Note: existing tasks will have null task_key for now. 
-- In a real production migration, we might backfill them, but we can handle nulls in UI or default to a generated one.
