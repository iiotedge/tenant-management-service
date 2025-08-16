package com.iotmining.services.tms.model;

import com.iotmining.services.tms.enums.TenantAccessLevel;
import com.iotmining.services.tms.enums.TenantType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Tenant {

    @Id
    @Column(name = "id")
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String tenantName;

    @Column(name = "subscription_plan", nullable = false)
    private String subscriptionPlan;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Tenant parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_type", nullable = false)
    private TenantType tenantType;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private TenantAccessLevel accessLevel;
}



//package com.iotmining.services.tms.model;
//
//import lombok.AllArgsConstructor;
//import lombok.NoArgsConstructor;
//import org.springframework.data.cassandra.core.mapping.PrimaryKey;
//import org.springframework.data.cassandra.core.mapping.Table;
//
//import java.time.Instant;
//import java.util.UUID;
//
//
//import lombok.*;
//@Table("tenants")  // Global table
//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//public class Tenant {
//
//    @PrimaryKey
//    private UUID tenantId;
//
//    private String tenantName;
//    private String subscriptionPlan;
//    private Instant createdAt;
//}
