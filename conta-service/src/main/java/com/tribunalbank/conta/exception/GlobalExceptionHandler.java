package com.tribunalbank.conta.exception;

import lombok.extern.slf4j.Slf4j;
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
// NÍVEIS DE LOG:
// log.warn()  → erros esperados (negócio) — não críticos
//               aparecem mas não disparam alertas
// log.error() → erros graves (infraestrutura, inesperados)
//               disparam alertas no monitoramento (Datadog, Grafana)
//
// @Slf4j → Lombok gera automaticamente:
// private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class)
// ═══════════════════════════════════════════════════════════
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // DTO interno de resposta de erro — formato padronizado
    // { "mensagem": "...", "timestamp": "...", "status": 404 }
    record ErroResponse(String mensagem, LocalDateTime timestamp, int status) {
        ErroResponse(String mensagem, int status) {
            this(mensagem, LocalDateTime.now(), status);
        }
    }

    // ── 404 Not Found ────────────────────────────────────
    //
    // log.warn() → erro esperado, não crítico
    // Acontece quando o usuário passa um ID inválido
    // Não precisa de alerta — faz parte do fluxo normal
    @ExceptionHandler({
            ContaNotFoundException.class,
            ClienteNotFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroResponse handleNotFound(BusinessException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        return new ErroResponse(ex.getMessage(), 404);
    }

    // ── 409 Conflict ─────────────────────────────────────
    //
    // log.warn() → tentativa de criar conta duplicada
    // Pode indicar bug no front-end (clique duplo) ou
    // usuário tentando burlar regras — worth monitoring
    @ExceptionHandler(TipoContaJaExisteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroResponse handleConflict(BusinessException ex) {
        log.warn("Conflito de dados: {}", ex.getMessage());
        return new ErroResponse(ex.getMessage(), 409);
    }

    // ── 422 Unprocessable Entity ──────────────────────────
    //
    // log.warn() → regra de negócio violada
    // Saldo insuficiente e conta inativa são situações
    // esperadas no dia a dia — não são erros críticos
    @ExceptionHandler({
            SaldoInsuficienteException.class,
            ContaInativaException.class
    })
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErroResponse handleUnprocessable(BusinessException ex) {
        log.warn("Regra de negócio violada: {}", ex.getMessage());
        return new ErroResponse(ex.getMessage(), 422);
    }

    // ── 503 Service Unavailable ───────────────────────────
    //
    // log.error() → Cliente Service está fora do ar
    // Erro CRÍTICO — precisa de atenção imediata
    //
    // ex como terceiro parâmetro → loga o stack trace completo
    // Essencial para entender a causa raiz do problema
    //
    // A mensagem ao cliente é genérica — não expõe detalhes
    // técnicos internos (IP do serviço, porta, stack trace)
    @ExceptionHandler(ClienteServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErroResponse handleClienteServiceIndisponivel(
            ClienteServiceException ex) {
        log.error("Cliente Service indisponível: {}", ex.getMessage(), ex);
        return new ErroResponse(
                "Serviço temporariamente indisponível. Tente novamente mais tarde.",
                503
        );
    }

    // ── 400 Bad Request — validação de campos ────────────
    //
    // log.warn() → campos inválidos no request
    // Pode indicar bug no front-end ou integração incorreta
    // Coleta TODOS os erros de validação em uma mensagem só
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResponse handleValidacao(
            MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("Validação falhou: {}", mensagem);
        return new ErroResponse(mensagem, 400);
    }

    // ── 500 Internal Server Error ─────────────────────────
    //
    // log.error() com ex → loga stack trace completo
    // Erro CRÍTICO e inesperado — sempre precisa investigação
    //
    // A mensagem ao cliente é genérica:
    // → Não expõe detalhes internos (nomes de classes, SQL etc)
    // → Protege contra information disclosure
    // → O log interno tem todos os detalhes para o developer
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErroResponse handleGenerico(Exception ex) {
        log.error("Erro inesperado: {}", ex.getMessage(), ex);
        return new ErroResponse("Erro interno do servidor", 500);
    }
}