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
import com.iotmining.services.tms.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService")
class TenantServiceTest {

    @Mock private TenantRepository tenantRepository;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository);
    }

    @Nested
    @DisplayName("getTenantSummary")
    class GetTenantSummary {

        @Test
        @DisplayName("returns the mapped summary when the tenant exists")
        void returnsSummaryWhenFound() {
            Tenant tenant = TestDataFactory.organization("Acme");
            when(tenantRepository.findById(tenant.getTenantId())).thenReturn(Optional.of(tenant));

            TenantSummaryResponse result = tenantService.getTenantSummary(tenant.getTenantId());

            assertThat(result.getTenantId()).isEqualTo(tenant.getTenantId());
            assertThat(result.getTenantName()).isEqualTo("Acme");
        }

        @Test
        @DisplayName("returns null when the tenant does not exist")
        void returnsNullWhenNotFound() {
            UUID missingId = UUID.randomUUID();
            when(tenantRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThat(tenantService.getTenantSummary(missingId)).isNull();
        }
    }

    @Nested
    @DisplayName("createTenant")
    class CreateTenant {

        @Test
        @DisplayName("creates a top-level ORGANIZATION when no roles are given")
        void createsOrganizationByDefault() {
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
            CreateTenantRequest request = new CreateTenantRequest("Acme", "PRO", null, null);

            CreateTenantResponse response = tenantService.createTenant(request);

            assertThat(response.getTenantType()).isEqualTo(TenantType.ORGANIZATION);
            assertThat(response.getAccessLevel()).isEqualTo(TenantAccessLevel.TENANT_ADMIN);
            assertThat(response.getSubscriptionPlan()).isEqualTo("PRO");
        }

        @Test
        @DisplayName("defaults the subscription plan to BASIC when none is given")
        void defaultsSubscriptionPlan() {
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
            CreateTenantRequest request = new CreateTenantRequest("Acme", null, null, null);

            CreateTenantResponse response = tenantService.createTenant(request);

            assertThat(response.getSubscriptionPlan()).isEqualTo("BASIC");
        }

        @Test
        @DisplayName("creates a SUB_TENANT under a valid ORGANIZATION parent")
        void createsSubTenantUnderOrganization() {
            Tenant parent = TestDataFactory.organization("Acme");
            when(tenantRepository.findById(parent.getTenantId())).thenReturn(Optional.of(parent));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
            CreateTenantRequest request = new CreateTenantRequest(
                    "Acme Site 1", null, parent.getTenantId(), List.of("ROLE_ADMIN"));

            CreateTenantResponse response = tenantService.createTenant(request);

            assertThat(response.getTenantType()).isEqualTo(TenantType.SUB_TENANT);
            assertThat(response.getAccessLevel()).isEqualTo(TenantAccessLevel.OPERATIONAL);
        }

        @Test
        @DisplayName("rejects a SUB_TENANT request with no parent")
        void rejectsSubTenantWithoutParent() {
            CreateTenantRequest request = new CreateTenantRequest(
                    "Orphan", null, null, List.of("ROLE_ADMIN"));

            assertThatThrownBy(() -> tenantService.createTenant(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must have a parent");
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects an ORGANIZATION becoming a child of another ORGANIZATION")
        void rejectsOrganizationUnderOrganization() {
            Tenant parent = TestDataFactory.organization("Acme");
            when(tenantRepository.findById(parent.getTenantId())).thenReturn(Optional.of(parent));
            CreateTenantRequest request = new CreateTenantRequest(
                    "Acme Sub-org", null, parent.getTenantId(), List.of("ROLE_SUPER_ADMIN"));

            assertThatThrownBy(() -> tenantService.createTenant(request))
                    .isInstanceOf(IllegalStateException.class);
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a SUB_TENANT becoming a parent")
        void rejectsSubTenantAsParent() {
            Tenant grandparent = TestDataFactory.organization("Acme");
            Tenant parent = TestDataFactory.subTenant("Acme Site 1", grandparent);
            when(tenantRepository.findById(parent.getTenantId())).thenReturn(Optional.of(parent));
            CreateTenantRequest request = new CreateTenantRequest(
                    "Acme Site 1 Sub", null, parent.getTenantId(), List.of("ROLE_ADMIN"));

            assertThatThrownBy(() -> tenantService.createTenant(request))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("throws TenantNotFoundException when the parent does not exist")
        void throwsWhenParentMissing() {
            UUID missingParentId = UUID.randomUUID();
            when(tenantRepository.findById(missingParentId)).thenReturn(Optional.empty());
            CreateTenantRequest request = new CreateTenantRequest(
                    "Acme", null, missingParentId, null);

            assertThatThrownBy(() -> tenantService.createTenant(request))
                    .isInstanceOf(TenantNotFoundException.class);
            verify(tenantRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteTenant")
    class DeleteTenant {

        @Test
        @DisplayName("deletes the tenant when it exists")
        void deletesExistingTenant() {
            UUID tenantId = UUID.randomUUID();
            when(tenantRepository.existsById(tenantId)).thenReturn(true);

            tenantService.deleteTenant(tenantId);

            verify(tenantRepository).deleteById(tenantId);
        }

        @Test
        @DisplayName("is a no-op when the tenant does not exist")
        void noOpWhenMissing() {
            UUID tenantId = UUID.randomUUID();
            when(tenantRepository.existsById(tenantId)).thenReturn(false);

            tenantService.deleteTenant(tenantId);

            verify(tenantRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("getAllTenants")
    class GetAllTenants {

        @Test
        @DisplayName("delegates to the repository's paginated query and maps each entry")
        void returnsPagedSummaries() {
            Tenant tenant = TestDataFactory.organization("Acme");
            Pageable pageable = PageRequest.of(0, 20);
            when(tenantRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(tenant)));

            var page = tenantService.getAllTenants(pageable);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getTenantName()).isEqualTo("Acme");
        }
    }

    @Nested
    @DisplayName("getSubTenants")
    class GetSubTenants {

        @Test
        @DisplayName("returns top-level tenants when parentId is null")
        void returnsTopLevelWhenNull() {
            Tenant root = TestDataFactory.organization("Acme");
            when(tenantRepository.findByParentIsNull()).thenReturn(List.of(root));

            List<TenantSummaryResponse> result = tenantService.getSubTenants(null);

            assertThat(result).hasSize(1);
            verify(tenantRepository, never()).findByParent_TenantId(any());
        }

        @Test
        @DisplayName("returns top-level tenants when parentId is the virtual root UUID")
        void returnsTopLevelWhenVirtualRoot() {
            Tenant root = TestDataFactory.organization("Acme");
            when(tenantRepository.findByParentIsNull()).thenReturn(List.of(root));

            List<TenantSummaryResponse> result = tenantService.getSubTenants(
                    UUID.fromString("00000000-0000-0000-0000-000000000000"));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns children of a specific parent")
        void returnsChildrenOfParent() {
            Tenant parent = TestDataFactory.organization("Acme");
            Tenant child = TestDataFactory.subTenant("Acme Site 1", parent);
            when(tenantRepository.findByParent_TenantId(parent.getTenantId())).thenReturn(List.of(child));

            List<TenantSummaryResponse> result = tenantService.getSubTenants(parent.getTenantId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTenantName()).isEqualTo("Acme Site 1");
        }
    }

    @Nested
    @DisplayName("getCompaniesAndUsers")
    class GetCompaniesAndUsers {

        @Test
        @DisplayName("throws TenantNotFoundException when the root tenant does not exist")
        void throwsWhenRootMissing() {
            UUID missingId = UUID.randomUUID();
            when(tenantRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tenantService.getCompaniesAndUsers(missingId))
                    .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        @DisplayName("builds the full descendant tree using one batched query per level, not per node")
        void buildsTreeWithBatchedQueries() {
            Tenant root = TestDataFactory.organization("Acme");
            Tenant childA = TestDataFactory.subTenant("Site A", root);
            Tenant childB = TestDataFactory.subTenant("Site B", root);
            Tenant grandchild = TestDataFactory.subTenant("Line 1", childA);

            when(tenantRepository.findById(root.getTenantId())).thenReturn(Optional.of(root));
            when(tenantRepository.findByParent_TenantIdIn(List.of(root.getTenantId())))
                    .thenReturn(List.of(childA, childB));
            when(tenantRepository.findByParent_TenantIdIn(List.of(childA.getTenantId(), childB.getTenantId())))
                    .thenReturn(List.of(grandchild));
            when(tenantRepository.findByParent_TenantIdIn(List.of(grandchild.getTenantId())))
                    .thenReturn(List.of());

            List<CompanyWithUsersResponse> result = tenantService.getCompaniesAndUsers(root.getTenantId());

            assertThat(result).hasSize(1);
            CompanyWithUsersResponse rootDto = result.get(0);
            assertThat(rootDto.getCompany().getTenantName()).isEqualTo("Acme");
            assertThat(rootDto.getSubCompanies()).hasSize(2);
            CompanyWithUsersResponse childADto = rootDto.getSubCompanies().stream()
                    .filter(c -> c.getCompany().getTenantName().equals("Site A"))
                    .findFirst().orElseThrow();
            assertThat(childADto.getSubCompanies()).hasSize(1);
            assertThat(childADto.getSubCompanies().get(0).getCompany().getTenantName()).isEqualTo("Line 1");
        }
    }
}
