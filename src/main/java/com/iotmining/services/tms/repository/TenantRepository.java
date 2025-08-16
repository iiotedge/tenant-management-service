package com.iotmining.services.tms.repository;

import com.iotmining.services.tms.enums.TenantType;
import com.iotmining.services.tms.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    List<Tenant> findByParent_TenantId(UUID parentId);
    List<Tenant> findByParent_TenantIdAndTenantType(UUID parentId, TenantType tenantType);
    Optional<Tenant> findById(UUID tenantId);
}


//package com.iotmining.services.tms.repository;
//
//
//
//import com.iotmining.services.tms.model.Tenant;
//import org.springframework.data.cassandra.repository.CassandraRepository;
//import org.springframework.stereotype.Repository;
//
//import java.util.UUID;
//
//@Repository
//public interface TenantRepository extends CassandraRepository<Tenant, UUID> {
//
//}
//
