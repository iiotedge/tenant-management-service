package com.iotmining.services.tms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotmining.services.tms.dto.CreateTenantRequest;
import com.iotmining.services.tms.dto.CreateTenantResponse;
import com.iotmining.services.tms.dto.TenantSummaryResponse;
import com.iotmining.services.tms.exceptions.GlobalExceptionHandler;
import com.iotmining.services.tms.exceptions.TenantNotFoundException;
import com.iotmining.services.tms.services.TenantService;
import com.iotmining.common.data.tenant.TenantAccessLevel;
import com.iotmining.common.data.tenant.TenantType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link TenantController} using standalone MockMvc.
 *
 * <p>Scope: request mapping, request validation, response contract, and
 * exception-to-status mapping. Spring Security filters and
 * {@code @PreAuthorize} rules are not part of a standalone setup and are
 * intentionally out of scope here (matches auth-service's controller test
 * convention).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantController")
class TenantControllerTest {

    @Mock private TenantService tenantService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantController controller = new TenantController(tenantService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/tenants")
    class CreateTenant {

        @Test
        @DisplayName("returns 201 with the created tenant")
        void createSuccess() throws Exception {
            CreateTenantRequest request = new CreateTenantRequest("Acme", "PRO", null, null);
            CreateTenantResponse response = new CreateTenantResponse(
                    UUID.randomUUID(), "Acme", "PRO", "acme_ks", Instant.now(),
                    TenantType.ORGANIZATION, TenantAccessLevel.TENANT_ADMIN);
            when(tenantService.createTenant(any(CreateTenantRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/tenants")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tenantName").value("Acme"));
        }

        @Test
        @DisplayName("returns 400 when tenantName is blank")
        void rejectsBlankTenantName() throws Exception {
            CreateTenantRequest request = new CreateTenantRequest("  ", null, null, null);

            mockMvc.perform(post("/api/v1/tenants")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 404 when the requested parent tenant does not exist")
        void returnsNotFoundForMissingParent() throws Exception {
            UUID parentId = UUID.randomUUID();
            CreateTenantRequest request = new CreateTenantRequest("Acme", null, parentId, null);
            when(tenantService.createTenant(any(CreateTenantRequest.class)))
                    .thenThrow(new TenantNotFoundException(parentId));

            mockMvc.perform(post("/api/v1/tenants")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 409 when the hierarchy rule is violated")
        void returnsConflictForInvalidHierarchy() throws Exception {
            CreateTenantRequest request = new CreateTenantRequest("Acme", null, null, List.of("ROLE_ADMIN"));
            when(tenantService.createTenant(any(CreateTenantRequest.class)))
                    .thenThrow(new IllegalStateException("A SUB_TENANT must have a parent ORGANIZATION."));

            mockMvc.perform(post("/api/v1/tenants")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/tenants/internal/{tenantId}")
    class RollbackTenantCreation {

        @Test
        @DisplayName("returns 204 and delegates to the service")
        void deletesTenant() throws Exception {
            UUID tenantId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/tenants/internal/{tenantId}", tenantId))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tenants/{tenantId}")
    class GetTenantDetails {

        @Test
        @DisplayName("returns 200 when found")
        void returnsDetailsWhenFound() throws Exception {
            UUID tenantId = UUID.randomUUID();
            TenantSummaryResponse summary = new TenantSummaryResponse(
                    tenantId, "Acme", "PRO", null, TenantType.ORGANIZATION, TenantAccessLevel.TENANT_ADMIN);
            when(tenantService.getTenantSummary(tenantId)).thenReturn(summary);

            mockMvc.perform(get("/api/v1/tenants/{tenantId}", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantName").value("Acme"));
        }

        @Test
        @DisplayName("returns 404 when not found")
        void returnsNotFoundWhenMissing() throws Exception {
            UUID tenantId = UUID.randomUUID();
            when(tenantService.getTenantSummary(tenantId)).thenReturn(null);

            mockMvc.perform(get("/api/v1/tenants/{tenantId}", tenantId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tenants")
    class GetAllTenants {

        @Test
        @DisplayName("returns a paginated listing")
        void returnsPagedListing() throws Exception {
            TenantSummaryResponse summary = new TenantSummaryResponse(
                    UUID.randomUUID(), "Acme", "PRO", null, TenantType.ORGANIZATION, TenantAccessLevel.TENANT_ADMIN);
            when(tenantService.getAllTenants(any()))
                    .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/tenants"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].tenantName").value("Acme"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tenants/children/{parentId}")
    class GetSubTenants {

        @Test
        @DisplayName("returns the children list")
        void returnsChildren() throws Exception {
            UUID parentId = UUID.randomUUID();
            TenantSummaryResponse child = new TenantSummaryResponse(
                    UUID.randomUUID(), "Site 1", "PRO", parentId, TenantType.SUB_TENANT, TenantAccessLevel.OPERATIONAL);
            when(tenantService.getSubTenants(parentId)).thenReturn(List.of(child));

            mockMvc.perform(get("/api/v1/tenants/children/{parentId}", parentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].tenantName").value("Site 1"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tenants/{tenantId}/companies-with-users")
    class GetCompaniesWithUsers {

        @Test
        @DisplayName("returns 404 when the root tenant does not exist")
        void returnsNotFoundWhenRootMissing() throws Exception {
            UUID tenantId = UUID.randomUUID();
            when(tenantService.getCompaniesAndUsers(tenantId)).thenThrow(new TenantNotFoundException(tenantId));

            mockMvc.perform(get("/api/v1/tenants/{tenantId}/companies-with-users", tenantId))
                    .andExpect(status().isNotFound());
        }
    }
}
