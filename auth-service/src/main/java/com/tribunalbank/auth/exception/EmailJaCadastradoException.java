package com.tribunalbank.auth.exception;

public class EmailJaCadastradoException extends BusinessException {

    public EmailJaCadastradoException(String email) {
        super("Email já cadastrado: " + email);
    }
}