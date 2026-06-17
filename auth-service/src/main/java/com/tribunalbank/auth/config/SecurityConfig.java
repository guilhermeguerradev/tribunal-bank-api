package com.tribunalbank.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// ═══════════════════════════════════════════════════════════
// SECURITY CONFIG — Regras de segurança HTTP
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA:
// Define APENAS as regras de acesso da API:
// → quais endpoints são públicos
// → quais endpoints precisam de JWT
// → política de sessão (stateless)
// → criptografia de senha
//
// O JWT em si (geração e verificação) está no JwtConfig
// Separar essas responsabilidades facilita manutenção:
// → Mudar regras de acesso? Mexe só aqui
// → Mudar algoritmo JWT?   Mexe só no JwtConfig
//
// STATELESS — conceito fundamental:
// O servidor NÃO guarda nenhuma sessão entre requisições
// Cada requisição precisa trazer o JWT no header
// Isso permite escalar horizontalmente sem compartilhar estado
// Ex: 10 instâncias do auth-service funcionam independentemente
// ═══════════════════════════════════════════════════════════
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // ═══════════════════════════════════════════════════════
    // SECURITY FILTER CHAIN — Regras de acesso por endpoint
    // ═══════════════════════════════════════════════════════
    //
    // O Spring Security aplica essas regras em TODA requisição
    // antes de chegar no Controller
    //
    // FLUXO:
    // Requisição chega
    //      ↓
    // SecurityFilterChain verifica as regras
    //      ↓
    // Endpoint público?  → passa direto para o Controller
    // Endpoint protegido? → valida o JWT primeiro
    //      ↓
    // JWT válido?   → passa para o Controller
    // JWT inválido? → retorna 401 automaticamente
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Desabilita CSRF — não necessário em APIs REST stateless
                // CSRF protege formulários HTML com sessão no servidor
                // APIs JWT não precisam — o token já é a proteção
                .csrf(AbstractHttpConfigurer::disable)

                // Regras de autorização por endpoint
                .authorizeHttpRequests(auth -> auth

                        // ── Endpoints PÚBLICOS ──────────────────────
                        // Não precisam de JWT — qualquer pessoa acessa
                        .requestMatchers(
                                "/auth/register",    // cadastro de novo usuário
                                "/auth/login",       // login — gera os tokens
                                "/auth/refresh",     // renovação do access token
                                "/swagger-ui/**",    // interface do Swagger
                                "/swagger-ui.html",  // página principal do Swagger
                                "/api-docs/**",      // JSON da documentação OpenAPI
                                "/actuator/health"   // healthcheck para o Eureka
                        ).permitAll()

                        // ── Endpoints PROTEGIDOS ────────────────────
                        // Qualquer outro endpoint exige JWT válido no header:
                        // Authorization: Bearer eyJhbGci...
                        .anyRequest().authenticated()
                )

                // Política de sessão STATELESS
                // NEVER_CREATE → nunca cria sessão HTTP no servidor
                // Cada requisição é completamente independente
                // O JWT carrega todas as informações necessárias
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configura o Resource Server OAuth2 para validar JWT
                // O JwtDecoder vem do JwtConfig automaticamente via @Bean
                // Spring injeta sem precisar referenciar explicitamente
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> {})
                );

        return http.build();
    }

    // ═══════════════════════════════════════════════════════
    // PASSWORD ENCODER — BCrypt
    // ═══════════════════════════════════════════════════════
    //
    // Fica aqui porque é parte da configuração de segurança
    // não de JWT — por isso não foi para o JwtConfig
    //
    // strength 10 = padrão do mercado (~100ms por hash)
    // Irreversível — o sistema nunca sabe a senha original
    // Para verificar: encoder.matches(senhaDigitada, hashSalvo)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // ═══════════════════════════════════════════════════════
    // AUTHENTICATION MANAGER
    // ═══════════════════════════════════════════════════════
    //
    // Usado pelo AuthService para validar email + senha no login:
    // authenticationManager.authenticate(
    //     new UsernamePasswordAuthenticationToken(email, senha)
    // )
    //
    // Se as credenciais forem inválidas lança BadCredentialsException
    // que o GlobalExceptionHandler captura e retorna 401
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}