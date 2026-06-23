package com.tribunalbank.conta.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// ═══════════════════════════════════════════════════════════
// SECURITY CONFIG — Regras de segurança do conta-service
// ═══════════════════════════════════════════════════════════
//
// Igual ao Cliente Service:
// → Chain 1 (@Order 1) → Swagger e Actuator → sem JWT
// → Chain 2 (@Order 2) → todos os endpoints de negócio → JWT obrigatório
//
// Diferença do Auth Service:
// Auth Service tem rotas públicas de negócio (/auth/register, /auth/login)
// Conta Service NÃO tem — toda rota de negócio exige JWT
// ═══════════════════════════════════════════════════════════
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/actuator/health"
    };

    // Chain 1 — ferramentas públicas (sem JWT)
    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http)
            throws Exception {
        http
                .securityMatcher(PUBLIC_ENDPOINTS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll());

        return http.build();
    }

    // Chain 2 — endpoints de negócio (JWT obrigatório)
    @Bean
    @Order(2)
    public SecurityFilterChain protectedFilterChain(HttpSecurity http)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> {}));

        return http.build();
    }
}