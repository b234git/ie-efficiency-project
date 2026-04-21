-- Phase 2.3: Add ip_address column to system_logs for audit trail
ALTER TABLE system_logs ADD COLUMN ip_address VARCHAR(45);
