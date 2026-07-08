-- =============================================================
-- ASVOSONK — Schema fix v9
--
-- Fixes enum mismatch between Java PaymentStatus and PostgreSQL
-- payment_status:
--   The Java enum PaymentStatus has values: paid, default_status
--   (the literal 'default' is a Java reserved keyword, so the enum
--    constant is named 'default_status').
--   With @Enumerated(STRING) + NAMED_ENUM, Hibernate writes the Java
--   constant name 'default_status', but the DB ENUM only had
--   'paid' and 'default'. Recording a defaulted tontine contribution
--   therefore failed with "invalid input value for enum payment_status".
--
-- Same fix pattern as V5 for attendance_status.
-- =============================================================

ALTER TYPE payment_status ADD VALUE IF NOT EXISTS 'default_status';
