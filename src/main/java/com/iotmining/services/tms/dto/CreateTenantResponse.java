package com.iotmining.services.tms.dto;

import com.iotmining.services.tms.enums.TenantType;
import com.iotmining.services.tms.enums.TenantAccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTenantResponse {
    private UUID tenantId;
    private String tenantName;
    private String subscriptionPlan;
    private String keyspaceName;
    private Instant createdAt;
    private TenantType tenantType;
    private TenantAccessLevel accessLevel;
}
