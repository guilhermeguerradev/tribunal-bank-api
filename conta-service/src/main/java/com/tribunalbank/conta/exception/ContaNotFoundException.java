package com.tribunalbank.conta.exception;

// Lançada quando uma conta não é encontrada
// GlobalExceptionHandler mapeia para HTTP 404
public class ContaNotFoundException extends BusinessException {
    public ContaNotFoundException(String id) {
        super("Conta não encontrada com id: " + id);
    }
}