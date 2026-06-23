package com.tribunalbank.conta.dto;

import com.tribunalbank.conta.entity.Conta;
import com.tribunalbank.conta.entity.TipoConta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ═══════════════════════════════════════════════════════════
// CONTA RESPONSE — DTO de saída para retorno ao cliente
// ═══════════════════════════════════════════════════════════
//
// Por que separar Request e Response?
// → Request: o que o cliente MANDA (tipo + limite)
// → Response: o que o cliente RECEBE (todos os dados)
// → Mudanças internas na entidade não quebram o contrato da API
//
// saldoDisponivel calculado aqui (saldo + limite):
// A lógica que estava na entidade (Rich Domain Model)
// foi movida para cá — calculado no momento da conversão.
// O DTO de resposta é o lugar certo para dados derivados
// que são apenas para exibição.
//
// numeroCompleto:
// Formato amigável para exibição: "0001-12345678-9"
// Calculado aqui — não salvo no banco (dado derivado)
// ═══════════════════════════════════════════════════════════
public record ContaResponse(
        String id,
        String clienteId,
        String numeroCompleto,    // "0001-12345678-9" — formatado para exibição
        TipoConta tipo,
        BigDecimal saldo,
        BigDecimal limite,
        BigDecimal saldoDisponivel, // saldo + limite — calculado aqui
        boolean ativa,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    // Método estático de conversão entidade → DTO
    // Centraliza a conversão — se a entidade mudar, muda só aqui
    // Chamado no service: ContaResponse.from(conta)
    public static ContaResponse from(Conta conta) {
        return new ContaResponse(
                conta.getId(),
                conta.getClienteId(),

                // Monta o número completo formatado para exibição
                // Ex: agencia=0001, conta=00000001, digito=5
                // → "0001-00000001-5"
                conta.getNumeroAgencia() + "-" +
                        conta.getNumeroConta()   + "-" +
                        conta.getDigitoVerificador(),

                conta.getTipo(),
                conta.getSaldo(),
                conta.getLimite(),

                // Calcula saldo disponível no momento da conversão
                // saldo + limite = saldo real disponível para uso
                // Ex: saldo=-200, limite=500 → disponível=300
                conta.getSaldo().add(conta.getLimite()),

                conta.isAtiva(),
                conta.getCriadoEm(),
                conta.getAtualizadoEm()
        );
    }
}