package com.tribunalbank.auth.exception;

// Lançada quando o refresh token não existe,
// já foi revogado ou está expirado
public class TokenInvalidoException extends BusinessException {

    public TokenInvalidoException(String mensagem) {
        super(mensagem);
    }
}