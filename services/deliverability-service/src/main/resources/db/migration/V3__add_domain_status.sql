-- V3 Add Domain Status
ALTER TABLE sender_domains ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
