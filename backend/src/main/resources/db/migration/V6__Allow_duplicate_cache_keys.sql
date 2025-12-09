-- Allow multiple users to upload the same file (same cache_key)
-- This enables analysis result reuse across different users
-- FR-023: Reuse analysis results for duplicate files to save cost and time

-- Drop the existing UNIQUE constraint on cache_key
ALTER TABLE pdf_documents DROP INDEX cache_key;

-- Add composite unique constraint to prevent same user from having duplicate records
-- This allows different users to upload the same file while preventing duplicates per user
ALTER TABLE pdf_documents ADD CONSTRAINT unique_user_cache_key UNIQUE (user_id, cache_key);