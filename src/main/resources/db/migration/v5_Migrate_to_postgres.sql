CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    subscription_plan VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    parent_id UUID NULL,  -- <--- Add this!
    FOREIGN KEY (parent_id) REFERENCES tenants(id) ON DELETE CASCADE
);
