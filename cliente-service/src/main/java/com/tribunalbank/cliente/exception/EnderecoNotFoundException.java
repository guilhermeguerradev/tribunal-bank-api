package com.tribunalbank.cliente.exception;

// Lançada quando um endereço não é encontrado
// O GlobalExceptionHandler mapeia para HTTP 404 Not Found
public class EnderecoNotFoundException extends BusinessException {

    public EnderecoNotFoundException(String id) {
        super("Endereço não encontrado com id: " + id);
    }
}