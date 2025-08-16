package com.iotmining.services.tms.services;

import com.iotmining.services.tms.dto.CompanyWithUsersResponse;
import com.iotmining.services.tms.dto.CreateTenantRequest;
import com.iotmining.services.tms.dto.CreateTenantResponse;
import com.iotmining.services.tms.dto.TenantSummaryResponse;
import com.iotmining.services.tms.enums.TenantAccessLevel;
import com.iotmining.services.tms.enums.TenantType;
import com.iotmining.services.tms.model.Tenant;
import com.iotmining.services.tms.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    private static TenantType determineTenantType(List<String> roles) {
        if (roles == null) return TenantType.USER;
        if (roles.contains("ROLE_SUPER_ADMIN")) {
            return TenantType.ORGANIZATION;
        } else if (roles.contains("ROLE_ADMIN")) {
            return TenantType.COMPANY;
        } else {
            return TenantType.USER;
        }
    }

    private static TenantAccessLevel determineAccessLevel(TenantType tenantType) {
        return switch (tenantType) {
            case ORGANIZATION -> TenantAccessLevel.SUPER;
            case COMPANY -> TenantAccessLevel.ADMIN;
            case USER -> TenantAccessLevel.READ_ONLY;
        };
    }

    public TenantSummaryResponse getTenantSummary(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> new TenantSummaryResponse(
                        t.getTenantId(),
                        t.getTenantName(),
                        t.getSubscriptionPlan(),
                        t.getParent() != null ? t.getParent().getTenantId() : null,
                        t.getTenantType(),
                        t.getAccessLevel()
                ))
                .orElse(null);
    }

    public CreateTenantResponse createTenant(CreateTenantRequest request) {
        Tenant parent = null;
        if (request.getParentId() != null) {
            parent = tenantRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent tenant not found"));
        }

        TenantType tenantType = determineTenantType(request.getRoles());
        TenantAccessLevel accessLevel = determineAccessLevel(tenantType);

        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setTenantName(request.getTenantName());
        tenant.setSubscriptionPlan(request.getSubscriptionPlan());
        tenant.setCreatedAt(now);
        tenant.setParent(parent);
        tenant.setTenantType(tenantType);
        tenant.setAccessLevel(accessLevel);

        tenantRepository.save(tenant);

        String keyspaceName = tenant.getTenantName().toLowerCase().replaceAll("[^a-z0-9]", "") + "_ks";

        return new CreateTenantResponse(
                tenant.getTenantId(),
                tenant.getTenantName(),
                tenant.getSubscriptionPlan(),
                keyspaceName,
                now,
                tenantType,
                accessLevel
        );
    }

    public List<TenantSummaryResponse> getAllTenants() {
        return tenantRepository.findAll()
                .stream()
                .map(t -> new TenantSummaryResponse(
                        t.getTenantId(),
                        t.getTenantName(),
                        t.getSubscriptionPlan(),
                        t.getParent() != null ? t.getParent().getTenantId() : null,
                        t.getTenantType(),
                        t.getAccessLevel()
                )).collect(Collectors.toList());
    }

    public List<TenantSummaryResponse> getSubTenants(UUID parentId) {
        return tenantRepository.findByParent_TenantId(parentId)
                .stream()
                .map(t -> new TenantSummaryResponse(
                        t.getTenantId(),
                        t.getTenantName(),
                        t.getSubscriptionPlan(),
                        t.getParent() != null ? t.getParent().getTenantId() : null,
                        t.getTenantType(),
                        t.getAccessLevel()
                )).collect(Collectors.toList());
    }

    /**
     * Unified: Returns a full tree for any org/company/user.
     * For ORG: returns itself (company: field), its direct users (users: field), and companies with their sub-users/subcompanies.
     * For COMPANY: returns itself, direct users, subcompanies/users recursively.
     * For USER: returns itself only.
     */
    public List<CompanyWithUsersResponse> getCompaniesAndUsers(UUID tenantId) {
        Tenant node = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        List<CompanyWithUsersResponse> result = new ArrayList<>();

        // Start recursion at the root node
        result.add(buildCompanyTreeRecursive(node));

        return result;
    }

    // --- Recursive tree builder ---
    private CompanyWithUsersResponse buildCompanyTreeRecursive(Tenant node) {
        // Users directly under this node
        List<Tenant> users = tenantRepository.findByParent_TenantIdAndTenantType(node.getTenantId(), TenantType.USER);
        List<TenantSummaryResponse> userDtos = users.stream()
                .map(u -> new TenantSummaryResponse(
                        u.getTenantId(), u.getTenantName(), u.getSubscriptionPlan(),
                        u.getParent() != null ? u.getParent().getTenantId() : null,
                        u.getTenantType(), u.getAccessLevel()
                )).collect(Collectors.toList());

        // Sub-companies under this node
        List<Tenant> subCompanies = tenantRepository.findByParent_TenantIdAndTenantType(node.getTenantId(), TenantType.COMPANY);

        List<CompanyWithUsersResponse> subCompanyDtos = subCompanies.stream()
                .map(this::buildCompanyTreeRecursive)
                .collect(Collectors.toList());

        // Wrap this node and children
        return new CompanyWithUsersResponse(
                new TenantSummaryResponse(
                        node.getTenantId(), node.getTenantName(), node.getSubscriptionPlan(),
                        node.getParent() != null ? node.getParent().getTenantId() : null,
                        node.getTenantType(), node.getAccessLevel()
                ),
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
//import com.iotmining.services.tms.model.*;
//import com.iotmining.services.tms.repository.TenantRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.UUID;
//import java.util.stream.Collectors;
//
//@Service
//@RequiredArgsConstructor
//public class TenantService {
//
//    private final TenantRepository tenantRepository;
//
//    private static TenantType determineTenantType(List<String> roles) {
//        if (roles == null) return TenantType.USER;
//        if (roles.contains("ROLE_SUPER_ADMIN")) {
//            return TenantType.ORGANIZATION;
//        } else if (roles.contains("ROLE_ADMIN")) {
//            return TenantType.COMPANY;
//        } else {
//            return TenantType.USER;
//        }
//    }
//
//    private static TenantAccessLevel determineAccessLevel(TenantType tenantType) {
//        return switch (tenantType) {
//            case ORGANIZATION -> TenantAccessLevel.SUPER;
//            case COMPANY -> TenantAccessLevel.ADMIN;
//            case USER -> TenantAccessLevel.READ_ONLY;
//        };
//    }
//
//    public CreateTenantResponse createTenant(CreateTenantRequest request) {
//        Tenant parent = null;
//        if (request.getParentId() != null) {
//            parent = tenantRepository.findById(request.getParentId())
//                    .orElseThrow(() -> new IllegalArgumentException("Parent tenant not found"));
//        }
//
//        TenantType tenantType = determineTenantType(request.getRoles());
//        TenantAccessLevel accessLevel = determineAccessLevel(tenantType);
//
//        UUID tenantId = UUID.randomUUID();
//        Instant now = Instant.now();
//
//        Tenant tenant = new Tenant();
//        tenant.setTenantId(tenantId);
//        tenant.setTenantName(request.getTenantName());
//        tenant.setSubscriptionPlan(request.getSubscriptionPlan());
//        tenant.setCreatedAt(now);
//        tenant.setParent(parent);
//        tenant.setTenantType(tenantType);
//        tenant.setAccessLevel(accessLevel);
//
//        tenantRepository.save(tenant);
//
//        String keyspaceName = tenant.getTenantName().toLowerCase().replaceAll("[^a-z0-9]", "") + "_ks";
//
//        return new CreateTenantResponse(
//                tenant.getTenantId(),
//                tenant.getTenantName(),
//                tenant.getSubscriptionPlan(),
//                keyspaceName,
//                now,
//                tenantType,
//                accessLevel
//        );
//    }
//
//    public List<TenantSummaryResponse> getAllTenants() {
//        return tenantRepository.findAll()
//                .stream()
//                .map(t -> new TenantSummaryResponse(
//                        t.getTenantId(),
//                        t.getTenantName(),
//                        t.getSubscriptionPlan(),
//                        t.getParent() != null ? t.getParent().getTenantId() : null,
//                        t.getTenantType(),
//                        t.getAccessLevel()
//                )).collect(Collectors.toList());
//    }
//
//    public List<TenantSummaryResponse> getSubTenants(UUID parentId) {
//        return tenantRepository.findByParent_TenantId(parentId)
//                .stream()
//                .map(t -> new TenantSummaryResponse(
//                        t.getTenantId(),
//                        t.getTenantName(),
//                        t.getSubscriptionPlan(),
//                        t.getParent() != null ? t.getParent().getTenantId() : null,
//                        t.getTenantType(),
//                        t.getAccessLevel()
//                )).collect(Collectors.toList());
//    }
//
////    public List<CompanyWithUsersResponse> getCompaniesAndUsers(UUID parentId) {
////        // 1. Get all COMPANY tenants under the given parent (likely ORGANIZATION)
////        List<Tenant> companies = tenantRepository.findByParent_TenantIdAndTenantType(parentId, TenantType.COMPANY);
////
////        // 2. For each company, get its USER children
////        List<CompanyWithUsersResponse> result = new ArrayList<>();
////        for (Tenant company : companies) {
////            List<Tenant> users = tenantRepository.findByParent_TenantIdAndTenantType(company.getTenantId(), TenantType.USER);
////            List<TenantSummaryResponse> userDtos = users.stream()
////                    .map(u -> new TenantSummaryResponse(
////                            u.getTenantId(),
////                            u.getTenantName(),
////                            u.getSubscriptionPlan(),
////                            u.getParent() != null ? u.getParent().getTenantId() : null,
////                            u.getTenantType(),
////                            u.getAccessLevel()
////                    )).toList();
////
////            result.add(new CompanyWithUsersResponse(
////                    new TenantSummaryResponse(
////                            company.getTenantId(),
////                            company.getTenantName(),
////                            company.getSubscriptionPlan(),
////                            company.getParent() != null ? company.getParent().getTenantId() : null,
////                            company.getTenantType(),
////                            company.getAccessLevel()
////                    ),
////                    userDtos
////            ));
////        }
////        return result;
////    }
//    public List<CompanyWithUsersResponse> getCompaniesAndUsers(UUID tenantId) {
//        Tenant parent = tenantRepository.findById(tenantId)
//                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
//
//        List<CompanyWithUsersResponse> result = new ArrayList<>();
//
//        if (parent.getTenantType() == TenantType.ORGANIZATION) {
//            // Get all COMPANIES under ORGANIZATION
//            List<Tenant> companies = tenantRepository.findByParent_TenantIdAndTenantType(tenantId, TenantType.COMPANY);
//            for (Tenant company : companies) {
//                List<Tenant> users = tenantRepository.findByParent_TenantIdAndTenantType(company.getTenantId(), TenantType.USER);
//                List<TenantSummaryResponse> userDtos = users.stream()
//                        .map(u -> new TenantSummaryResponse(
//                                u.getTenantId(), u.getTenantName(), u.getSubscriptionPlan(),
//                                u.getParent() != null ? u.getParent().getTenantId() : null,
//                                u.getTenantType(), u.getAccessLevel()
//                        )).toList();
//
//                result.add(new CompanyWithUsersResponse(
//                        new TenantSummaryResponse(
//                                company.getTenantId(), company.getTenantName(), company.getSubscriptionPlan(),
//                                company.getParent() != null ? company.getParent().getTenantId() : null,
//                                company.getTenantType(), company.getAccessLevel()
//                        ),
//                        userDtos
//                ));
//            }
//        } else if (parent.getTenantType() == TenantType.COMPANY) {
//            // Directly return this COMPANY and its USERS
//            List<Tenant> users = tenantRepository.findByParent_TenantIdAndTenantType(tenantId, TenantType.USER);
//            List<TenantSummaryResponse> userDtos = users.stream()
//                    .map(u -> new TenantSummaryResponse(
//                            u.getTenantId(), u.getTenantName(), u.getSubscriptionPlan(),
//                            u.getParent() != null ? u.getParent().getTenantId() : null,
//                            u.getTenantType(), u.getAccessLevel()
//                    )).toList();
//
//            result.add(new CompanyWithUsersResponse(
//                    new TenantSummaryResponse(
//                            parent.getTenantId(), parent.getTenantName(), parent.getSubscriptionPlan(),
//                            parent.getParent() != null ? parent.getParent().getTenantId() : null,
//                            parent.getTenantType(), parent.getAccessLevel()
//                    ),
//                    userDtos
//            ));
//        }
//        // For USER level tenant, result stays empty (or you could throw)
//        return result;
//    }
//
//}
//
////public class TenantService {
////
////    private final TenantRepository tenantRepository;              // used for global read/list only
////    private final TenantCassandraDao tenantCassandraDao;
////    private final RestTemplate restTemplate;
////
////    @Value("${services.dms.url}")
////    private String deviceServiceUrl;
////
////    private static final String KEYSPACE_SUFFIX = "_ks";
////
////    public CreateTenantResponse createTenant(CreateTenantRequest request) {
////        UUID tenantId = UUID.randomUUID();
////        Instant now = Instant.now();
////
////        String tenantName = sanitizeTenantName(request.getTenantName());
////        String subscriptionPlan = request.getSubscriptionPlan();
////        String keyspace = tenantName + KEYSPACE_SUFFIX;
////
////        // Step 1: Create keyspace and tables
////        createKeyspaceIfNotExists(keyspace);
////        createTenantTablesIfNeeded(keyspace);
////
////        // Step 2: Insert tenant into per-tenant keyspace (DO NOT use tenantRepository here)
////        tenantCassandraDao.insertTenantData(keyspace, tenantId, tenantName, subscriptionPlan, now);
////
////        // Step 3: Notify DMS to provision other tables
////        notifyDeviceService(tenantId, keyspace);
////
////        return new CreateTenantResponse(tenantId, tenantName, subscriptionPlan, keyspace, now);
////    }
////
////    private void createKeyspaceIfNotExists(String keyspace) {
////        String cql = String.format(
////                "CREATE KEYSPACE IF NOT EXISTS %s WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};",
////                keyspace);
////        tenantCassandraDao.getSession().execute(cql);
////        log.info("Keyspace ensured: {}", keyspace);
////    }
////
////    private void createTenantTablesIfNeeded(String keyspace) {
////        String createTableCql = String.format(
////                "CREATE TABLE IF NOT EXISTS %s.tenants (" +
////                        "tenantid UUID PRIMARY KEY, " +
////                        "tenantname TEXT, " +
////                        "subscriptionplan TEXT, " +
////                        "createdat TIMESTAMP);",
////                keyspace);
////        tenantCassandraDao.getSession().execute(createTableCql);
////        log.info("Table ensured in keyspace: {}", keyspace);
////    }
////
////    private void notifyDeviceService(UUID tenantId, String keyspace) {
////        try {
////            Map<String, String> body = Map.of("tenantId", tenantId.toString(), "keyspace", keyspace);
////            restTemplate.postForEntity(deviceServiceUrl + "/api/v1/internal/provision", body, Void.class);
////        } catch (Exception e) {
////            log.warn("Failed to notify device service", e);
////        }
////    }
////
////    private String sanitizeTenantName(String name) {
////        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
////    }
////
////    // Reads from the global static keyspace (iotmining_ks)
////    public List<TenantSummaryResponse> getAllTenants() {
////        List<Tenant> tenants = tenantRepository.findAll();
////        return tenants.stream()
////                .map(t -> new TenantSummaryResponse(t.getTenantId(), t.getTenantName(), t.getSubscriptionPlan()))
////                .toList();
////    }
////}
//
//
////public class TenantService {
////
////    private final TenantRepository tenantRepository;
////    private final CqlSession cqlSession;
////
////    private static final String KEYSPACE_SUFFIX = "_ks";
////
////    @Autowired
////    private RestTemplate restTemplate;
////
////    @Value("${services.dms.url}")
////    private String deviceServiceUrl;
////
////    public TenantService(TenantRepository tenantRepository, CqlSession cqlSession) {
////        this.tenantRepository = tenantRepository;
////        this.cqlSession = cqlSession;
////    }
////
////    /**
////     * Create a new Tenant, Keyspace, and Devices table dynamically
////     */
////    public CreateTenantResponse createTenant(CreateTenantRequest request) {
////        validateRequest(request);
////
////        UUID tenantId = UUID.randomUUID();
////        Instant now = Instant.now();
////
////        String tenantName = request.getTenantName();
////        String subscriptionPlan = request.getSubscriptionPlan();
////        String keyspaceName = generateSafeKeyspaceName(tenantName);
////
////        createKeyspaceIfNotExists(keyspaceName);
///// /        createTablesForTenant(keyspaceName);
////
////        Tenant tenant = new Tenant(tenantId, tenantName, subscriptionPlan, now);
////        tenantRepository.save(tenant);
////        notifyDeviceServiceForProvisioning(tenantId, keyspaceName);
////
////        log.info("Tenant created: id={}, name={}, keyspace={}", tenantId, tenantName, keyspaceName);
////
////        return new CreateTenantResponse(tenantId, tenantName, subscriptionPlan, keyspaceName, now);
////    }
////
////    /**
////     * Validate the tenant creation request
////     */
////    private void validateRequest(CreateTenantRequest request) {
////        if (request.getTenantName() == null || request.getTenantName().isBlank()) {
////            throw new IllegalArgumentException("Tenant name must not be empty");
////        }
////        if (request.getSubscriptionPlan() == null || request.getSubscriptionPlan().isBlank()) {
////            throw new IllegalArgumentException("Subscription plan must not be empty");
////        }
///// /        if (tenantRepository.existsByTenantName(request.getTenantName())) {
///// /            throw new IllegalStateException("Tenant already exists: " + request.getTenantName());
///// /        }
////    }
////
////    /**
////     * Generate a safe Cassandra keyspace name
////     */
////    private String generateSafeKeyspaceName(String tenantName) {
////        String sanitized = tenantName.toLowerCase().replaceAll("[^a-z0-9]", "");
////        return sanitized + KEYSPACE_SUFFIX;
////    }
////
////    /**
////     * Create Keyspace for the tenant if not exists
////     */
////    private void createKeyspaceIfNotExists(String keyspaceName) {
////        String createCql = String.format(
////                "CREATE KEYSPACE IF NOT EXISTS %s WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};",
////                keyspaceName
////        );
////        cqlSession.execute(createCql);
////        log.info("Keyspace created or already exists: {}", keyspaceName);
////    }
////
////    private void notifyDeviceServiceForProvisioning(UUID tenantId, String keyspaceName) {
////        try {
////            String url = deviceServiceUrl + "/api/v1/internal/provision";
////            Map<String, String> body = Map.of(
////                    "tenantId", tenantId.toString(),
////                    "keyspace", keyspaceName
////            );
////            restTemplate.postForEntity(url, body, Void.class);
////            log.info("Notified DMS to provision schema for tenant {}", tenantId);
////        } catch (Exception e) {
////            log.error("Failed to notify DMS for schema provisioning", e);
////        }
////    }
//////    /**
//////     * Dynamically create necessary tables for the tenant
//////     */
//////    private void createTablesForTenant(String keyspaceName) {
//////        String createDevicesTableCql = DDLGenerator.generateCreateTableCQL(Device.class, keyspaceName, "devices");
//////        cqlSession.execute(createDevicesTableCql);
//////        log.info("Devices table created inside keyspace: {}", keyspaceName);
//////
//////        // Future: Add more tables like telemetry, profiles etc.
//////        // String createTelemetryTableCql = DDLGenerator.generateCreateTableCQL(Telemetry.class, keyspaceName, "telemetry");
//////        // cqlSession.execute(createTelemetryTableCql);
//////    }
////
////    /**
////     * Find a Tenant by its UUID
////     */
////    public Optional<Tenant> findTenantById(UUID tenantId) {
////        return tenantRepository.findById(tenantId);
////    }
////
////    /**
////     * Fetch all tenants (summary view)
////     */
////    public List<TenantSummaryResponse> getAllTenants() {
////        List<Tenant> tenants = tenantRepository.findAll();
////        return tenants.stream()
////                .map(t -> new TenantSummaryResponse(t.getTenantId(), t.getTenantName(), t.getSubscriptionPlan()))
////                .toList();
////    }
////}
