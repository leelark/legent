-- V4: Add missing created_by column to sender_domains
-- Fixes: Schema-validation: missing column [created_by] in table [sender_domains]
-- The SenderDomain entity extends BaseEntity requiring created_by

ALTER TABLE sender_domains ADD COLUMN IF NOT EXISTS created_by VARCHAR(26);

-- Populate existing rows with a default system user if needed
-- UPDATE sender_domains SET created_by = '01HSYSTEM00000000000000001' WHERE created_by IS NULL;
