package com.iotmining.services.tms.model;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantCassandraDao {

    private final CqlSession session;

    public CqlSession getSession() {
        return session;
    }

    public void insertTenantData(String keyspace, UUID tenantId, String name, String plan, Instant createdAt) {
        String cql = String.format("INSERT INTO %s.tenants (tenantid, tenantname, subscriptionplan, createdat) VALUES (?, ?, ?, ?)", keyspace);
        session.execute(session.prepare(cql).bind(tenantId, name, plan, createdAt));
    }

    // Add more methods here for per-tenant telemetry/devices
}

