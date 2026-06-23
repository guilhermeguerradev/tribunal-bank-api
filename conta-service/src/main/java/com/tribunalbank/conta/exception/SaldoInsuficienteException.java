package com.tribunalbank.conta.exception;

// Lançada quando o saldo disponível (saldo + limite)
// é insuficiente para a operação solicitada
// GlobalExceptionHandler mapeia para HTTP 422
//
// Por que 422 Unprocessable Entity e não 400?
// 400 → requisição malformada (campo inválido, formato errado)
// 422 → requisição válida mas não processável por regra de negócio
// Saldo insuficiente é uma regra de negócio — 422 é mais semântico
public class SaldoInsuficienteException extends BusinessException {
    public SaldoInsuficienteException() {
        super("Saldo insuficiente para realizar a operação");
    }

    public SaldoInsuficienteException(String mensagem) {
        super(mensagem);
    }
}