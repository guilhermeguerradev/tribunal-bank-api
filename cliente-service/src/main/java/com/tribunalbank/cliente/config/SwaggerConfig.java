package com.tribunalbank.cliente.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// ═══════════════════════════════════════════════════════════
// SWAGGER CONFIG — Personalização da documentação OpenAPI
// ═══════════════════════════════════════════════════════════
//
// Configura o Swagger UI para:
// → Mostrar informações do serviço (nome, versão, descrição)
// → Adicionar campo de Bearer token no Swagger UI
//   para testar endpoints protegidos diretamente pela interface
//
// Sem essa config o Swagger funciona, mas não tem o botão
// "Authorize" para inserir o JWT nos testes.
// ═══════════════════════════════════════════════════════════
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // Nome do esquema de segurança — referenciado abaixo
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                // Informações exibidas no topo do Swagger UI
                .info(new Info()
                        .title("Cliente Service API")
                        .description("Gerenciamento de clientes e endereços — Tribunal Bank")
                        .version("1.0.0"))

                // Adiciona o botão "Authorize" no Swagger UI
                // Permite inserir o Bearer token para testar endpoints protegidos
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))

                // Define o esquema de segurança Bearer JWT
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        // HTTP = usa o header Authorization automaticamente
                                        .type(SecurityScheme.Type.HTTP)
                                        // bearer = esquema Bearer (padrão OAuth2/JWT)
                                        .scheme("bearer")
                                        // bearerFormat = dica visual no Swagger (não funcional)
                                        .bearerFormat("JWT")));
    }
}