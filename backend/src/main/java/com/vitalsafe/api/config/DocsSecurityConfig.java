package com.vitalsafe.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Only exists when the API docs are switched on. Swagger UI needs to load scripts and styles, so the docs
 * paths get their own header policy. Every other path keeps the strict default-src 'none' policy.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class DocsSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain docsFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; "
                                + "frame-ancestors 'none'; base-uri 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.disable()))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}