package com.tribunalbank.conta.exception;

// ═══════════════════════════════════════════════════════════
// CLIENTE SERVICE EXCEPTION — Falha na comunicação via Feign
// ═══════════════════════════════════════════════════════════
//
// Lançada quando o FeignClient falha ao chamar o Cliente Service
// Diferente de ClienteNotFoundException:
// → ClienteNotFoundException → cliente não existe (404 do Cliente Service)
// → ClienteServiceException  → Cliente Service está fora do ar ou
//                               retornou erro inesperado (5xx)
//
// GlobalExceptionHandler mapeia para HTTP 503 Service Unavailable
// Indica que o serviço dependente está indisponível
public class ClienteServiceException extends RuntimeException {

    // Herda de RuntimeException (não de BusinessException)
    // porque é um erro de infraestrutura, não de negócio
    // A distinção importa para o GlobalExceptionHandler
    // poder retornar status codes diferentes
    public ClienteServiceException(String mensagem) {
        super(mensagem);
    }

    public ClienteServiceException(String mensagem, Throwable causa) {
        super(mensagem, causa);
        // causa → a exceção original do FeignClient
        // Preservada para logging — aparece no stack trace
    }
}