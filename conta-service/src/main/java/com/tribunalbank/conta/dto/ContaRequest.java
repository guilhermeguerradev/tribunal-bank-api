package com.tribunalbank.conta.dto;

import com.tribunalbank.conta.entity.TipoConta;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;


// ═══════════════════════════════════════════════════════════
// CONTA REQUEST — DTO de entrada para criação de conta
// ═══════════════════════════════════════════════════════════
//
// O clienteId NÃO vem no body da requisição.
// Ele é extraído do JWT no controller:
// @AuthenticationPrincipal Jwt jwt → jwt.getSubject()
// Isso garante que um usuário só pode abrir conta para si mesmo.
//
// Por que só tipo e limite?
// → id → gerado pelo sistema (UUID)
// → clienteId → extraído do JWT (segurança)
// → numeroAgencia, numeroConta, digito → gerados pelo service
// → saldo → sempre começa em ZERO
// → ativa → sempre começa true
// → criadoEm, atualizadoEm → auditoria automática
//
// O cliente só precisa informar:
// → Qual tipo de conta quer abrir
// → Qual limite de cheque especial (pode ser zero)
// ═══════════════════════════════════════════════════════════
public record ContaRequest(

        // @NotNull → campo obrigatório — não pode ser null
        // @NotBlank não funciona em Enum — use @NotNull
        // Jackson deserializa "CORRENTE", "POUPANCA" etc para o Enum
        // Se vier valor inválido → 400 Bad Request automático
        @NotNull(message = "Tipo da conta é obrigatório")
        TipoConta tipo,

        // Limite do cheque especial — opcional
        // Se não informado → null → service usa BigDecimal.ZERO
        //
        // @DecimalMin("0.00") → limite não pode ser negativo
        // message → mensagem exibida se violar a regra
        // inclusive = true → 0.00 é permitido (sem limite)
        @DecimalMin(value = "0.00",
                inclusive = true,
                message = "Limite não pode ser negativo")
        BigDecimal limite

) {}