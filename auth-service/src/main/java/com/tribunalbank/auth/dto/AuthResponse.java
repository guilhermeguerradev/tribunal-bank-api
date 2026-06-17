package com.tribunalbank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// ═══════════════════════════════════════════════════════════════════
// DTO de Resposta — o que o sistema devolve após autenticação
// ═══════════════════════════════════════════════════════════════════
// Retornado nos endpoints:
// → POST /auth/login    (login bem sucedido)
// → POST /auth/refresh  (renovação de token)
//
// Exemplo de JSON que o cliente recebe:
// {
//   "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
//   "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
//   "tipo": "Bearer",
//   "email": "joao@tribunalbank.com"
// }
//
// O cliente usa o accessToken assim em toda requisição:
// Header: Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
//
// Quando o accessToken expirar (15 min) o cliente usa o
// refreshToken para pegar um novo sem fazer login de novo
@Schema(description = "Resposta de autenticação contendo os tokens JWT")
public record AuthResponse(

        // Token JWT de curta duração — expira em 15 minutos
        // Assinado com chave RSA privada — verificado com chave pública
        // Contém as informações do usuário (email, roles) dentro do payload
        // Enviado no header de toda requisição autenticada
        @Schema(
                description = "Token de acesso JWT — expira em 15 minutos",
                example = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJqb2FvQHRyaWJ1..."
        )
        String accessToken,

        // Token de longa duração — expira em 7 dias
        // Salvo no banco para poder ser revogado a qualquer momento
        // NÃO é um JWT — é um UUID aleatório gerado pelo sistema
        // Mais simples e mais seguro para renovação de sessão
        @Schema(
                description = "Token de renovação — expira em 7 dias",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        String refreshToken,

        // Tipo do token — sempre "Bearer" no padrão OAuth2/JWT
        // O cliente precisa incluir esse prefixo no header:
        // Authorization: Bearer <accessToken>
        @Schema(
                description = "Tipo do token — sempre Bearer",
                example = "Bearer"
        )
        String tipo,

        // Email retornado para o frontend saber quem está logado
        // Evita que o frontend precise decodificar o JWT para pegar o email
        @Schema(
                description = "Email do usuário autenticado",
                example = "joao@tribunalbank.com"
        )
        String email

) {}
