-- Create keyspace (only once)
CREATE KEYSPACE IF NOT EXISTS iotmining
WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};

-- Create tenants table
CREATE TABLE IF NOT EXISTS iotmining.tenants (
    tenantId uuid PRIMARY KEY,
    tenantName text,
    subscriptionPlan text,
    createdAt timestamp
);
