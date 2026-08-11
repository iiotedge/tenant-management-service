package com.iotmining.services.tms.support;

import com.iotmining.common.data.tenant.TenantAccessLevel;
import com.iotmining.common.data.tenant.TenantType;
import com.iotmining.services.tms.model.Tenant;

import java.time.Instant;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {
    }

    public static Tenant tenant(String name, TenantType type, TenantAccessLevel accessLevel, Tenant parent) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(UUID.randomUUID());
        tenant.setTenantName(name);
        tenant.setSubscriptionPlan("BASIC");
        tenant.setCreatedAt(Instant.now());
        tenant.setParent(parent);
        tenant.setTenantType(type);
        tenant.setAccessLevel(accessLevel);
        return tenant;
    }

    public static Tenant organization(String name) {
        return tenant(name, TenantType.ORGANIZATION, TenantAccessLevel.TENANT_ADMIN, null);
    }

    public static Tenant subTenant(String name, Tenant parent) {
        return tenant(name, TenantType.SUB_TENANT, TenantAccessLevel.OPERATIONAL, parent);
    }
}
