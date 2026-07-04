-- =============================================================
-- ASVOSONK — Initial data (seed) v1
-- Run after V1__asvosonk_schema.sql
-- =============================================================

-- =============================================================
-- 1. PERMISSIONS
-- =============================================================

INSERT INTO permission (code, description) VALUES
-- Member management
('MEMBER_VIEW',                 'View member list and details'),
('MEMBER_CREATE',               'Register a new member'),
('MEMBER_EDIT',                 'Edit member information'),
('MEMBER_CHANGE_STATUS',        'Suspend / reactivate / mark member as resigned or deceased'),
('MEMBERSHIP_FEE_RECORD',       'Record membership fee payments'),

-- Session (presence)
('SESSION_VIEW',                'View sessions and attendance'),
('SESSION_CREATE',              'Create a new session'),
('SESSION_EDIT',                'Edit a session before closing'),
('SESSION_CLOSE',               'Close a session and trigger all automatic calculations'),
('ATTENDANCE_RECORD',           'Record attendance and contributions for a session'),

-- Grand tontine
('TONTINE_VIEW',                'View tontine tours and contributions'),
('TONTINE_TOUR_CREATE',         'Create a new tontine tour and register participants'),
('TONTINE_CONTRIBUTION_RECORD', 'Record weekly grand tontine contributions'),

-- Annual bank
('BANK_VIEW',                   'View savings and loans'),
('BANK_SAVING_RECORD',          'Record a savings deposit'),
('BANK_LOAN_CREATE',            'Create a loan after eligibility check'),
('BANK_LOAN_REPAYMENT',         'Record a loan repayment'),

-- Cashboxes
('CASHBOX_VIEW',                'View cashbox balances and movement history'),
('CASHBOX_MANUAL_MOVEMENT',     'Record a manual cashbox movement with justification'),

-- Sanctions
('SANCTION_VIEW',               'View sanctions list'),
('SANCTION_CREATE_MANUAL',      'Create a manual disciplinary sanction'),
('SANCTION_RECORD_PAYMENT',     'Record a sanction cash payment'),
('SANCTION_CANCEL',             'Cancel or void a sanction - president only'),

-- Users
('USER_VIEW',                   'View user accounts'),
('USER_CREATE',                 'Create a user account'),
('USER_EDIT',                   'Edit a user account'),
('USER_DEACTIVATE',             'Deactivate a user account'),

-- Reports
('REPORT_SESSION',              'Generate session report'),
('REPORT_MONTHLY',              'Generate monthly cashbox statement'),
('REPORT_QUARTERLY',            'Generate quarterly audit report'),

-- Search
('SEARCH_GLOBAL',               'Use the transversal search across all modules');

-- =============================================================
-- 2. ROLES
-- =============================================================

INSERT INTO role (name, description) VALUES
('PRESIDENT',  'Full access to all modules and administrative actions'),
('SECRETARY',  'Full access except Annual Bank module'),
('TREASURER',  'Full access to all modules'),
('AUDITOR',    'Full read and control access - Commissaire aux comptes'),
('CENSOR',     'Manages discipline and sanctions');

-- =============================================================
-- 3. ROLE — PERMISSION MAPPING
-- =============================================================

-- PRESIDENT: all permissions
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'PRESIDENT';

-- SECRETARY: all permissions EXCEPT annual bank
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'SECRETARY'
  AND p.code NOT IN (
      'BANK_VIEW', 'BANK_SAVING_RECORD',
      'BANK_LOAN_CREATE', 'BANK_LOAN_REPAYMENT'
  );

-- TREASURER: all permissions
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'TREASURER';

-- AUDITOR: read-only across all modules + reports
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'AUDITOR'
  AND p.code IN (
      'MEMBER_VIEW', 'SESSION_VIEW', 'TONTINE_VIEW',
      'BANK_VIEW', 'CASHBOX_VIEW', 'SANCTION_VIEW',
      'REPORT_SESSION', 'REPORT_MONTHLY', 'REPORT_QUARTERLY',
      'SEARCH_GLOBAL'
  );

-- CENSOR: view everything + full sanction management + cashbox + reports
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'CENSOR'
  AND p.code IN (
      'MEMBER_VIEW', 'SESSION_VIEW', 'ATTENDANCE_RECORD',
      'TONTINE_VIEW', 'TONTINE_CONTRIBUTION_RECORD',
      'BANK_VIEW', 'CASHBOX_VIEW', 'CASHBOX_MANUAL_MOVEMENT',
      'SANCTION_VIEW', 'SANCTION_CREATE_MANUAL', 'SANCTION_RECORD_PAYMENT',
      'REPORT_SESSION', 'REPORT_MONTHLY', 'SEARCH_GLOBAL'
  );

-- =============================================================
-- 4. CASHBOXES
-- =============================================================

INSERT INTO cashbox (type, balance) VALUES
('development', 0),
('sanction',    0),
('beverage',    0),
('bank',        0);

-- =============================================================
-- 5. DEFAULT ADMIN USER
-- login    : admin
-- password : Admin@2024  (BCrypt strength 12)
-- ⚠️  Changer ce mot de passe dès la première connexion
-- =============================================================

INSERT INTO member (
    full_name,
    phone,
    join_date,
    is_resident,
    status
)
VALUES (
           'Administrateur',
           NULL,
           CURRENT_DATE,
           TRUE,
           'active'
       );

INSERT INTO app_user (
    login,
    password_hash,
    role_id,
    member_id,
    active
)
SELECT
    'admin',
    '$2a$12$FjcCAT5Lhki/yRZocf6rB.VB9GIJhjzkkOVFmmPOiL8APsZgsH23y',
    r.id,
    m.id,
    TRUE
FROM role r
         CROSS JOIN (
    SELECT id
    FROM member
    WHERE full_name = 'Administrateur'
        LIMIT 1
) m
WHERE r.name = 'PRESIDENT';