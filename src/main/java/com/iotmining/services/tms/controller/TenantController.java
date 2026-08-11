package com.iotmining.services.tms.controller;

import com.iotmining.services.tms.dto.CompanyWithUsersResponse;
import com.iotmining.services.tms.dto.CreateTenantRequest;
import com.iotmining.services.tms.dto.CreateTenantResponse;
import com.iotmining.services.tms.dto.TenantSummaryResponse;
import com.iotmining.services.tms.services.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    // Allows both an interactive SUPER_ADMIN action and auth-service's
    // signup-time internal call (see JwtTokenProvider.issueInternalToken).
    @PostMapping
    @PreAuthorize("@tenantSecurity.isSuperAdmin() or hasAuthority('SCOPE_INTERNAL')")
    public ResponseEntity<CreateTenantResponse> createTenant(@RequestBody CreateTenantRequest request) {
        CreateTenantResponse response = tenantService.createTenant(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Internal-service-only: never reachable with an ordinary end-user token,
    // regardless of role. This is auth-service's compensating-transaction call.
    @DeleteMapping("/internal/{tenantId}")
    @PreAuthorize("hasAuthority('SCOPE_INTERNAL')")
    public ResponseEntity<Void> rollbackTenantCreation(@PathVariable("tenantId") UUID tenantId) {
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("@tenantSecurity.isTenantMember(#tenantId.toString())")
    public ResponseEntity<TenantSummaryResponse> getTenantDetails(@PathVariable("tenantId") UUID tenantId) {
        TenantSummaryResponse details = tenantService.getTenantSummary(tenantId);
        if (details != null) {
            return ResponseEntity.ok(details);
        }
        return ResponseEntity.notFound().build();
    }

    // Platform-wide tenant listing - SUPER_ADMIN only.
    @GetMapping
    @PreAuthorize("@tenantSecurity.isSuperAdmin()")
    public ResponseEntity<List<TenantSummaryResponse>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/children/{parentId}")
    @PreAuthorize("@tenantSecurity.isTenantMember(#parentId.toString())")
    public ResponseEntity<List<TenantSummaryResponse>> getSubTenants(@PathVariable("parentId") UUID parentId) {
        return ResponseEntity.ok(tenantService.getSubTenants(parentId));
    }

    @GetMapping("/{tenantId}/companies-with-users")
    @PreAuthorize("@tenantSecurity.isTenantMember(#tenantId.toString())")
    public ResponseEntity<List<CompanyWithUsersResponse>> getCompaniesWithUsers(@PathVariable("tenantId") UUID tenantId) {
        return ResponseEntity.ok(tenantService.getCompaniesAndUsers(tenantId));
    }
}
