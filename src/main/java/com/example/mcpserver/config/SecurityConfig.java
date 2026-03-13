package com.example.mcpserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the MCP Server.
 * <p>
 * In production, you would configure proper authentication (OAuth2, API keys, mTLS, etc.) based on
 * your requirements.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  @Order(0)
  public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/mcp", "/mcp/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

    return http.build();
  }

  @Bean
  @Order(1)
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable()) // Disable CSRF for API endpoints
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .authorizeHttpRequests(auth -> auth
            // Allow MCP endpoints (include both /mcp and /mcp/** to avoid accidental 403 on root)
            .requestMatchers("/mcp", "/mcp/**").permitAll()
            // Allow actuator health, info, and mappings
            .requestMatchers("/actuator/health", "/actuator/info", "/actuator/mappings").permitAll()
            // Allow error path to surface real status codes (e.g., 404 on /mcp if unmapped)
            .requestMatchers("/error").permitAll()
            // Require authentication for other actuator endpoints
            .requestMatchers("/actuator/**").authenticated()
            // Allow H2 console for development
            .requestMatchers("/h2-console/**").permitAll()
            // Default: require authentication
            .anyRequest().authenticated()
        )
        // Allow H2 console frames
        .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

    return http.build();
  }
}
