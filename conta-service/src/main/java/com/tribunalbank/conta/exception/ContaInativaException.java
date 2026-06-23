package com.tribunalbank.conta.exception;

// Lançada quando se tenta operar em uma conta desativada
// GlobalExceptionHandler mapeia para HTTP 422
public class ContaInativaException extends BusinessException {
    public ContaInativaException(String numeroConta) {
        super("Conta encerrada e não pode ser operada: " + numeroConta);
    }
}