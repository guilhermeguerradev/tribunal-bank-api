package com.tribunalbank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// ═══════════════════════════════════════════════════════════════════
// DTO — Data Transfer Object
// ═══════════════════════════════════════════════════════════════════
// DTO é um objeto que trafega dados entre o cliente e a API
// Ele NÃO é uma entidade JPA — nunca vai ao banco diretamente
// Serve como um "envelope" que define exatamente o que entra e sai
//
// FLUXO:
// Cliente manda JSON → Spring converte para RegisterRequest
//                    → Controller passa para o Service
//                    → Service usa os dados para criar um Usuario (entidade)
//                    → Usuario vai ao banco
//
// Por que record em vez de class?
// record é imutável por padrão — perfeito para DTOs que só transportam dados
// Gera automaticamente: construtor, getters, equals, hashCode e toString
// Muito menos código que uma class com Lombok
//
// @Schema → anotação do Swagger que documenta o DTO na interface visual
//           aparece no Swagger UI em http://localhost:8081/swagger-ui.html
@Schema(description = "Dados necessários para cadastro de novo usuário no sistema")
public record RegisterRequest(

        // @Schema      → documenta o campo no Swagger com descrição e exemplo
        // @NotBlank    → não aceita nulo, vazio ("") ou só espaços ("   ")
        // @Email       → valida o formato do email — precisa ter @ e domínio válido
        //
        // Se o cliente mandar email inválido:
        // → Spring lança MethodArgumentNotValidException automaticamente
        // → GlobalExceptionHandler captura e retorna 400 com a mensagem abaixo
        @Schema(
                description = "Email do usuário — deve ser único no sistema",
                example = "joao@tribunalbank.com"
        )
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        // @Size → valida o tamanho da string
        //         min = 6   → mínimo 6 caracteres para ser uma senha segura
        //         max = 100 → limite para não estourar o campo no banco
        //
        // A senha aqui chega em texto puro — é o Service que vai
        // criptografar com BCrypt antes de salvar no banco
        // NUNCA salvar senha sem criptografia!
        @Schema(
                description = "Senha do usuário — mínimo 6 caracteres",
                example = "minhasenha123",
                minLength = 6,
                maxLength = 100
        )
        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, max = 100, message = "Senha deve ter entre 6 e 100 caracteres")
        String senha

) {}