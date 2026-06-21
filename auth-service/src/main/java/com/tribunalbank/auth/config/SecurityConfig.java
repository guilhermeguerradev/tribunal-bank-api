package com.tribunalbank.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
// SECURITY CONFIG — Regras de segurança HTTP da aplicação
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA (SRP):
// Essa classe define APENAS as regras de acesso da API:
// → quais endpoints são públicos (não precisam de token)
// → quais endpoints são protegidos (exigem JWT válido)
// → política de sessão (stateless)
// → criptografia de senha (BCrypt)
//
// O que NÃO é responsabilidade dessa classe:
// → Gerar/verificar JWT → JwtConfig + JwtService
// → Converter chaves PEM → PemKeyParser
// → Lógica de autenticação → AuthService
//
// PADRÃO DE DUPLA FILTER CHAIN:
// O Spring Security permite múltiplas SecurityFilterChain.
// Cada chain tem um securityMatcher que define para quais
// rotas ela se aplica.
//
// Usamos DUAS chains separadas porque:
//
// PROBLEMA com uma única chain:
// O oauth2ResourceServer instala o BearerTokenAuthenticationFilter
// que tenta processar JWT em TODA requisição — mesmo as públicas.
// Se não houver token, ele lança exceção antes do permitAll() ser avaliado.
// Resultado: endpoints públicos retornam 401 sem token.
//
// SOLUÇÃO com duas chains:
// → Chain 1 (pública): sem oauth2ResourceServer → sem filtro Bearer
//   O permitAll() funciona perfeitamente — nenhum token é exigido.
// → Chain 2 (protegida): com oauth2ResourceServer → valida JWT
//   Só se aplica às rotas que não casaram na chain 1.
//
// @Configuration — classe de configuração Spring, processada no startup.
// @EnableWebSecurity — ativa o módulo de segurança web do Spring Security.
//   Sem essa anotação as configurações de HttpSecurity são ignoradas.
// @EnableMethodSecurity — ativa segurança em nível de método com anotações:
//   @PreAuthorize("hasRole('ADMIN')") nos Controllers/Services.
//   Complementa a segurança de URL — dupla camada de proteção.
// ═══════════════════════════════════════════════════════════
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // ═══════════════════════════════════════════════════════
    // ENDPOINTS PÚBLICOS — Lista centralizada
    // ═══════════════════════════════════════════════════════
    //
    // Centralizar aqui evita duplicação entre as duas chains.
    // Se um endpoint mudar de nome, muda em um só lugar.
    // É um array de Strings porque securityMatcher aceita varargs.
    //
    // /** (double asterisk) → casa qualquer subpath recursivamente
    // Ex: /swagger-ui/** casa /swagger-ui/index.html, /swagger-ui/swagger.css etc.
    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/register",    // cadastro de novo usuário — sem token
            "/auth/login",       // login — recebe email/senha, retorna tokens
            "/auth/refresh",     // renovação do access token usando refresh token
            "/swagger-ui/**",    // recursos estáticos da interface Swagger UI
            "/swagger-ui.html",  // página de entrada do Swagger UI
            "/api-docs/**",      // JSON da especificação OpenAPI (usado pelo Swagger)
            "/actuator/health"   // healthcheck — consultado pelo Eureka e load balancers
    };

    // ═══════════════════════════════════════════════════════
    // CHAIN 1 — Filter Chain Pública
    // ═══════════════════════════════════════════════════════
    //
    // Processa APENAS os endpoints definidos em PUBLIC_ENDPOINTS.
    // Qualquer rota não listada aqui cai automaticamente na Chain 2.
    //
    // @Bean — registra como bean Spring gerenciado pelo container IoC.
    //
    // @Order(1) — prioridade máxima.
    // O Spring avalia as chains na ordem crescente.
    // A primeira chain cujo securityMatcher bater com a URL é usada.
    // As demais chains são ignoradas para aquela requisição.
    //
    // securityMatcher(...) — define o escopo desta chain.
    // Diferente de requestMatchers dentro de authorizeHttpRequests:
    // securityMatcher determina SE a chain é aplicada à requisição.
    // requestMatchers determina O QUE fazer DENTRO da chain.
    //
    // csrf().disable() — desabilita proteção CSRF.
    // CSRF (Cross-Site Request Forgery) protege formulários HTML com cookies de sessão.
    // APIs REST stateless com JWT não precisam — o token no header já é a proteção.
    // Manter CSRF habilitado em APIs REST causaria rejeição de todas as requisições.
    //
    // sessionManagement(STATELESS) — nunca cria sessão HTTP no servidor.
    // Cada requisição é completamente independente e self-contained via JWT.
    // Sem estado → escala horizontalmente sem compartilhar sessões entre instâncias.
    // Ex: 10 pods do auth-service funcionam sem precisar de Redis para sessões.
    //
    // authorizeHttpRequests(anyRequest().permitAll()):
    // Como securityMatcher já limitou a chain aos endpoints públicos,
    // podemos simplesmente liberar tudo que chegar aqui.
    // Não há necessidade de oauth2ResourceServer — nenhum JWT é verificado.
    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http
                // Limita esta chain APENAS aos endpoints públicos
                .securityMatcher(PUBLIC_ENDPOINTS)

                // Sem CSRF — API REST stateless com JWT
                .csrf(AbstractHttpConfigurer::disable)

                // Sem sessão — cada requisição é independente
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Libera tudo que chegou aqui — já está no escopo público
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll());

        return http.build();
    }

    // ═══════════════════════════════════════════════════════
    // CHAIN 2 — Filter Chain Protegida
    // ═══════════════════════════════════════════════════════
    //
    // Captura TODAS as rotas que não casaram na Chain 1.
    // Sem securityMatcher → aplica-se a qualquer URL restante.
    //
    // @Order(2) — prioridade secundária.
    // Só é avaliada se a Chain 1 não casou com a URL.
    //
    // oauth2ResourceServer(oauth2 -> oauth2.jwt(...)):
    // Instala o BearerTokenAuthenticationFilter na chain.
    // Esse filtro intercepta toda requisição e:
    // → Extrai o token do header: Authorization: Bearer eyJhbG...
    // → Delega a validação ao JwtDecoder (bean do JwtConfig)
    // → O JwtDecoder verifica assinatura, expiração e integridade
    // → Sucesso: popula o SecurityContext com o usuário autenticado
    // → Falha:   retorna 401 Unauthorized automaticamente
    //
    // jwt(jwt -> {}) — usa o JwtDecoder bean registrado em JwtConfig.
    // O lambda vazio significa "use as configurações padrão".
    // O Spring injeta o JwtDecoder automaticamente por tipo.
    //
    // anyRequest().authenticated() — toda rota nessa chain exige autenticação.
    // Se o BearerTokenFilter não populou o SecurityContext → 401.
    // Se populou mas o usuário não tem permissão → 403 Forbidden.
    @Bean
    @Order(2)
    public SecurityFilterChain protectedFilterChain(HttpSecurity http) throws Exception {
        http
                // Sem CSRF — API REST stateless com JWT
                .csrf(AbstractHttpConfigurer::disable)

                // Sem sessão — o JWT carrega todas as informações
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Toda rota nessa chain exige autenticação via JWT
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().authenticated())

                // Instala o filtro JWT apenas nessa chain (rotas protegidas)
                // O JwtDecoder do JwtConfig é injetado automaticamente
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> {}));

        return http.build();
    }

    // ═══════════════════════════════════════════════════════
    // PASSWORD ENCODER — BCrypt
    // ═══════════════════════════════════════════════════════
    //
    // Exposto como bean para ser injetado em AuthService.
    //
    // Por que BCrypt e não MD5, SHA-256 ou SHA-512?
    // MD5/SHA são algoritmos de HASH RÁPIDO — projetados para velocidade.
    // Velocidade é INIMIGA da segurança de senhas — facilita brute force.
    //
    // BCrypt é especificamente projetado para ser LENTO e CUSTOSO:
    // → Fator de custo (strength 10) → ~100ms por hash na CPU atual
    // → Inclui salt automático — mesmo a mesma senha gera hashes diferentes
    // → À medida que CPUs ficam mais rápidas, aumenta-se o strength
    //
    // Com strength 10, um atacante com GPU potente levaria anos
    // para tentar todas as combinações de uma senha razoável.
    //
    // Irreversível por design — o sistema NUNCA sabe a senha original.
    // Para verificar: encoder.matches("senhaDigitada", hashSalvo)
    // O BCrypt recalcula o hash e compara — nunca descriptografa.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // ═══════════════════════════════════════════════════════
    // AUTHENTICATION MANAGER
    // ═══════════════════════════════════════════════════════
    //
    // Exposto como bean para ser injetado no AuthService.
    //
    // Usado no login para validar email + senha:
    // authManager.authenticate(
    //     new UsernamePasswordAuthenticationToken(email, senha)
    // )
    //
    // Internamente o AuthenticationManager:
    // → Chama UsuarioDetailsService.loadUserByUsername(email)
    // → Compara a senha digitada com o hash salvo via BCrypt
    // → Sucesso: retorna Authentication populado com o usuário
    // → Falha:   lança BadCredentialsException → GlobalExceptionHandler → 401
    //
    // AuthenticationConfiguration é um bean interno do Spring Security
    // que já possui o AuthenticationManager configurado com o
    // UsuarioDetailsService e PasswordEncoder que registramos como beans.
    // Não precisamos configurar manualmente — o Spring conecta tudo.
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}