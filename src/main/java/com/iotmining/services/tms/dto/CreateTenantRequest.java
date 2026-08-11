package com.iotmining.services.tms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTenantRequest {

    @NotBlank(message = "Tenant name must not be blank")
    @Size(max = 255, message = "Tenant name must be at most 255 characters")
    private String tenantName;

    @Size(max = 64, message = "Subscription plan must be at most 64 characters")
    private String subscriptionPlan;

    private UUID parentId;
    private List<String> roles; // Pass roles here for mapping
}
