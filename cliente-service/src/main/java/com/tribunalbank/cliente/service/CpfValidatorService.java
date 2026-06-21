package com.tribunalbank.cliente.service;

import org.springframework.stereotype.Service;

// ═══════════════════════════════════════════════════════════
// CPF VALIDATOR SERVICE — Validação do algoritmo do CPF
// ═══════════════════════════════════════════════════════════
//
// Por que separar em um service próprio?
// Single Responsibility Principle — validação de CPF é uma
// responsabilidade independente do ClienteService.
// Pode ser reutilizado em outros lugares sem duplicar código.
//
// COMO FUNCIONA O ALGORITMO DO CPF:
// CPF tem 11 dígitos: 9 dígitos base + 2 dígitos verificadores
// Ex: 123.456.789-09 → base: 123456789 → verificadores: 0 e 9
//
// CÁLCULO DO 1º DÍGITO VERIFICADOR:
// Multiplica os 9 primeiros dígitos por 10, 9, 8, 7, 6, 5, 4, 3, 2
// Soma os resultados
// Resto = soma % 11
// Se resto < 2 → dígito = 0
// Se resto >= 2 → dígito = 11 - resto
//
// CÁLCULO DO 2º DÍGITO VERIFICADOR:
// Multiplica os 10 primeiros dígitos por 11, 10, 9, 8, 7, 6, 5, 4, 3, 2
// Mesma lógica do resto
// ═══════════════════════════════════════════════════════════
@Service
public class CpfValidatorService {

    public boolean isValido(String cpf) {
        // Remove qualquer formatação que vier com pontos ou hífen
        // "123.456.789-09" → "12345678909"
        if (cpf == null) return false;

        String cpfLimpo = cpf.replaceAll("[^0-9]", "");

        // CPF deve ter exatamente 11 dígitos
        if (cpfLimpo.length() != 11) return false;

        // Rejeita CPFs com todos os dígitos iguais
        // "00000000000", "11111111111" etc são inválidos matematicamente
        // mas passariam no algoritmo — precisam ser rejeitados explicitamente
        if (cpfLimpo.matches("(\\d)\\1{10}")) return false;

        // Calcula e valida os dois dígitos verificadores
        return calcularDigito(cpfLimpo, 9) == Character.getNumericValue(cpfLimpo.charAt(9))
                && calcularDigito(cpfLimpo, 10) == Character.getNumericValue(cpfLimpo.charAt(10));
    }

    // Calcula um dígito verificador do CPF
    // tamanho = 9 para o 1º dígito, 10 para o 2º dígito
    private int calcularDigito(String cpf, int tamanho) {
        int soma = 0;

        // Multiplica cada dígito pelo peso decrescente
        // 1º dígito: pesos 10, 9, 8, 7, 6, 5, 4, 3, 2
        // 2º dígito: pesos 11, 10, 9, 8, 7, 6, 5, 4, 3, 2
        for (int i = 0; i < tamanho; i++) {
            soma += Character.getNumericValue(cpf.charAt(i)) * (tamanho + 1 - i);
        }

        int resto = soma % 11;

        // Regra do algoritmo Módulo 11:
        // Se resto for 0 ou 1 → dígito verificador é 0
        // Caso contrário → dígito verificador é 11 - resto
        return resto < 2 ? 0 : 11 - resto;
    }
}