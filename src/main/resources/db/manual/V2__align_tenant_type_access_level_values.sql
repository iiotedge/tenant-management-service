-- MANUAL migration - not run automatically.
--
-- This service uses spring.jpa.hibernate.ddl-auto (Flyway is disabled,
-- spring.flyway.enabled: false in application.yml, matching the rest of
-- this platform's current convention). ddl-auto only creates/updates
-- columns and tables; it does not add CHECK constraints or backfill data,
-- so this needs to be run by hand against any existing database before/
-- during a deploy that changes TenantType/TenantAccessLevel's enum values
-- (see com.iotmining.common.data.tenant.TenantType/TenantAccessLevel).
--
-- Safe to skip entirely on a fresh database - the CHECK constraints below
-- are a defense-in-depth data-integrity backstop, not something the
-- application depends on to function.

-- 1. Drop existing checks to prevent conflicts
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_tenant_type_check;
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_access_level_check;

-- 2. Add Constraint for TenantType
ALTER TABLE tenants
ADD CONSTRAINT tenants_tenant_type_check
CHECK (tenant_type IN ('PLATFORM', 'ORGANIZATION', 'SUB_TENANT'));

-- 3. Add Constraint for TenantAccessLevel
ALTER TABLE tenants
ADD CONSTRAINT tenants_access_level_check
CHECK (access_level IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'OPERATIONAL', 'READ_ONLY'));

-- 4. Data cleanup - remap any rows still holding the old enum values
-- (TenantType.COMPANY/USER, TenantAccessLevel.SUPER/ADMIN) before the
-- constraints above are added, or they'll fail to apply.
UPDATE tenants SET tenant_type = 'ORGANIZATION' WHERE tenant_type = 'COMPANY';
UPDATE tenants SET access_level = 'SUPER_ADMIN' WHERE access_level = 'SUPER';
UPDATE tenants SET access_level = 'TENANT_ADMIN' WHERE access_level = 'ADMIN';
