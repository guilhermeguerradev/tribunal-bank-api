package com.tribunalbank.cliente.exception;

// Lançada quando o email já existe no banco
// O GlobalExceptionHandler mapeia para HTTP 409 Conflict
public class EmailJaCadastradoException extends BusinessException {

    public EmailJaCadastradoException(String email) {
        super("Email já cadastrado: " + email);
    }
}