-- V6: Add missing columns to sender_domains table
-- Fixes remaining schema mismatches with SenderDomain entity

ALTER TABLE sender_domains 
    ADD COLUMN IF NOT EXISTS dkim_selector VARCHAR(255) DEFAULT 'legent',
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
