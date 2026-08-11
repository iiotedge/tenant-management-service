package com.iotmining.services.tms.services;

import com.iotmining.common.data.tenant.TenantAccessLevel;
import com.iotmining.common.data.tenant.TenantType;
import com.iotmining.services.tms.model.Tenant;
import com.iotmining.services.tms.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantInitializer")
class TenantInitializerTest {

    @Mock private TenantRepository tenantRepository;

    private TenantInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new TenantInitializer(tenantRepository);
    }

    @Test
    @DisplayName("does nothing when the platform tenant already exists")
    void skipsWhenAlreadyExists() {
        when(tenantRepository.existsById(TenantInitializer.SYSTEM_TENANT_ID)).thenReturn(true);

        initializer.init();

        verify(tenantRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("creates the platform tenant when missing")
    void createsWhenMissing() {
        when(tenantRepository.existsById(TenantInitializer.SYSTEM_TENANT_ID)).thenReturn(false);
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);

        initializer.init();

        verify(tenantRepository).save(captor.capture());
        Tenant saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TenantInitializer.SYSTEM_TENANT_ID);
        assertThat(saved.getTenantType()).isEqualTo(TenantType.PLATFORM);
        assertThat(saved.getAccessLevel()).isEqualTo(TenantAccessLevel.SUPER_ADMIN);
        assertThat(saved.getParent()).isNull();
    }
}
