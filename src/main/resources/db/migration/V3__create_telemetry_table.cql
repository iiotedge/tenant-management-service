CREATE TABLE IF NOT EXISTS telemetry (
    tenant_id UUID,
    device_id UUID,
    timestamp timestamp,
    parameter_name text,
    value double,
    PRIMARY KEY ((tenant_id, device_id), timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC);
