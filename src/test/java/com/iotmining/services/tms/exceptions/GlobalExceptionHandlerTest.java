package com.iotmining.services.tms.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("maps TenantNotFoundException to 404")
    void mapsTenantNotFound() {
        UUID id = UUID.randomUUID();
        ResponseEntity<?> response = handler.handleTenantNotFoundException(new TenantNotFoundException(id));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("statusCode", 404);
    }

    @Test
    @DisplayName("maps IllegalStateException to 409")
    void mapsIllegalState() {
        ResponseEntity<?> response = handler.handleIllegalStateException(new IllegalStateException("conflict"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("message", "conflict");
    }

    @Test
    @DisplayName("maps IllegalArgumentException to 400")
    void mapsIllegalArgument() {
        ResponseEntity<?> response = handler.handleIllegalArgumentException(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("message", "bad input");
    }

    @Test
    @DisplayName("maps AccessDeniedException to 403")
    void mapsAccessDenied() {
        ResponseEntity<?> response = handler.handleAccessDeniedException(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("message", "denied");
    }

    @Test
    @DisplayName("maps unexpected exceptions to a generic 500 with no internal detail leaked")
    void mapsUnexpectedException() {
        ResponseEntity<?> response = handler.handleUnexpectedException(new RuntimeException("some internal secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("message", "Something went wrong. Please try again later.")
                .doesNotContainValue("some internal secret");
    }
}
