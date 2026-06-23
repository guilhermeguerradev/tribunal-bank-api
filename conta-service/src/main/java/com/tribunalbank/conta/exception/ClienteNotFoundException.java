package com.tribunalbank.conta.exception;

// Lançada quando o FeignClient não encontra o cliente
// no Cliente Service — cliente inexistente ou inativo
// GlobalExceptionHandler mapeia para HTTP 404
public class ClienteNotFoundException extends BusinessException {
    public ClienteNotFoundException(String clienteId) {
        super("Cliente não encontrado ou inativo: " + clienteId);
    }
}