package com.tribunalbank.cliente.exception;

// Lançada quando o CPF já existe no banco
// O GlobalExceptionHandler mapeia para HTTP 409 Conflict
public class CpfJaCadastradoException extends BusinessException {

    public CpfJaCadastradoException(String cpf) {
        super("CPF já cadastrado: " + cpf);
    }
}