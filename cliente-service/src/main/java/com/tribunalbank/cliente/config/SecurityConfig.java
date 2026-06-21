package com.tribunalbank.cliente.config;

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
// SECURITY CONFIG — Regras de segurança do cliente-service
// ═══════════════════════════════════════════════════════════
//
// DIFERENÇA em relação ao auth-service:
// O cliente-service NÃO tem rotas públicas de negócio.
// Toda rota de /clientes exige JWT válido.
// Apenas Swagger e Actuator ficam públicos (ferramentas).
//
// Por que ainda duas chains?
// Swagger e Actuator precisam ser acessados sem token.
// Se colocássemos tudo em uma chain com oauth2ResourceServer,
// o BearerTokenFilter bloquearia o Swagger sem token — igual ao auth-service.
//
// O JWT gerado pelo auth-service é verificado aqui
// usando a MESMA chave pública RSA.
// O cliente-service só precisa da chave pública — nunca da privada.
// ═══════════════════════════════════════════════════════════
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Rotas de ferramentas — públicas em todos os serviços
    private static final String[] PUBLIC_ENDPOINTS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/actuator/health"
    };

    // ── Chain 1 — Rotas de ferramentas (sem JWT) ─────────
    // Swagger e health check ficam acessíveis sem autenticação
    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PUBLIC_ENDPOINTS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll());

        return http.build();
    }

    // ── Chain 2 — Rotas de negócio (exigem JWT) ──────────
    // TODA rota de /clientes exige token válido do auth-service
    // O JwtDecoder do JwtConfig verifica a assinatura com a chave pública RSA
    @Bean
    @Order(2)
    public SecurityFilterChain protectedFilterChain(HttpSecurity http) throws Exception {
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