package com.iotmining.services.tms.repository;

import com.iotmining.common.data.tenant.TenantType;
import com.iotmining.services.tms.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    List<Tenant> findByParent_TenantId(UUID parentId);
    List<Tenant> findByParent_TenantIdIn(Collection<UUID> parentIds);
    List<Tenant> findByParent_TenantIdAndTenantType(UUID parentId, TenantType tenantType);
    Optional<Tenant> findById(UUID tenantId);
    List<Tenant> findByParentIsNull();
}
