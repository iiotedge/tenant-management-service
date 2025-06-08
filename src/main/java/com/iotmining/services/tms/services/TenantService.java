package com.iotmining.services.tms.services;

import com.iotmining.services.tms.dto.CreateTenantRequest;
import com.iotmining.services.tms.dto.CreateTenantResponse;
import com.iotmining.services.tms.dto.TenantSummaryResponse;
import com.iotmining.services.tms.model.Tenant;
import com.iotmining.services.tms.model.TenantCassandraDao;
import com.iotmining.services.tms.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;              // used for global read/list only
    private final TenantCassandraDao tenantCassandraDao;
    private final RestTemplate restTemplate;

    @Value("${services.dms.url}")
    private String deviceServiceUrl;

    private static final String KEYSPACE_SUFFIX = "_ks";

    public CreateTenantResponse createTenant(CreateTenantRequest request) {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        String tenantName = sanitizeTenantName(request.getTenantName());
        String subscriptionPlan = request.getSubscriptionPlan();
        String keyspace = tenantName + KEYSPACE_SUFFIX;

        // Step 1: Create keyspace and tables
        createKeyspaceIfNotExists(keyspace);
        createTenantTablesIfNeeded(keyspace);

        // Step 2: Insert tenant into per-tenant keyspace (DO NOT use tenantRepository here)
        tenantCassandraDao.insertTenantData(keyspace, tenantId, tenantName, subscriptionPlan, now);

        // Step 3: Notify DMS to provision other tables
        notifyDeviceService(tenantId, keyspace);

        return new CreateTenantResponse(tenantId, tenantName, subscriptionPlan, keyspace, now);
    }

    private void createKeyspaceIfNotExists(String keyspace) {
        String cql = String.format(
                "CREATE KEYSPACE IF NOT EXISTS %s WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};",
                keyspace);
        tenantCassandraDao.getSession().execute(cql);
        log.info("Keyspace ensured: {}", keyspace);
    }

    private void createTenantTablesIfNeeded(String keyspace) {
        String createTableCql = String.format(
                "CREATE TABLE IF NOT EXISTS %s.tenants (" +
                        "tenantid UUID PRIMARY KEY, " +
                        "tenantname TEXT, " +
                        "subscriptionplan TEXT, " +
                        "createdat TIMESTAMP);",
                keyspace);
        tenantCassandraDao.getSession().execute(createTableCql);
        log.info("Table ensured in keyspace: {}", keyspace);
    }

    private void notifyDeviceService(UUID tenantId, String keyspace) {
        try {
            Map<String, String> body = Map.of("tenantId", tenantId.toString(), "keyspace", keyspace);
            restTemplate.postForEntity(deviceServiceUrl + "/api/v1/internal/provision", body, Void.class);
        } catch (Exception e) {
            log.warn("Failed to notify device service", e);
        }
    }

    private String sanitizeTenantName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    // Reads from the global static keyspace (iotmining_ks)
    public List<TenantSummaryResponse> getAllTenants() {
        List<Tenant> tenants = tenantRepository.findAll();
        return tenants.stream()
                .map(t -> new TenantSummaryResponse(t.getTenantId(), t.getTenantName(), t.getSubscriptionPlan()))
                .toList();
    }
}


//public class TenantService {
//
//    private final TenantRepository tenantRepository;
//    private final CqlSession cqlSession;
//
//    private static final String KEYSPACE_SUFFIX = "_ks";
//
//    @Autowired
//    private RestTemplate restTemplate;
//
//    @Value("${services.dms.url}")
//    private String deviceServiceUrl;
//
//    public TenantService(TenantRepository tenantRepository, CqlSession cqlSession) {
//        this.tenantRepository = tenantRepository;
//        this.cqlSession = cqlSession;
//    }
//
//    /**
//     * Create a new Tenant, Keyspace, and Devices table dynamically
//     */
//    public CreateTenantResponse createTenant(CreateTenantRequest request) {
//        validateRequest(request);
//
//        UUID tenantId = UUID.randomUUID();
//        Instant now = Instant.now();
//
//        String tenantName = request.getTenantName();
//        String subscriptionPlan = request.getSubscriptionPlan();
//        String keyspaceName = generateSafeKeyspaceName(tenantName);
//
//        createKeyspaceIfNotExists(keyspaceName);
/// /        createTablesForTenant(keyspaceName);
//
//        Tenant tenant = new Tenant(tenantId, tenantName, subscriptionPlan, now);
//        tenantRepository.save(tenant);
//        notifyDeviceServiceForProvisioning(tenantId, keyspaceName);
//
//        log.info("Tenant created: id={}, name={}, keyspace={}", tenantId, tenantName, keyspaceName);
//
//        return new CreateTenantResponse(tenantId, tenantName, subscriptionPlan, keyspaceName, now);
//    }
//
//    /**
//     * Validate the tenant creation request
//     */
//    private void validateRequest(CreateTenantRequest request) {
//        if (request.getTenantName() == null || request.getTenantName().isBlank()) {
//            throw new IllegalArgumentException("Tenant name must not be empty");
//        }
//        if (request.getSubscriptionPlan() == null || request.getSubscriptionPlan().isBlank()) {
//            throw new IllegalArgumentException("Subscription plan must not be empty");
//        }
/// /        if (tenantRepository.existsByTenantName(request.getTenantName())) {
/// /            throw new IllegalStateException("Tenant already exists: " + request.getTenantName());
/// /        }
//    }
//
//    /**
//     * Generate a safe Cassandra keyspace name
//     */
//    private String generateSafeKeyspaceName(String tenantName) {
//        String sanitized = tenantName.toLowerCase().replaceAll("[^a-z0-9]", "");
//        return sanitized + KEYSPACE_SUFFIX;
//    }
//
//    /**
//     * Create Keyspace for the tenant if not exists
//     */
//    private void createKeyspaceIfNotExists(String keyspaceName) {
//        String createCql = String.format(
//                "CREATE KEYSPACE IF NOT EXISTS %s WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};",
//                keyspaceName
//        );
//        cqlSession.execute(createCql);
//        log.info("Keyspace created or already exists: {}", keyspaceName);
//    }
//
//    private void notifyDeviceServiceForProvisioning(UUID tenantId, String keyspaceName) {
//        try {
//            String url = deviceServiceUrl + "/api/v1/internal/provision";
//            Map<String, String> body = Map.of(
//                    "tenantId", tenantId.toString(),
//                    "keyspace", keyspaceName
//            );
//            restTemplate.postForEntity(url, body, Void.class);
//            log.info("Notified DMS to provision schema for tenant {}", tenantId);
//        } catch (Exception e) {
//            log.error("Failed to notify DMS for schema provisioning", e);
//        }
//    }
////    /**
////     * Dynamically create necessary tables for the tenant
////     */
////    private void createTablesForTenant(String keyspaceName) {
////        String createDevicesTableCql = DDLGenerator.generateCreateTableCQL(Device.class, keyspaceName, "devices");
////        cqlSession.execute(createDevicesTableCql);
////        log.info("Devices table created inside keyspace: {}", keyspaceName);
////
////        // Future: Add more tables like telemetry, profiles etc.
////        // String createTelemetryTableCql = DDLGenerator.generateCreateTableCQL(Telemetry.class, keyspaceName, "telemetry");
////        // cqlSession.execute(createTelemetryTableCql);
////    }
//
//    /**
//     * Find a Tenant by its UUID
//     */
//    public Optional<Tenant> findTenantById(UUID tenantId) {
//        return tenantRepository.findById(tenantId);
//    }
//
//    /**
//     * Fetch all tenants (summary view)
//     */
//    public List<TenantSummaryResponse> getAllTenants() {
//        List<Tenant> tenants = tenantRepository.findAll();
//        return tenants.stream()
//                .map(t -> new TenantSummaryResponse(t.getTenantId(), t.getTenantName(), t.getSubscriptionPlan()))
//                .toList();
//    }
//}
