package com.joe.paymentgateway.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Jackson {@link ObjectMapper} bean configuration.
 *
 * <p>Spring Boot 4.x uses Jackson 3.x ({@code tools.jackson} package), which includes
 * several improvements over Jackson 2.x:</p>
 * <ul>
 *   <li>{@code JavaTimeModule} is built-in — no manual registration needed.</li>
 *   <li>{@code WRITE_DATES_AS_TIMESTAMPS} is disabled by default — dates serialize
 *       as ISO 8601 strings (e.g., "2026-02-23T10:30:00Z") out of the box.</li>
 *   <li>Date/time features moved to {@code DateTimeFeature} enum (separate from
 *       {@code SerializationFeature}).</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates and configures the application-wide ObjectMapper.
     *
     * <p>Jackson 3.x handles Java 8+ date/time types (Instant, LocalDateTime, etc.)
     * natively without additional modules or configuration.</p>
     *
     * @return a configured ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }
}
