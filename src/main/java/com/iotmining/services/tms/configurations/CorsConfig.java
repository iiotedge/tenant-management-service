package com.iotmining.services.tms.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * Dev-only CORS. In prod, Nginx already adds CORS headers for TMS's routes
 * (see iiotedge-cli.sh), so no bean is defined for that profile here - the
 * shared SecurityAutoConfiguration (common/security) only wires Spring-managed
 * CORS when a CorsConfigurationSource bean exists, and stays disabled
 * otherwise, to avoid duplicate Access-Control-Allow-Origin headers.
 */
@Configuration
public class CorsConfig {

    @Bean
    @Profile("dev")
    public CorsConfigurationSource devCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://localhost:3002",
                "https://*.iiotedge.in",
                "https://iiotedge.in"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
