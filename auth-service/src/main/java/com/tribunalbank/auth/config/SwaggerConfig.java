package com.tribunalbank.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// ═══════════════════════════════════════════════════════════════════
// SWAGGER CONFIG — Documentação automática da API
// ═══════════════════════════════════════════════════════════════════
//
// O Swagger (OpenAPI) gera automaticamente uma interface visual
// onde qualquer desenvolvedor consegue ver e testar todos os
// endpoints da API sem precisar de Postman ou curl
//
// Acesse em: http://localhost:8081/swagger-ui.html
//
// O que você vai encontrar lá:
// → Lista de todos os endpoints com descrição
// → Campos de entrada com exemplos preenchidos
// → Botão para executar as requisições direto no browser
// → Botão Authorize para informar o JWT e testar endpoints protegidos
//
// ═══════════════════════════════════════════════════════════════════

// @OpenAPIDefinition → configura as informações gerais da documentação
//                      aparece no topo da página do Swagger UI
//
// @Info → título, versão e descrição da API
//         útil para identificar qual serviço está sendo documentado
//         lembre que teremos vários microsserviços com Swagger próprio
@OpenAPIDefinition(
        info = @Info(
                title = "Tribunal Bank API — Auth Service",
                version = "1.0",
                description = """
                Serviço responsável por autenticação e autorização.
                
                Funcionalidades:
                - Cadastro de usuários
                - Login com JWT RSA
                - Renovação de token (Refresh Token Rotation)
                - Logout com revogação de token
                - Controle de roles (ROLE_USER, ROLE_ADMIN)
                """
        )
)

// @SecurityScheme → configura o botão "Authorize" no Swagger UI
//
// Sem isso você não consegue testar endpoints protegidos pelo JWT
// diretamente no Swagger — teria que usar o Postman
//
// Com isso o fluxo no Swagger UI fica:
// 1. Chama POST /auth/login → copia o accessToken da resposta
// 2. Clica no botão Authorize (cadeado) no topo da página
// 3. Cola o token no campo: Bearer eyJhbGci...
// 4. Clica em Authorize — agora todos os endpoints protegidos
//    vão incluir o token automaticamente nas requisições
//
// name = "bearerAuth"    → nome do esquema — referenciado nos controllers
//                          com @SecurityRequirement(name = "bearerAuth")
// type = HTTP            → autenticação via header HTTP
// scheme = "bearer"      → padrão Bearer token
// bearerFormat = "JWT"   → informa que o token é um JWT (só documentação)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Cole o access token JWT recebido no login. Exemplo: eyJhbGci..."
)
@Configuration
public class SwaggerConfig {
    // Toda a configuração é feita pelas anotações acima
    // Não precisa de nenhum @Bean aqui
    // O springdoc-openapi lê as anotações e gera a documentação automaticamente
}
