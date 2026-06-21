package com.tribunalbank.cliente.exception;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

// ═══════════════════════════════════════════════════════════
// GLOBAL EXCEPTION HANDLER — Tratamento centralizado de erros
// ═══════════════════════════════════════════════════════════
//
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody
// Intercepta exceções de TODOS os controllers automaticamente
// Retorna JSON padronizado em vez de página de erro HTML
//
// Por que centralizar?
// → DRY — sem try/catch repetido em cada controller
// → Respostas de erro consistentes em toda a API
// → Fácil de adicionar novos tipos de erro
// ═══════════════════════════════════════════════════════════
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── DTO de resposta de erro ──────────────────────────
    // record interno — só usado aqui, não precisa de arquivo separado
    // Formato padronizado de TODOS os erros da API:
    // { "mensagem": "...", "timestamp": "...", "status": 404 }
    record ErroResponse(String mensagem, LocalDateTime timestamp, int status) {
        ErroResponse(String mensagem, int status) {
            this(mensagem, LocalDateTime.now(), status);
        }
    }

    // ── 404 Not Found ────────────────────────────────────
    // Captura ClienteNotFoundException E EnderecoNotFoundException
    // porque ambas herdam de BusinessException
    // Um único handler para todas as exceções de "não encontrado"
    @ExceptionHandler({
            ClienteNotFoundException.class,
            EnderecoNotFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroResponse handleNotFound(BusinessException ex) {
        return new ErroResponse(ex.getMessage(), 404);
    }

    // ── 409 Conflict ─────────────────────────────────────
    // Captura todas as exceções de duplicidade:
    // CPF já cadastrado, email já cadastrado, tipo de endereço duplicado
    @ExceptionHandler({
            CpfJaCadastradoException.class,
            EmailJaCadastradoException.class,
            TipoEnderecoJaExisteException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroResponse handleConflict(BusinessException ex) {
        return new ErroResponse(ex.getMessage(), 409);
    }

    // ── 400 Bad Request — validação de campos ────────────
    // Lançada pelo Spring quando @Valid falha no controller
    // Coleta TODOS os erros de validação e junta em uma mensagem
    // Ex: "Nome é obrigatório, CPF deve ter 11 dígitos"
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResponse handleValidacao(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                // getDefaultMessage() → retorna a mensagem da anotação
                // ex: @NotBlank(message = "Nome é obrigatório") → "Nome é obrigatório"
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return new ErroResponse(mensagem, 400);
    }

    // ── 500 Internal Server Error ─────────────────────────
    // Captura qualquer exceção não tratada pelos handlers acima
    // NUNCA expõe detalhes internos ao cliente — apenas mensagem genérica
    // O stack trace vai para o log do servidor (não para o cliente)
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErroResponse handleGenerico(Exception ex) {
        return new ErroResponse("Erro interno do servidor", 500);
    }
}