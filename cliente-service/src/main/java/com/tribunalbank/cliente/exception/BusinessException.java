package com.tribunalbank.cliente.exception;

// ═══════════════════════════════════════════════════════════
// BUSINESS EXCEPTION — Exceção base para regras de negócio
// ═══════════════════════════════════════════════════════════
//
// Por que extends RuntimeException e não Exception?
// → RuntimeException = unchecked → não exige try/catch obrigatório
// → O Spring captura automaticamente via @ControllerAdvice
// → checked Exception forçaria try/catch em cada service — verboso
//
// Por que classe abstrata?
// → Nunca lançamos BusinessException diretamente
// → Sempre lançamos uma subclasse específica (ClienteNotFoundException etc)
// → Abstrata = não pode ser instanciada — só herdada
// → Permite capturar QUALQUER exceção de negócio com um único handler:
//   @ExceptionHandler(BusinessException.class)
// ═══════════════════════════════════════════════════════════
public abstract class BusinessException extends RuntimeException {

    // Construtor que recebe a mensagem de erro
    // Passa para o RuntimeException que armazena internamente
    // Acessada depois via ex.getMessage() no GlobalExceptionHandler
    public BusinessException(String mensagem) {
        super(mensagem);
    }
}