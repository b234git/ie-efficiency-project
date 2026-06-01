-- ============================================================
-- V11__rename_salary_routes_to_incentive.sql
-- The salary page/API routes were renamed /salary -> /incentive
-- and the feature was rebranded to "Incentive". feature_key stays
-- 'SALARY' (internal id referenced by role_features grants), so
-- existing role permissions are preserved unchanged.
-- ============================================================

UPDATE features
SET url_patterns = '/incentive/**,/api/v1/incentive/**',
    display_name = 'Incentive'
WHERE feature_key = 'SALARY';
