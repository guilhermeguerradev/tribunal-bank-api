package com.tribunalbank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// ═══════════════════════════════════════════════════════════════════
// DTO de Renovação de Token
// ═══════════════════════════════════════════════════════════════════
// Dados que o cliente manda quando o access token expira
// POST /auth/refresh
//
// Exemplo de JSON que o cliente envia:
// {
//   "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
// }
//
// FLUXO após receber esse DTO:
// 1. RefreshTokenService busca o token no banco
// 2. Verifica se existe, não está revogado e não expirou
// 3. Revoga o token antigo (rotação de token — segurança)
// 4. Gera novo access token (15min) + novo refresh token (7 dias)
// 5. Retorna AuthResponse com os dois novos tokens
//
// Por que revogar o token antigo ao renovar?
// → Se o refresh token foi roubado, o atacante só consegue
//   usá-lo uma vez — na próxima tentativa ele já foi revogado
//   Isso se chama Refresh Token Rotation
@Schema(description = "Dados para renovação do access token expirado")
public record RefreshRequest(

        // O UUID do refresh token recebido no login
        // Buscado no banco para validar antes de renovar
        @Schema(
                description = "Refresh token recebido no login ou na última renovação",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @NotBlank(message = "Refresh token é obrigatório")
        String refreshToken

) {}