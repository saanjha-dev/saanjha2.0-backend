ALTER TABLE auth.auth_trusted_devices
    ADD COLUMN created_by VARCHAR(255) DEFAULT 'SYSTEM',
    ADD COLUMN updated_by VARCHAR(255) DEFAULT 'SYSTEM';
