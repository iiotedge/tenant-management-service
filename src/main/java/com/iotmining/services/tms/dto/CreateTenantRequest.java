package com.iotmining.services.tms.dto;


import lombok.Data;

@Data
public class CreateTenantRequest {
    private String tenantName;
    private String subscriptionPlan;
}
