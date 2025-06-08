package com.iotmining.services.tms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class CreateTenantResponse {
    private UUID tenantId;
    private String tenantName;
    private String subscriptionPlan;
    private String keyspaceName;
    private Instant createdAt;
}
