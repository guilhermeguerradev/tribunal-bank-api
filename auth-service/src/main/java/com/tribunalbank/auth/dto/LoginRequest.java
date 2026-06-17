package com.tribunalbank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// ═══════════════════════════════════════════════════════════════════
// DTO de Login
// ═══════════════════════════════════════════════════════════════════
// Dados que o cliente manda para autenticar no sistema
// POST /auth/login
//
// Exemplo de JSON que o cliente envia:
// {
//   "email": "joao@tribunalbank.com",
//   "senha": "minhasenha123"
// }
//
// FLUXO após receber esse DTO:
// 1. Spring valida os campos com @NotBlank e @Email
// 2. AuthService busca o usuário pelo email no banco
// 3. BCrypt compara a senha enviada com o hash salvo no banco
// 4. Se válido → gera access token (15min) + refresh token (7 dias)
// 5. Retorna AuthResponse com os dois tokens
@Schema(description = "Dados para autenticação do usuário")
public record LoginRequest(

        @Schema(
                description = "Email cadastrado no sistema",
                example = "joao@tribunalbank.com"
        )
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        // Não tem @Size aqui intencionalmente
        // No login só verificamos se a senha não está vazia
        // O tamanho já foi validado no cadastro
        @Schema(
                description = "Senha do usuário",
                example = "minhasenha123"
        )
        @NotBlank(message = "Senha é obrigatória")
        String senha

) {}