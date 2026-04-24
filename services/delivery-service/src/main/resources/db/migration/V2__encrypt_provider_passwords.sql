-- V2: Migrate SMTP providers to use encrypted passwords only
-- Removes plaintext password_hash column and adds encryption metadata columns

-- Add encrypted password columns if they don't exist
ALTER TABLE smtp_providers
    ADD COLUMN IF NOT EXISTS encrypted_password TEXT,
    ADD COLUMN IF NOT EXISTS encryption_iv VARCHAR(255),
    ADD COLUMN IF NOT EXISTS encryption_salt VARCHAR(255);

-- Remove the legacy password_hash column (plaintext storage)
-- Note: Any existing plaintext passwords will need to be re-entered through the API
-- with proper encryption, as we cannot securely convert plaintext to encrypted
-- without the encryption key being present during migration.
ALTER TABLE smtp_providers DROP COLUMN IF EXISTS password_hash;

-- Add comment explaining the encryption requirements
COMMENT ON TABLE smtp_providers IS 'SMTP provider configurations. Passwords must be encrypted using AES-256-GCM via the CredentialEncryptionService before storage.';
