package com.tribunalbank.auth.exception;

public class UsuarioNotFoundException extends BusinessException {

    public UsuarioNotFoundException(String mensagem) {
        super(mensagem);
    }
}