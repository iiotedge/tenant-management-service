package com.iotmining.services.tms.dto;

import com.iotmining.services.tms.enums.TenantAccessLevel;
import com.iotmining.services.tms.enums.TenantType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TenantSummaryResponse {
    private UUID tenantId;
    private String tenantName;
    private String subscriptionPlan;
    private UUID parentId;
    private TenantType tenantType;
    private TenantAccessLevel accessLevel;
}
