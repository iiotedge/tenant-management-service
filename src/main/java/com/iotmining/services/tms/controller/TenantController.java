package com.iotmining.services.tms.controller;


import com.iotmining.common.base.context.TenantContext;
import com.iotmining.common.base.context.TenantKeySpaceContext;
import com.iotmining.services.tms.dto.CreateTenantRequest;
import com.iotmining.services.tms.dto.CreateTenantResponse;
import com.iotmining.services.tms.dto.TenantSummaryResponse;
import com.iotmining.services.tms.services.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    @Autowired
    private TenantService tenantService;

    @PostMapping
    public CreateTenantResponse createTenant(@RequestBody CreateTenantRequest request) {
        return tenantService.createTenant(request);
    }

//    public Tenant createTenant(@RequestBody Map<String, String> payload) {
//        String tenantName = payload.get("tenantName");
//        String subscriptionPlan = payload.get("subscriptionPlan");
//        return tenantService.createTenant(tenantName, subscriptionPlan);
//    }

    @GetMapping
    public List<TenantSummaryResponse> getAllTenants() {
        return tenantService.getAllTenants();
    }

    @PostMapping("/tenants/{tenantKeyspace}/doSomething")
    public ResponseEntity<?> doSomething(@PathVariable String tenantKeyspace) {
        try {
            TenantKeySpaceContext.setKeyspace(tenantKeyspace);  // 👈 Set at start of request
//            someService.performOperation();             // No need to pass keyspace manually
            return ResponseEntity.ok("Success");
        } finally {
            TenantKeySpaceContext.clear(); // 👈 Always clear to avoid leaks
        }
    }

}

