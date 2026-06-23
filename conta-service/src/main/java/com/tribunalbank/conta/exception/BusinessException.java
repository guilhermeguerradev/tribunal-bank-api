package com.tribunalbank.conta.exception;

// ═══════════════════════════════════════════════════════════
// BUSINESS EXCEPTION — Exceção base para regras de negócio
// ═══════════════════════════════════════════════════════════
//
// abstract → nunca lançamos BusinessException diretamente
// Sempre lançamos uma subclasse específica
// Permite capturar qualquer exceção de negócio com um handler:
// @ExceptionHandler(BusinessException.class)
//
// RuntimeException (unchecked) → não exige try/catch
// O Spring captura automaticamente via @ControllerAdvice
public abstract class BusinessException extends RuntimeException {
    public BusinessException(String mensagem) {
        super(mensagem);
    }
}