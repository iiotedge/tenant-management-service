-- Create index on tenantName
CREATE INDEX IF NOT EXISTS idx_tenant_name ON iotmining.tenants (tenantName);
