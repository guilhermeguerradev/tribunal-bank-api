package com.tribunalbank.conta.service;

import com.tribunalbank.conta.repository.ContaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// ═══════════════════════════════════════════════════════════
// NUMERO CONTA SERVICE — Geração do número da conta bancária
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA (SRP):
// Essa classe tem UMA responsabilidade:
// gerar o número completo de uma nova conta bancária.
//
// Por que separar do ContaService?
// A geração de número de conta é uma regra de negócio
// complexa e independente — tem seu próprio algoritmo
// (sequencial + dígito verificador).
// Separar facilita testar isoladamente e reutilizar.
//
// FORMATO DO NÚMERO:
// Agência:  0001         (fixa para todo o sistema)
// Conta:    00000001     (sequencial via SEQUENCE do PostgreSQL)
// Dígito:   X            (calculado via Módulo 10)
// Exibição: 0001-00000001-X
//
// THREAD-SAFETY:
// O sequencial vem do PostgreSQL (nextval) — atômico por natureza.
// Impossível gerar o mesmo número duas vezes mesmo com
// múltiplas threads simultâneas.
// ═══════════════════════════════════════════════════════════
@Slf4j
@Service
@RequiredArgsConstructor
public class NumeroContaService {

    // Agência fixa — nosso banco tem uma agência
    // Constante em vez de @Value porque não muda por ambiente
    private static final String AGENCIA_PADRAO = "0001";

    private final ContaRepository contaRepository;

    // ── Gera número da conta ─────────────────────────────
    //
    // Retorna array com 3 posições:
    // [0] → numeroAgencia  "0001"
    // [1] → numeroConta    "00000001"
    // [2] → digito         "5"
    //
    // Por que array e não record/DTO?
    // É um retorno interno — nunca sai do service layer.
    // Array simples evita criar uma classe só para isso.
    // Um record seria mais expressivo mas para 3 campos
    // em uso interno o array é aceitável.
    public String[] gerar() {
        // Busca o próximo número da SEQUENCE do PostgreSQL
        // nextval() é atômico — thread-safe por natureza
        Long sequencial = contaRepository.proximoNumeroConta();

        // Formata com zeros à esquerda — sempre 8 dígitos
        // %08d → preenche com zeros até ter 8 dígitos
        // Ex: 1 → "00000001", 1234 → "00001234"
        String numeroConta = String.format("%08d", sequencial);

        // Calcula o dígito verificador
        String digito = calcularDigito(numeroConta);

        log.debug("Número de conta gerado: {}-{}-{}",
                AGENCIA_PADRAO, numeroConta, digito);

        return new String[]{AGENCIA_PADRAO, numeroConta, digito};
    }

    // ── Calcula dígito verificador — Módulo 10 ───────────
    //
    // Algoritmo Módulo 10 (usado por bancos brasileiros):
    //
    // EXEMPLO com "00000001":
    // Dígitos:  0  0  0  0  0  0  0  1
    // Pesos:    2  1  2  1  2  1  2  1  (alternando da direita)
    // Produto:  0  0  0  0  0  0  0  1
    //
    // Se produto >= 10 → soma os dígitos: 15 → 1+5 = 6
    // Se produto < 10  → usa o produto: 7 → 7
    //
    // Soma total dos resultados
    // Resto = soma % 10
    // Dígito = (10 - resto) % 10
    // → Se resto = 0 → dígito = 0
    // → Se resto = 5 → dígito = 5
    private String calcularDigito(String numeroConta) {
        int soma = 0;
        int peso = 2; // começa com peso 2 da direita para esquerda

        // Percorre da direita para a esquerda
        for (int i = numeroConta.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(numeroConta.charAt(i));
            int produto = digito * peso;

            // Se produto >= 10 → soma os dois dígitos
            // Ex: 14 → 1 + 4 = 5
            soma += produto >= 10
                    ? (produto / 10) + (produto % 10)
                    : produto;

            // Alterna o peso entre 2 e 1
            peso = (peso == 2) ? 1 : 2;
        }

        int resto = soma % 10;
        int digitoVerificador = (10 - resto) % 10;

        return String.valueOf(digitoVerificador);
    }
}