CREATE TABLE IF NOT EXISTS devices (
    tenant_id UUID,
    device_id UUID,
    device_name text,
    device_type text,
    registered_at timestamp,
    PRIMARY KEY (tenant_id, device_id)
);
