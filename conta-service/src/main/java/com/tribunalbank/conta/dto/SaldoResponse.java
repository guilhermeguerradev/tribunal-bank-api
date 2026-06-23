package com.tribunalbank.conta.dto;

import com.tribunalbank.conta.entity.Conta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ═══════════════════════════════════════════════════════════
// SALDO RESPONSE — DTO específico para consulta de saldo
// ═══════════════════════════════════════════════════════════
//
// Por que um DTO separado para saldo?
// → O cliente pode consultar saldo sem ver todos os dados da conta
// → Retorna só o essencial: saldo, limite e disponível
// → Segurança: não expõe id interno, clienteId etc
// → Performance: menor payload na resposta
//
// Endpoint: GET /contas/{id}/saldo
// ═══════════════════════════════════════════════════════════
public record SaldoResponse(
        String numeroConta,       // "0001-00000001-5" — identificação amigável
        BigDecimal saldo,         // saldo atual (pode ser negativo com cheque especial)
        BigDecimal limite,        // limite do cheque especial
        BigDecimal saldoDisponivel, // saldo + limite
        LocalDateTime consultadoEm  // momento exato da consulta
) {
    public static SaldoResponse from(Conta conta) {
        return new SaldoResponse(
                conta.getNumeroAgencia() + "-" +
                        conta.getNumeroConta()   + "-" +
                        conta.getDigitoVerificador(),
                conta.getSaldo(),
                conta.getLimite(),
                conta.getSaldo().add(conta.getLimite()),
                LocalDateTime.now() // momento exato da consulta
        );
    }
}