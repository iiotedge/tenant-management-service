package com.iotmining.services.tms.controller;

import com.iotmining.services.tms.dto.CompanyWithUsersResponse;
import com.iotmining.services.tms.dto.CreateTenantRequest;
import com.iotmining.services.tms.dto.CreateTenantResponse;
import com.iotmining.services.tms.dto.TenantSummaryResponse;
import com.iotmining.services.tms.services.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public CreateTenantResponse createTenant(@RequestBody CreateTenantRequest request) {
        return tenantService.createTenant(request);
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantSummaryResponse> getTenantDetails(@PathVariable("tenantId")  UUID tenantId) {
        TenantSummaryResponse details = tenantService.getTenantSummary(tenantId);
        if (details != null) {
            return ResponseEntity.ok(details);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping
    public List<TenantSummaryResponse> getAllTenants() {
        return tenantService.getAllTenants();
    }

    @GetMapping("/children/{parentId}")
    public List<TenantSummaryResponse> getSubTenants(@PathVariable UUID parentId) {
        return tenantService.getSubTenants(parentId);
    }

//    @GetMapping("/{tenantId}/companies-with-users")
//    public List<CompanyWithUsersResponse> getCompaniesWithUsers(@PathVariable("tenantId") UUID tenantId) {
//        return tenantService.getCompaniesAndUsers(tenantId);
//    }
    @GetMapping("/{tenantId}/companies-with-users")
    public List<CompanyWithUsersResponse> getCompaniesWithUsers(@PathVariable("tenantId") UUID tenantId) {
        return tenantService.getCompaniesAndUsers(tenantId);
    }
}

//package com.iotmining.services.tms.controller;
//
//
//import com.iotmining.common.base.context.TenantContext;
//import com.iotmining.common.base.context.TenantKeySpaceContext;
//import com.iotmining.services.tms.dto.CreateTenantRequest;
//import com.iotmining.services.tms.dto.TenantSummaryResponse;
//import com.iotmining.services.tms.services.TenantService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.UUID;
//
//@RestController
//@RequestMapping("/api/v1/tenants")
//@RequiredArgsConstructor
//public class TenantController {
//    private final TenantService tenantService;
//
//    @PostMapping
//    public TenantSummaryResponse createTenant(@RequestBody CreateTenantRequest request) {
//        return tenantService.createTenant(request);
//    }
//
//    @GetMapping
//    public List<TenantSummaryResponse> getAllTenants() {
//        return tenantService.getAllTenants();
//    }
//
//    @GetMapping("/children/{parentId}")
//    public List<TenantSummaryResponse> getSubTenants(@PathVariable UUID parentId) {
//        return tenantService.getSubTenants(parentId);
//    }
//}
//
////public class TenantController {
////
////    @Autowired
////    private TenantService tenantService;
////
////    @PostMapping
////    public CreateTenantResponse createTenant(@RequestBody CreateTenantRequest request) {
////        return tenantService.createTenant(request);
////    }
////
//////    public Tenant createTenant(@RequestBody Map<String, String> payload) {
//////        String tenantName = payload.get("tenantName");
//////        String subscriptionPlan = payload.get("subscriptionPlan");
//////        return tenantService.createTenant(tenantName, subscriptionPlan);
//////    }
////
////    @GetMapping
////    public List<TenantSummaryResponse> getAllTenants() {
////        return tenantService.getAllTenants();
////    }
////
////    @PostMapping("/tenants/{tenantKeyspace}/doSomething")
////    public ResponseEntity<?> doSomething(@PathVariable String tenantKeyspace) {
////        try {
////            TenantKeySpaceContext.setKeyspace(tenantKeyspace);  // 👈 Set at start of request
//////            someService.performOperation();             // No need to pass keyspace manually
////            return ResponseEntity.ok("Success");
////        } finally {
////            TenantKeySpaceContext.clear(); // 👈 Always clear to avoid leaks
////        }
////    }
////
////}
//
