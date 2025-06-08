package com.iotmining.services.tms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class TenantSummaryResponse {
    private UUID tenantId;
    private String tenantName;
    private String subscriptionPlan;
}

