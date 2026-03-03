package com.joe.paymentgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the payment gateway.
 *
 * <p>Configures HTTP security for the REST API with the following policies:</p>
 * <ul>
 *   <li>Stateless session management (no server-side sessions — suitable for REST APIs).</li>
 *   <li>CSRF disabled (API is stateless and uses JSON, not form submissions).</li>
 *   <li>Transaction and key exchange endpoints require HTTP Basic authentication.</li>
 *   <li>Health check and H2 console endpoints are publicly accessible.</li>
 * </ul>
 *
 * <p>In production, this would be replaced with JWT/OAuth2 token-based authentication
 * and HTTPS would be enforced at the load balancer level.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Defines the security filter chain for all HTTP requests.
     *
     * @param http the HttpSecurity builder
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — this is a stateless REST API
                .csrf(csrf -> csrf.disable())

                // Allow H2 console frames (local dev only)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()))

                // Stateless sessions — no cookies or server-side session storage
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()

                        // All API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()

                        // Deny everything else
                        .anyRequest().denyAll()
                )

                // Use HTTP Basic authentication for simplicity
                .httpBasic(basic -> {});

        return http.build();
    }
}
