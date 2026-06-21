package com.tribunalbank.cliente.exception;

// Lançada quando o cliente tenta adicionar um endereço
// de um tipo que já existe (ex: segundo RESIDENCIAL)
// O GlobalExceptionHandler mapeia para HTTP 409 Conflict
public class TipoEnderecoJaExisteException extends BusinessException {

    public TipoEnderecoJaExisteException(String tipo) {
        super("Cliente já possui um endereço do tipo: " + tipo);
    }
}