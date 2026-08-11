package com.iotmining.services.tms.services;

import com.iotmining.common.data.tenant.TenantAccessLevel;
import com.iotmining.common.data.tenant.TenantType;
import com.iotmining.services.tms.dto.CompanyWithUsersResponse;
import com.iotmining.services.tms.dto.CreateTenantRequest;
import com.iotmining.services.tms.dto.CreateTenantResponse;
import com.iotmining.services.tms.dto.TenantSummaryResponse;
import com.iotmining.services.tms.exceptions.TenantNotFoundException;

import com.iotmining.services.tms.model.Tenant;
import com.iotmining.services.tms.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                    .orElseThrow(() -> new TenantNotFoundException(request.getParentId()));
        }

        TenantType tenantType = determineTenantType(request.getRoles());

        if (parent != null) {
            validateHierarchy(parent.getTenantType(), tenantType);
        } else if (tenantType == TenantType.SUB_TENANT) {
            throw new IllegalStateException("A SUB_TENANT must have a parent ORGANIZATION.");
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
    public Page<TenantSummaryResponse> getAllTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(this::mapToSummary);
    }

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
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        List<CompanyWithUsersResponse> result = new ArrayList<>();
        result.add(buildCompanyTree(node));
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

    // Builds the full descendant tree breadth-first, fetching each level's
    // children in one batched query instead of one query per node - a tree
    // with N nodes across D levels costs O(D) queries here, not O(N).
    private CompanyWithUsersResponse buildCompanyTree(Tenant root) {
        Map<UUID, CompanyWithUsersResponse> byId = new HashMap<>();
        Map<UUID, List<CompanyWithUsersResponse>> childrenById = new HashMap<>();

        CompanyWithUsersResponse rootDto = new CompanyWithUsersResponse(mapToSummary(root), new ArrayList<>(), new ArrayList<>());
        byId.put(root.getTenantId(), rootDto);

        List<UUID> currentLevelIds = List.of(root.getTenantId());
        while (!currentLevelIds.isEmpty()) {
            List<Tenant> children = tenantRepository.findByParent_TenantIdIn(currentLevelIds);
            if (children.isEmpty()) break;

            List<UUID> nextLevelIds = new ArrayList<>();
            for (Tenant child : children) {
                CompanyWithUsersResponse childDto = new CompanyWithUsersResponse(mapToSummary(child), new ArrayList<>(), new ArrayList<>());
                byId.put(child.getTenantId(), childDto);
                childrenById.computeIfAbsent(child.getParent().getTenantId(), k -> new ArrayList<>()).add(childDto);
                nextLevelIds.add(child.getTenantId());
            }
            currentLevelIds = nextLevelIds;
        }

        for (Map.Entry<UUID, CompanyWithUsersResponse> entry : byId.entrySet()) {
            List<CompanyWithUsersResponse> kids = childrenById.get(entry.getKey());
            if (kids != null) {
                entry.getValue().getSubCompanies().addAll(kids);
            }
        }

        return rootDto;
    }
}
