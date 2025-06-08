package com.iotmining.services.tms.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;


import lombok.*;
@Table("tenants")  // Global table
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Tenant {

    @PrimaryKey
    private UUID tenantId;

    private String tenantName;
    private String subscriptionPlan;
    private Instant createdAt;
}
