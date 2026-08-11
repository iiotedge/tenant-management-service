package com.iotmining.services.tms.services;

import com.iotmining.common.data.tenant.TenantAccessLevel;
import com.iotmining.common.data.tenant.TenantType;
import com.iotmining.services.tms.dto.CompanyWithUsersResponse;
import com.iotmining.services.tms.dto.CreateTenantRequest;
import com.iotmining.services.tms.dto.CreateTenantResponse;
import com.iotmining.services.tms.dto.TenantSummaryResponse;

import com.iotmining.services.tms.model.Tenant;
import com.iotmining.services.tms.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;

    // NOTE: TMS cannot inject UserRepository directly because Users belong to Auth Service.
    // If you need users populated here, you must use a Feign Client (REST Call) to Auth Service.

    private static TenantType determineTenantType(List<String> roles) {
        if (roles == null || roles.isEmpty()) return TenantType.ORGANIZATION;
        if (roles.contains("ROLE_SUPER_ADMIN")) return TenantType.ORGANIZATION;
        if (roles.contains("ROLE_ADMIN")) return TenantType.SUB_TENANT;
        return TenantType.ORGANIZATION;
    }

    private static TenantAccessLevel determineAccessLevel(TenantType tenantType) {
        return switch (tenantType) {
            case PLATFORM -> TenantAccessLevel.SUPER_ADMIN;
            case ORGANIZATION -> TenantAccessLevel.TENANT_ADMIN;
            case SUB_TENANT -> TenantAccessLevel.OPERATIONAL;
        };
    }

    @Transactional(readOnly = true)
    public TenantSummaryResponse getTenantSummary(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(this::mapToSummary)
                .orElse(null);
    }

    @Transactional
    public CreateTenantResponse createTenant(CreateTenantRequest request) {
        log.info("Attempting to create tenant: {}", request.getTenantName());

        Tenant parent = null;
        if (request.getParentId() != null) {
            parent = tenantRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent tenant not found with ID: " + request.getParentId()));
        }

        TenantType tenantType = determineTenantType(request.getRoles());

        if (parent != null) {
            validateHierarchy(parent.getTenantType(), tenantType);
        } else if (tenantType == TenantType.SUB_TENANT) {
            throw new IllegalArgumentException("A SUB_TENANT must have a parent ORGANIZATION.");
        }

        TenantAccessLevel accessLevel = determineAccessLevel(tenantType);
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setTenantName(request.getTenantName());
        tenant.setSubscriptionPlan(request.getSubscriptionPlan() != null ? request.getSubscriptionPlan() : "BASIC");
        tenant.setCreatedAt(now);
        tenant.setParent(parent);
        tenant.setTenantType(tenantType);
        tenant.setAccessLevel(accessLevel);

        Tenant savedTenant = tenantRepository.save(tenant);

        String keyspaceName = savedTenant.getTenantName().toLowerCase().replaceAll("[^a-z0-9]", "") + "_ks";
        log.info("Tenant created: ID={}, Keyspace={}", savedTenant.getTenantId(), keyspaceName);

        return new CreateTenantResponse(
                savedTenant.getTenantId(),
                savedTenant.getTenantName(),
                savedTenant.getSubscriptionPlan(),
                keyspaceName,
                now,
                tenantType,
                accessLevel
        );
    }

    @Transactional
    public void deleteTenant(UUID tenantId) {
        if (tenantRepository.existsById(tenantId)) {
            tenantRepository.deleteById(tenantId);
            log.warn("Tenant deleted: {}", tenantId);
        }
    }

    @Transactional(readOnly = true)
    public List<TenantSummaryResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

//    @Transactional(readOnly = true)
//    public List<TenantSummaryResponse> getSubTenants(UUID parentId) {
//        return tenantRepository.findByParent_TenantId(parentId).stream()
//                .map(this::mapToSummary)
//                .collect(Collectors.toList());
//    }

    @Transactional(readOnly = true)
    public List<TenantSummaryResponse> getSubTenants(UUID parentId) {
        // 1. Define the Virtual Root ID
        UUID rootId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        List<Tenant> tenants;

        // 2. If ID is NULL or ZERO -> Fetch Top-Level Tenants (where parent is null)
        if (parentId == null || parentId.equals(rootId)) {
            tenants = tenantRepository.findByParentIsNull();
        } else {
            // 3. Otherwise fetch children of the specific parent
            tenants = tenantRepository.findByParent_TenantId(parentId);
        }

        return tenants.stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public List<CompanyWithUsersResponse> getCompaniesAndUsers(UUID tenantId) {
        Tenant node = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        List<CompanyWithUsersResponse> result = new ArrayList<>();
        result.add(buildCompanyTreeRecursive(node));
        return result;
    }

    // --- Helper Methods ---

    private TenantSummaryResponse mapToSummary(Tenant t) {
        return new TenantSummaryResponse(
                t.getTenantId(),
                t.getTenantName(),
                t.getSubscriptionPlan(),
                t.getParent() != null ? t.getParent().getTenantId() : null,
                t.getTenantType(),
                t.getAccessLevel()
        );
    }

    private void validateHierarchy(TenantType parentType, TenantType childType) {
        if (parentType == TenantType.ORGANIZATION && childType == TenantType.ORGANIZATION) {
            throw new IllegalStateException("An ORGANIZATION cannot be a child of another ORGANIZATION.");
        }
        if (parentType == TenantType.SUB_TENANT) {
            throw new IllegalStateException("A SUB_TENANT cannot be a parent.");
        }
    }

    // --- Recursive Tree Builder (Fixed: No User DB Access) ---
    private CompanyWithUsersResponse buildCompanyTreeRecursive(Tenant node) {

        // 1. Users: We CANNOT fetch them directly here (Microservice Boundary).
        // Return empty list for now. The Frontend or Auth Service should merge this data.
        List<TenantSummaryResponse> userDtos = new ArrayList<>();

        // 2. Sub-Tenants (Divisions/Sites) - We CAN fetch these as they are in TMS DB
        List<Tenant> subCompanies = tenantRepository.findByParent_TenantId(node.getTenantId());

        // 3. Recursive call for Sub-Tenants
        List<CompanyWithUsersResponse> subCompanyDtos = subCompanies.stream()
                .map(this::buildCompanyTreeRecursive)
                .collect(Collectors.toList());

        // 4. Wrap this node and its children
        return new CompanyWithUsersResponse(
                mapToSummary(node),
                userDtos,
                subCompanyDtos
        );
    }
}

//package com.iotmining.services.tms.services;
//
//import com.iotmining.services.tms.dto.CompanyWithUsersResponse;
//import com.iotmining.services.tms.dto.CreateTenantRequest;
//import com.iotmining.services.tms.dto.CreateTenantResponse;
//import com.iotmining.services.tms.dto.TenantSummaryResponse;
//import com.iotmining.services.tms.enums.TenantAccessLevel;
//import com.iotmining.services.tms.enums.TenantType;
//import com.iotmining.services.tms.model.Tenant;
//import com.iotmining.services.tms.repository.TenantRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Instant;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class TenantService {
//
//    private final TenantRepository tenantRepository;
//
//    /**
//     * Determines the tenant type based on the roles provided during registration.
//     */
//    private static TenantType determineTenantType(List<String> roles) {
//        if (roles == null || roles.isEmpty()) return TenantType.USER;
//        if (roles.contains("ROLE_SUPER_ADMIN")) {
//            return TenantType.ORGANIZATION;
//        } else if (roles.contains("ROLE_ADMIN")) {
//            return TenantType.COMPANY;
//        } else {
//            return TenantType.USER;
//        }
//    }
//
//    /**
//     * Maps TenantType to a specific AccessLevel.
//     */
//    private static TenantAccessLevel determineAccessLevel(TenantType tenantType) {
//        return switch (tenantType) {
//            case ORGANIZATION -> TenantAccessLevel.SUPER;
//            case COMPANY -> TenantAccessLevel.ADMIN;
//            case USER -> TenantAccessLevel.READ_ONLY;
//        };
//    }
//
//    @Transactional(readOnly = true)
//    public TenantSummaryResponse getTenantSummary(UUID tenantId) {
//        return tenantRepository.findById(tenantId)
//                .map(this::mapToSummary)
//                .orElse(null);
//    }
//
//    @Transactional
//    public CreateTenantResponse createTenant(CreateTenantRequest request) {
//        log.info("Attempting to create tenant: {}", request.getTenantName());
//
//        Tenant parent = null;
//        if (request.getParentId() != null) {
//            parent = tenantRepository.findById(request.getParentId())
//                    .orElseThrow(() -> new IllegalArgumentException("Parent tenant not found with ID: " + request.getParentId()));
//
//            // Production Check: Ensure the parent is actually allowed to have children (e.g. a USER type cannot be a parent)
//            if (parent.getTenantType() == TenantType.USER) {
//                throw new IllegalStateException("A USER type tenant cannot be a parent tenant.");
//            }
//        }
//
//        TenantType tenantType = determineTenantType(request.getRoles());
//
//        // Validation: If parent exists, ensure hierarchy makes sense (Organization -> Company -> User)
//        if (parent != null) {
//            validateHierarchy(parent.getTenantType(), tenantType);
//        }
//
//        TenantAccessLevel accessLevel = determineAccessLevel(tenantType);
//        UUID tenantId = UUID.randomUUID();
//        Instant now = Instant.now();
//
//        Tenant tenant = new Tenant();
//        tenant.setTenantId(tenantId);
//        tenant.setTenantName(request.getTenantName());
//        tenant.setSubscriptionPlan(request.getSubscriptionPlan() != null ? request.getSubscriptionPlan() : "BASIC");
//        tenant.setCreatedAt(now);
//        tenant.setParent(parent);
//        tenant.setTenantType(tenantType);
//        tenant.setAccessLevel(accessLevel);
//
//        Tenant savedTenant = tenantRepository.save(tenant);
//        log.info("Tenant created successfully: ID={}, Name={}, Type={}", savedTenant.getTenantId(), savedTenant.getTenantName(), savedTenant.getTenantType());
//
//        // Keyspace naming convention (sanitized)
//        String keyspaceName = tenant.getTenantName().toLowerCase().replaceAll("[^a-z0-9]", "") + "_ks";
//
//        return new CreateTenantResponse(
//                savedTenant.getTenantId(),
//                savedTenant.getTenantName(),
//                savedTenant.getSubscriptionPlan(),
//                keyspaceName,
//                now,
//                tenantType,
//                accessLevel
//        );
//    }
//
//    /**
//     * Deletes a tenant.
//     * IMPORTANT: This is primarily used for the Rollback mechanism from Auth Service.
//     */
//    @Transactional
//    public void deleteTenant(UUID tenantId) {
//        if (tenantRepository.existsById(tenantId)) {
//            tenantRepository.deleteById(tenantId);
//            log.warn("Tenant deleted (Rollback or Manual): {}", tenantId);
//        } else {
//            log.warn("Attempted to delete non-existent tenant: {}", tenantId);
//        }
//    }
//
//    @Transactional(readOnly = true)
//    public List<TenantSummaryResponse> getAllTenants() {
//        return tenantRepository.findAll()
//                .stream()
//                .map(this::mapToSummary)
//                .collect(Collectors.toList());
//    }
//
//    @Transactional(readOnly = true)
//    public List<TenantSummaryResponse> getSubTenants(UUID parentId) {
//        return tenantRepository.findByParent_TenantId(parentId)
//                .stream()
//                .map(this::mapToSummary)
//                .collect(Collectors.toList());
//    }
//
//    /**
//     * Unified: Returns a full tree for any org/company/user.
//     */
//    @Transactional(readOnly = true)
//    public List<CompanyWithUsersResponse> getCompaniesAndUsers(UUID tenantId) {
//        Tenant node = tenantRepository.findById(tenantId)
//                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
//
//        List<CompanyWithUsersResponse> result = new ArrayList<>();
//        // Start recursion at the root node
//        result.add(buildCompanyTreeRecursive(node));
//        return result;
//    }
//
//    // --- Helper Methods ---
//
//    private TenantSummaryResponse mapToSummary(Tenant t) {
//        return new TenantSummaryResponse(
//                t.getTenantId(),
//                t.getTenantName(),
//                t.getSubscriptionPlan(),
//                t.getParent() != null ? t.getParent().getTenantId() : null,
//                t.getTenantType(),
//                t.getAccessLevel()
//        );
//    }
//
//    private void validateHierarchy(TenantType parentType, TenantType childType) {
//        if (parentType == TenantType.ORGANIZATION && childType == TenantType.ORGANIZATION) {
//            throw new IllegalStateException("Organization cannot be a child of another Organization");
//        }
//        if (parentType == TenantType.COMPANY && (childType == TenantType.ORGANIZATION || childType == TenantType.COMPANY)) {
//            throw new IllegalStateException("Company cannot be a parent to an Organization or another Company");
//        }
//        if (parentType == TenantType.USER) {
//            throw new IllegalStateException("User cannot be a parent");
//        }
//    }
//
//    // --- Recursive tree builder ---
//    private CompanyWithUsersResponse buildCompanyTreeRecursive(Tenant node) {
//        // Optimization: Fetch children in batch if possible, but standard JPA lazy loading works for simple depth
//
//        // 1. Get Users (Leaf nodes usually)
//        List<Tenant> users = tenantRepository.findByParent_TenantIdAndTenantType(node.getTenantId(), TenantType.USER);
//        List<TenantSummaryResponse> userDtos = users.stream()
//                .map(this::mapToSummary)
//                .collect(Collectors.toList());
//
//        // 2. Get Sub-companies (Intermediate nodes)
//        List<Tenant> subCompanies = tenantRepository.findByParent_TenantIdAndTenantType(node.getTenantId(), TenantType.COMPANY);
//
//        // 3. Recursive call for sub-companies
//        List<CompanyWithUsersResponse> subCompanyDtos = subCompanies.stream()
//                .map(this::buildCompanyTreeRecursive)
//                .collect(Collectors.toList());
//
//        // 4. Wrap this node and children
//        return new CompanyWithUsersResponse(
//                mapToSummary(node),
//                userDtos,
//                subCompanyDtos
//        );
//    }
//}
//
////package com.iotmining.services.tms.services;
////
////import com.iotmining.services.tms.dto.CompanyWithUsersResponse;
////import com.iotmining.services.tms.dto.CreateTenantRequest;
////import com.iotmining.services.tms.dto.CreateTenantResponse;
////import com.iotmining.services.tms.dto.TenantSummaryResponse;
////import com.iotmining.services.tms.enums.TenantAccessLevel;
////import com.iotmining.services.tms.enums.TenantType;
////import com.iotmining.services.tms.model.Tenant;
////import com.iotmining.services.tms.repository.TenantRepository;
////import lombok.RequiredArgsConstructor;
////import org.springframework.stereotype.Service;
////import java.time.Instant;
////import java.util.*;
////import java.util.stream.Collectors;
////
////@Service
////@RequiredArgsConstructor
////public class TenantService {
////
////    private final TenantRepository tenantRepository;
////
////    private static TenantType determineTenantType(List<String> roles) {
////        if (roles == null) return TenantType.USER;
////        if (roles.contains("ROLE_SUPER_ADMIN")) {
////            return TenantType.ORGANIZATION;
////        } else if (roles.contains("ROLE_ADMIN")) {
////            return TenantType.COMPANY;
////        } else {
////            return TenantType.USER;
////        }
////    }
////
////    private static TenantAccessLevel determineAccessLevel(TenantType tenantType) {
////        return switch (tenantType) {
////            case ORGANIZATION -> TenantAccessLevel.SUPER;
////            case COMPANY -> TenantAccessLevel.ADMIN;
////            case USER -> TenantAccessLevel.READ_ONLY;
////        };
////    }
////
////    public TenantSummaryResponse getTenantSummary(UUID tenantId) {
////        return tenantRepository.findById(tenantId)
////                .map(t -> new TenantSummaryResponse(
////                        t.getTenantId(),
////                        t.getTenantName(),
////                        t.getSubscriptionPlan(),
////                        t.getParent() != null ? t.getParent().getTenantId() : null,
////                        t.getTenantType(),
////                        t.getAccessLevel()
////                ))
////                .orElse(null);
////    }
////
////    public CreateTenantResponse createTenant(CreateTenantRequest request) {
////        Tenant parent = null;
////        if (request.getParentId() != null) {
////            parent = tenantRepository.findById(request.getParentId())
////                    .orElseThrow(() -> new IllegalArgumentException("Parent tenant not found"));
////        }
////
////        TenantType tenantType = determineTenantType(request.getRoles());
////        TenantAccessLevel accessLevel = determineAccessLevel(tenantType);
////
////        UUID tenantId = UUID.randomUUID();
////        Instant now = Instant.now();
////
////        Tenant tenant = new Tenant();
////        tenant.setTenantId(tenantId);
////        tenant.setTenantName(request.getTenantName());
////        tenant.setSubscriptionPlan(request.getSubscriptionPlan());
////        tenant.setCreatedAt(now);
////        tenant.setParent(parent);
////        tenant.setTenantType(tenantType);
////        tenant.setAccessLevel(accessLevel);
////
////        tenantRepository.save(tenant);
////
////        String keyspaceName = tenant.getTenantName().toLowerCase().replaceAll("[^a-z0-9]", "") + "_ks";
////
////        return new CreateTenantResponse(
////                tenant.getTenantId(),
////                tenant.getTenantName(),
////                tenant.getSubscriptionPlan(),
////                keyspaceName,
////                now,
////                tenantType,
////                accessLevel
////        );
////    }
////
////    public List<TenantSummaryResponse> getAllTenants() {
////        return tenantRepository.findAll()
////                .stream()
////                .map(t -> new TenantSummaryResponse(
////                        t.getTenantId(),
////                        t.getTenantName(),
////                        t.getSubscriptionPlan(),
////                        t.getParent() != null ? t.getParent().getTenantId() : null,
////                        t.getTenantType(),
////                        t.getAccessLevel()
////                )).collect(Collectors.toList());
////    }
////
////    public List<TenantSummaryResponse> getSubTenants(UUID parentId) {
////        return tenantRepository.findByParent_TenantId(parentId)
////                .stream()
////                .map(t -> new TenantSummaryResponse(
////                        t.getTenantId(),
////                        t.getTenantName(),
////                        t.getSubscriptionPlan(),
////                        t.getParent() != null ? t.getParent().getTenantId() : null,
////                        t.getTenantType(),
////                        t.getAccessLevel()
////                )).collect(Collectors.toList());
////    }
////
////    /**
////     * Unified: Returns a full tree for any org/company/user.
////     * For ORG: returns itself (company: field), its direct users (users: field), and companies with their sub-users/subcompanies.
////     * For COMPANY: returns itself, direct users, subcompanies/users recursively.
////     * For USER: returns itself only.
////     */
////    public List<CompanyWithUsersResponse> getCompaniesAndUsers(UUID tenantId) {
////        Tenant node = tenantRepository.findById(tenantId)
////                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
////
////        List<CompanyWithUsersResponse> result = new ArrayList<>();
////
////        // Start recursion at the root node
////        result.add(buildCompanyTreeRecursive(node));
////
////        return result;
////    }
////
////    // --- Recursive tree builder ---
////    private CompanyWithUsersResponse buildCompanyTreeRecursive(Tenant node) {
////        // Users directly under this node
////        List<Tenant> users = tenantRepository.findByParent_TenantIdAndTenantType(node.getTenantId(), TenantType.USER);
////        List<TenantSummaryResponse> userDtos = users.stream()
////                .map(u -> new TenantSummaryResponse(
////                        u.getTenantId(), u.getTenantName(), u.getSubscriptionPlan(),
////                        u.getParent() != null ? u.getParent().getTenantId() : null,
////                        u.getTenantType(), u.getAccessLevel()
////                )).collect(Collectors.toList());
////
////        // Sub-companies under this node
////        List<Tenant> subCompanies = tenantRepository.findByParent_TenantIdAndTenantType(node.getTenantId(), TenantType.COMPANY);
////
////        List<CompanyWithUsersResponse> subCompanyDtos = subCompanies.stream()
////                .map(this::buildCompanyTreeRecursive)
////                .collect(Collectors.toList());
////
////        // Wrap this node and children
////        return new CompanyWithUsersResponse(
////                new TenantSummaryResponse(
////                        node.getTenantId(), node.getTenantName(), node.getSubscriptionPlan(),
////                        node.getParent() != null ? node.getParent().getTenantId() : null,
////                        node.getTenantType(), node.getAccessLevel()
////                ),
////                userDtos,
////                subCompanyDtos
////        );
////    }
////}
////
//////package com.iotmining.services.tms.services;
//////
//////import com.iotmining.services.tms.dto.CompanyWithUsersResponse;
//////import com.iotmining.services.tms.dto.CreateTenantRequest;
//////import com.iotmining.services.tms.dto.CreateTenantResponse;
//////import com.iotmining.services.tms.dto.TenantSummaryResponse;
//////import com.iotmining.services.tms.enums.TenantAccessLevel;
//////import com.iotmining.services.tms.enums.TenantType;
//////import com.iotmining.services.tms.model.*;
//////import com.iotmining.services.tms.repository.TenantRepository;
//////import lombok.RequiredArgsConstructor;
//////import org.springframework.stereotype.Service;
//////import java.time.Instant;
//////import java.util.ArrayList;
//////import java.util.List;
//////import java.util.UUID;
//////import java.util.stream.Collectors;
//////
//////@Service
//////@RequiredArgsConstructor
//////public class TenantService {
//////
//////    private final TenantRepository tenantRepository;
//////
//////    private static TenantType determineTenantType(List<String> roles) {
//////        if (roles == null) return TenantType.USER;
//////        if (roles.contains("ROLE_SUPER_ADMIN")) {
//////            return TenantType.ORGANIZATION;
//////        } else if (roles.contains("ROLE_ADMIN")) {
//////            return TenantType.COMPANY;
//////        } else {
//////            return TenantType.USER;
//////        }
//////    }
//////
//////    private static TenantAccessLevel determineAccessLevel(TenantType tenantType) {
//////        return switch (tenantType) {
//////            case ORGANIZATION -> TenantAccessLevel.SUPER;
//////            case COMPANY -> TenantAccessLevel.ADMIN;
//////            case USER -> TenantAccessLevel.READ_ONLY;
//////        };
//////    }
//////
//////    public CreateTenantResponse createTenant(CreateTenantRequest request) {
//////        Tenant parent = null;
//////        if (request.getParentId() != null) {
//////            parent = tenantRepository.findById(request.getParentId())
//////                    .orElseThrow(() -> new IllegalArgumentException("Parent tenant not found"));
//////        }
//////
//////        TenantType tenantType = determineTenantType(request.getRoles());
//////        TenantAccessLevel accessLevel = determineAccessLevel(tenantType);
//////
//////        UUID tenantId = UUID.randomUUID();
//////        Instant now = Instant.now();
//////
//////        Tenant tenant = new Tenant();
//////        tenant.setTenantId(tenantId);
//////        tenant.setTenantName(request.getTenantName());
//////        tenant.setSubscriptionPlan(request.getSubscriptionPlan());
//////        tenant.setCreatedAt(now);
//////        tenant.setParent(parent);
//////        tenant.setTenantType(tenantType);
//////        tenant.setAccessLevel(accessLevel);
//////
//////        tenantRepository.save(tenant);
//////
//////        String keyspaceName = tenant.getTenantName().toLowerCase().replaceAll("[^a-z0-9]", "") + "_ks";
//////
//////        return new CreateTenantResponse(
//////                tenant.getTenantId(),
//////                tenant.getTenantName(),
//////                tenant.getSubscriptionPlan(),
//////                keyspaceName,
//////                now,
//////                tenantType,
//////                accessLevel
//////        );
//////    }
//////
//////    public List<TenantSummaryResponse> getAllTenants() {
//////        return tenantRepository.findAll()
//////                .stream()
//////                .map(t -> new TenantSummaryResponse(
//////                        t.getTenantId(),
//////                        t.getTenantName(),
//////                        t.getSubscriptionPlan(),
//////                        t.getParent() != null ? t.getParent().getTenantId() : null,
//////                        t.getTenantType(),
//////                        t.getAccessLevel()
//////                )).collect(Collectors.toList());
//////    }
//////
//////    public List<TenantSummaryResponse> getSubTenants(UUID parentId) {
//////        return tenantRepository.findByParent_TenantId(parentId)
//////                .stream()
//////                .map(t -> new TenantSummaryResponse(
//////                        t.getTenantId(),
//////                        t.getTenantName(),
//////                        t.getSubscriptionPlan(),
//////                        t.getParent() != null ? t.getParent().getTenantId() : null,
//////                        t.getTenantType(),
//////                        t.getAccessLevel()
//////                )).collect(Collectors.toList());
//////    }
//////
////////    public List<CompanyWithUsersResponse> getCompaniesAndUsers(UUID parentId) {
////////        // 1. Get all COMPANY tenants under the given parent (likely ORGANIZATION)
////////        List<Tenant> companies = tenantRepository.findByParent_TenantIdAndTenantType(parentId, TenantType.COMPANY);
////////
////////        // 2. For each company, get its USER children
////////        List<CompanyWithUsersResponse> result = new ArrayList<>();
////////        for (Tenant company : companies) {
////////            List<Tenant> users = tenantRepository.findByParent_TenantIdAndTenantType(company.getTenantId(), TenantType.USER);
////////            List<TenantSummaryResponse> userDtos = users.stream()
////////                    .map(u -> new TenantSummaryResponse(
////////                            u.getTenantId(),
////////                            u.getTenantName(),
////////                            u.getSubscriptionPlan(),
////////                            u.getParent() != null ? u.getParent().getTenantId() : null,
////////                            u.getTenantType(),
////////                            u.getAccessLevel()
////////                    )).toList();
////////
////////            result.add(new CompanyWithUsersResponse(
////////                    new TenantSummaryResponse(
////////                            company.getTenantId(),
////////                            company.getTenantName(),
////////                            company.getSubscriptionPlan(),
////////                            company.getParent() != null ? company.getParent().getTenantId() : null,
////////                            company.getTenantType(),
////////                            company.getAccessLevel()
////////                    ),
////////                    userDtos
////////            ));
////////        }
////////        return result;
////////    }
//////    public List<CompanyWithUsersResponse> getCompaniesAndUsers(UUID tenantId) {
//////        Tenant parent = tenantRepository.findById(tenantId)
//////                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
//////
//////        List<CompanyWithUsersResponse> result = new ArrayList<>();
//////
//////        if (parent.getTenantType() == TenantType.ORGANIZATION) {
//////            // Get all COMPANIES under ORGANIZATION
//////            List<Tenant> companies = tenantRepository.findByParent_TenantIdAndTenantType(tenantId, TenantType.COMPANY);
//////            for (Tenant company : companies) {
//////                List<Tenant> users = tenantRepository.findByParent_TenantIdAndTenantType(company.getTenantId(), TenantType.USER);
//////                List<TenantSummaryResponse> userDtos = users.stream()
//////                        .map(u -> new TenantSummaryResponse(
//////                                u.getTenantId(), u.getTenantName(), u.getSubscriptionPlan(),
//////                                u.getParent() != null ? u.getParent().getTenantId() : null,
//////                                u.getTenantType(), u.getAccessLevel()
//////                        )).toList();
//////
//////                result.add(new CompanyWithUsersResponse(
//////                        new TenantSummaryResponse(
//////                                company.getTenantId(), company.getTenantName(), company.getSubscriptionPlan(),
//////                                company.getParent() != null ? company.getParent().getTenantId() : null,
//////                                company.getTenantType(), company.getAccessLevel()
//////                        ),
//////                        userDtos
//////                ));
//////            }
//////        } else if (parent.getTenantType() == TenantType.COMPANY) {
//////            // Directly return this COMPANY and its USERS
//////            List<Tenant> users = tenantRepository.findByParent_TenantIdAndTenantType(tenantId, TenantType.USER);
//////            List<TenantSummaryResponse> userDtos = users.stream()
//////                    .map(u -> new TenantSummaryResponse(
//////                            u.getTenantId(), u.getTenantName(), u.getSubscriptionPlan(),
//////                            u.getParent() != null ? u.getParent().getTenantId() : null,
//////                            u.getTenantType(), u.getAccessLevel()
//////                    )).toList();
//////
//////            result.add(new CompanyWithUsersResponse(
//////                    new TenantSummaryResponse(
//////                            parent.getTenantId(), parent.getTenantName(), parent.getSubscriptionPlan(),
//////                            parent.getParent() != null ? parent.getParent().getTenantId() : null,
//////                            parent.getTenantType(), parent.getAccessLevel()
//////                    ),
//////                    userDtos
//////            ));
//////        }
//////        // For USER level tenant, result stays empty (or you could throw)
//////        return result;
//////    }
//////
//////}
//////
////////public class TenantService {
////////
////////    private final TenantRepository tenantRepository;              // used for global read/list only
////////    private final TenantCassandraDao tenantCassandraDao;
////////    private final RestTemplate restTemplate;
////////
////////    @Value("${services.dms.url}")
////////    private String deviceServiceUrl;
////////
////////    private static final String KEYSPACE_SUFFIX = "_ks";
////////
////////    public CreateTenantResponse createTenant(CreateTenantRequest request) {
////////        UUID tenantId = UUID.randomUUID();
////////        Instant now = Instant.now();
////////
////////        String tenantName = sanitizeTenantName(request.getTenantName());
////////        String subscriptionPlan = request.getSubscriptionPlan();
////////        String keyspace = tenantName + KEYSPACE_SUFFIX;
////////
////////        // Step 1: Create keyspace and tables
////////        createKeyspaceIfNotExists(keyspace);
////////        createTenantTablesIfNeeded(keyspace);
////////
////////        // Step 2: Insert tenant into per-tenant keyspace (DO NOT use tenantRepository here)
////////        tenantCassandraDao.insertTenantData(keyspace, tenantId, tenantName, subscriptionPlan, now);
////////
////////        // Step 3: Notify DMS to provision other tables
////////        notifyDeviceService(tenantId, keyspace);
////////
////////        return new CreateTenantResponse(tenantId, tenantName, subscriptionPlan, keyspace, now);
////////    }
////////
////////    private void createKeyspaceIfNotExists(String keyspace) {
////////        String cql = String.format(
////////                "CREATE KEYSPACE IF NOT EXISTS %s WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};",
////////                keyspace);
////////        tenantCassandraDao.getSession().execute(cql);
////////        log.info("Keyspace ensured: {}", keyspace);
////////    }
////////
////////    private void createTenantTablesIfNeeded(String keyspace) {
////////        String createTableCql = String.format(
////////                "CREATE TABLE IF NOT EXISTS %s.tenants (" +
////////                        "tenantid UUID PRIMARY KEY, " +
////////                        "tenantname TEXT, " +
////////                        "subscriptionplan TEXT, " +
////////                        "createdat TIMESTAMP);",
////////                keyspace);
////////        tenantCassandraDao.getSession().execute(createTableCql);
////////        log.info("Table ensured in keyspace: {}", keyspace);
////////    }
////////
////////    private void notifyDeviceService(UUID tenantId, String keyspace) {
////////        try {
////////            Map<String, String> body = Map.of("tenantId", tenantId.toString(), "keyspace", keyspace);
////////            restTemplate.postForEntity(deviceServiceUrl + "/api/v1/internal/provision", body, Void.class);
////////        } catch (Exception e) {
////////            log.warn("Failed to notify device service", e);
////////        }
////////    }
////////
////////    private String sanitizeTenantName(String name) {
////////        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
////////    }
////////
////////    // Reads from the global static keyspace (iotmining_ks)
////////    public List<TenantSummaryResponse> getAllTenants() {
////////        List<Tenant> tenants = tenantRepository.findAll();
////////        return tenants.stream()
////////                .map(t -> new TenantSummaryResponse(t.getTenantId(), t.getTenantName(), t.getSubscriptionPlan()))
////////                .toList();
////////    }
////////}
//////
//////
////////public class TenantService {
////////
////////    private final TenantRepository tenantRepository;
////////    private final CqlSession cqlSession;
////////
////////    private static final String KEYSPACE_SUFFIX = "_ks";
////////
////////    @Autowired
////////    private RestTemplate restTemplate;
////////
////////    @Value("${services.dms.url}")
////////    private String deviceServiceUrl;
////////
////////    public TenantService(TenantRepository tenantRepository, CqlSession cqlSession) {
////////        this.tenantRepository = tenantRepository;
////////        this.cqlSession = cqlSession;
////////    }
////////
////////    /**
////////     * Create a new Tenant, Keyspace, and Devices table dynamically
////////     */
////////    public CreateTenantResponse createTenant(CreateTenantRequest request) {
////////        validateRequest(request);
////////
////////        UUID tenantId = UUID.randomUUID();
////////        Instant now = Instant.now();
////////
////////        String tenantName = request.getTenantName();
////////        String subscriptionPlan = request.getSubscriptionPlan();
////////        String keyspaceName = generateSafeKeyspaceName(tenantName);
////////
////////        createKeyspaceIfNotExists(keyspaceName);
///////// /        createTablesForTenant(keyspaceName);
////////
////////        Tenant tenant = new Tenant(tenantId, tenantName, subscriptionPlan, now);
////////        tenantRepository.save(tenant);
////////        notifyDeviceServiceForProvisioning(tenantId, keyspaceName);
////////
////////        log.info("Tenant created: id={}, name={}, keyspace={}", tenantId, tenantName, keyspaceName);
////////
////////        return new CreateTenantResponse(tenantId, tenantName, subscriptionPlan, keyspaceName, now);
////////    }
////////
////////    /**
////////     * Validate the tenant creation request
////////     */
////////    private void validateRequest(CreateTenantRequest request) {
////////        if (request.getTenantName() == null || request.getTenantName().isBlank()) {
////////            throw new IllegalArgumentException("Tenant name must not be empty");
////////        }
////////        if (request.getSubscriptionPlan() == null || request.getSubscriptionPlan().isBlank()) {
////////            throw new IllegalArgumentException("Subscription plan must not be empty");
////////        }
///////// /        if (tenantRepository.existsByTenantName(request.getTenantName())) {
///////// /            throw new IllegalStateException("Tenant already exists: " + request.getTenantName());
///////// /        }
////////    }
////////
////////    /**
////////     * Generate a safe Cassandra keyspace name
////////     */
////////    private String generateSafeKeyspaceName(String tenantName) {
////////        String sanitized = tenantName.toLowerCase().replaceAll("[^a-z0-9]", "");
////////        return sanitized + KEYSPACE_SUFFIX;
////////    }
////////
////////    /**
////////     * Create Keyspace for the tenant if not exists
////////     */
////////    private void createKeyspaceIfNotExists(String keyspaceName) {
////////        String createCql = String.format(
////////                "CREATE KEYSPACE IF NOT EXISTS %s WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};",
////////                keyspaceName
////////        );
////////        cqlSession.execute(createCql);
////////        log.info("Keyspace created or already exists: {}", keyspaceName);
////////    }
////////
////////    private void notifyDeviceServiceForProvisioning(UUID tenantId, String keyspaceName) {
////////        try {
////////            String url = deviceServiceUrl + "/api/v1/internal/provision";
////////            Map<String, String> body = Map.of(
////////                    "tenantId", tenantId.toString(),
////////                    "keyspace", keyspaceName
////////            );
////////            restTemplate.postForEntity(url, body, Void.class);
////////            log.info("Notified DMS to provision schema for tenant {}", tenantId);
////////        } catch (Exception e) {
////////            log.error("Failed to notify DMS for schema provisioning", e);
////////        }
////////    }
//////////    /**
//////////     * Dynamically create necessary tables for the tenant
//////////     */
//////////    private void createTablesForTenant(String keyspaceName) {
//////////        String createDevicesTableCql = DDLGenerator.generateCreateTableCQL(Device.class, keyspaceName, "devices");
//////////        cqlSession.execute(createDevicesTableCql);
//////////        log.info("Devices table created inside keyspace: {}", keyspaceName);
//////////
//////////        // Future: Add more tables like telemetry, profiles etc.
//////////        // String createTelemetryTableCql = DDLGenerator.generateCreateTableCQL(Telemetry.class, keyspaceName, "telemetry");
//////////        // cqlSession.execute(createTelemetryTableCql);
//////////    }
////////
////////    /**
////////     * Find a Tenant by its UUID
////////     */
////////    public Optional<Tenant> findTenantById(UUID tenantId) {
////////        return tenantRepository.findById(tenantId);
////////    }
////////
////////    /**
////////     * Fetch all tenants (summary view)
////////     */
////////    public List<TenantSummaryResponse> getAllTenants() {
////////        List<Tenant> tenants = tenantRepository.findAll();
////////        return tenants.stream()
////////                .map(t -> new TenantSummaryResponse(t.getTenantId(), t.getTenantName(), t.getSubscriptionPlan()))
////////                .toList();
////////    }
////////}
