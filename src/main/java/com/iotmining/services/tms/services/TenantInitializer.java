package com.iotmining.services.tms.services;

import com.iotmining.common.data.tenant.TenantAccessLevel;
import com.iotmining.common.data.tenant.TenantType;
import com.iotmining.services.tms.model.Tenant;
import com.iotmining.services.tms.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TenantInitializer {

    private final TenantRepository tenantRepository;

    // Shared Constant for the System Tenant
    public static final UUID SYSTEM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @PostConstruct
    @Transactional
    public void init() {
        if (tenantRepository.existsById(SYSTEM_TENANT_ID)) {
            log.info("TMS Boot: Platform Tenant already exists.");
            return;
        }

        log.info("TMS Boot: Creating Platform (System) Tenant...");

        Tenant systemTenant = new Tenant();
        systemTenant.setTenantId(SYSTEM_TENANT_ID);
        systemTenant.setTenantName("IIoTEdge Platform");
        systemTenant.setSubscriptionPlan("INTERNAL");
        systemTenant.setCreatedAt(Instant.now());
        systemTenant.setTenantType(TenantType.PLATFORM);
        systemTenant.setAccessLevel(TenantAccessLevel.SUPER_ADMIN);
        systemTenant.setParent(null);

        tenantRepository.save(systemTenant);
        log.info("TMS Boot: Platform Tenant Created successfully.");
    }
}