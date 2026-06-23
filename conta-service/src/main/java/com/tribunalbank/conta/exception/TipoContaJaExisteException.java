package com.tribunalbank.conta.exception;

// Lançada quando o cliente já tem uma conta do tipo solicitado
// Um cliente só pode ter uma conta de cada tipo
// GlobalExceptionHandler mapeia para HTTP 409
public class TipoContaJaExisteException extends BusinessException {
    public TipoContaJaExisteException(String tipo) {
        super("Cliente já possui uma conta do tipo: " + tipo);
    }
}