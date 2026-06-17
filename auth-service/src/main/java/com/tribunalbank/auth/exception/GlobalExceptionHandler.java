package com.tribunalbank.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// ═══════════════════════════════════════════════════════════════════
// GLOBAL EXCEPTION HANDLER — Centralizador de erros do Auth Service
// ═══════════════════════════════════════════════════════════════════
//
// PROBLEMA QUE ELE RESOLVE:
// Sem esse handler, cada controller precisaria tratar seus próprios erros:
//
//   try {
//       authService.cadastrar(dto);
//   } catch (EmailJaCadastradoException e) {
//       return ResponseEntity.status(422).body(...);
//   } catch (Exception e) {
//       return ResponseEntity.status(500).body(...);
//   }
//
// Com esse handler, o controller simplesmente deixa a exceção subir:
//   authService.cadastrar(dto); // se der erro, o handler cuida
//
// FLUXO COMPLETO:
//
//   Requisição HTTP chega
//         ↓
//   Controller recebe e chama o Service
//         ↓
//   Service executa a regra de negócio
//         ↓
//   [algo deu errado] → Service lança uma Exception
//         ↓
//   Spring intercepta a Exception automaticamente
//         ↓
//   Spring procura o @ExceptionHandler correto aqui
//         ↓
//   Handler monta o JSON de erro e retorna para o cliente
//
// ORDEM DE PRIORIDADE DOS HANDLERS:
// O Spring sempre usa o handler MAIS ESPECÍFICO primeiro.
// Se não encontrar específico, vai para o mais genérico.
//
//   TokenInvalidoException    → handler específico → 401
//   UsuarioNotFoundException  → handler específico → 404
//   EmailJaCadastradoException → não tem handler próprio
//                              → é filha de BusinessException
//                              → handler de BusinessException captura → 422
//   MethodArgumentNotValid    → handler específico → 400
//   NullPointerException      → não tem handler próprio
//                              → handler genérico de Exception captura → 500
//
// @RestControllerAdvice → combina @ControllerAdvice + @ResponseBody
//   @ControllerAdvice  = intercepta exceções de qualquer Controller
//   @ResponseBody      = garante que a resposta seja JSON automaticamente
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ═══════════════════════════════════════════════════════════════
    // MÉTODO AUXILIAR — Monta o corpo padrão de todos os erros
    // ═══════════════════════════════════════════════════════════════
    //
    // Garante que TODOS os erros retornem sempre o mesmo formato JSON:
    // {
    //   "timestamp": "2026-06-17T14:30:00",
    //   "status": 422,
    //   "mensagem": "Email já cadastrado: joao@email.com"
    // }
    //
    // Sem esse método cada handler teria que montar o Map manualmente
    // — código repetido e inconsistente. Aqui centralizamos em um lugar só.
    private Map<String, Object> erro(String mensagem, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now()); // quando o erro aconteceu
        body.put("status", status);                 // código HTTP do erro
        body.put("mensagem", mensagem);             // descrição legível do erro
        return body;
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLER 1 — Regras de negócio genéricas → 422
    // ═══════════════════════════════════════════════════════════════
    //
    // 422 Unprocessable Entity = "entendi o que você mandou,
    //                             mas não posso processar por regra de negócio"
    //
    // Captura BusinessException E todas as suas filhas que não têm
    // handler próprio. Exemplo: EmailJaCadastradoException não tem
    // handler específico → cai aqui automaticamente.
    //
    // Fluxo de exemplo:
    //   AuthService lança EmailJaCadastradoException("joao@email.com")
    //         ↓
    //   Spring procura handler para EmailJaCadastradoException → não tem
    //         ↓
    //   EmailJaCadastradoException é filha de BusinessException → captura aqui
    //         ↓
    //   Cliente recebe: { "status": 422, "mensagem": "Email já cadastrado: joao@email.com" }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY) // 422
                .body(erro(ex.getMessage(), 422));
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLER 2 — Token inválido, expirado ou revogado → 401
    // ═══════════════════════════════════════════════════════════════
    //
    // 401 Unauthorized = "você não está autenticado ou sua sessão expirou"
    //
    // Apesar de TokenInvalidoException ser filha de BusinessException,
    // ela tem handler PRÓPRIO porque o código HTTP é diferente (401 vs 422)
    // O Spring sempre usa o handler mais específico — esse é chamado
    // antes do handler de BusinessException quando o erro for TokenInvalidoException
    //
    // Fluxo de exemplo:
    //   RefreshTokenService lança TokenInvalidoException("Token já foi revogado")
    //         ↓
    //   Spring procura handler para TokenInvalidoException → encontra esse aqui
    //         ↓
    //   Cliente recebe: { "status": 401, "mensagem": "Token já foi revogado" }
    //         ↓
    //   App redireciona o usuário para a tela de login
    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleTokenInvalido(TokenInvalidoException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED) // 401
                .body(erro(ex.getMessage(), 401));
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLER 3 — Usuário não encontrado → 404
    // ═══════════════════════════════════════════════════════════════
    //
    // 404 Not Found = "o recurso que você pediu não existe"
    //
    // Também tem handler próprio pelo mesmo motivo do token:
    // o código HTTP é diferente (404 vs 422)
    //
    // Fluxo de exemplo:
    //   AuthService lança UsuarioNotFoundException("Usuário não encontrado")
    //         ↓
    //   Spring procura handler para UsuarioNotFoundException → encontra esse aqui
    //         ↓
    //   Cliente recebe: { "status": 404, "mensagem": "Usuário não encontrado" }
    @ExceptionHandler(UsuarioNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUsuarioNotFound(UsuarioNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND) // 404
                .body(erro(ex.getMessage(), 404));
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLER 4 — Erros de validação dos DTOs → 400
    // ═══════════════════════════════════════════════════════════════
    //
    // 400 Bad Request = "o que você mandou está malformado ou incompleto"
    //
    // Esse handler é especial — não é lançado pelo nosso código.
    // O Spring lança MethodArgumentNotValidException automaticamente
    // quando um DTO anotado com @Valid falha na validação.
    //
    // Exemplo de DTO:
    //   public record RegisterDTO(
    //       @NotBlank(message = "email obrigatório") String email,
    //       @Size(min = 6, message = "mínimo 6 caracteres") String senha
    //   ) {}
    //
    // Fluxo de exemplo:
    //   Cliente manda POST /auth/register com email vazio: { "email": "", "senha": "123" }
    //         ↓
    //   @Valid no controller dispara antes de chegar no Service
    //         ↓
    //   Spring lança MethodArgumentNotValidException automaticamente
    //         ↓
    //   Handler percorre todos os campos com erro e monta a lista
    //         ↓
    //   Cliente recebe:
    //   {
    //     "timestamp": "2026-06-17T14:30:00",
    //     "status": 400,
    //     "mensagem": "Erro de validação",
    //     "campos": {
    //       "email": "email obrigatório",
    //       "senha": "mínimo 6 caracteres"
    //     }
    //   }
    //
    // getBindingResult() → resultado da validação com todos os campos que falharam
    // getFieldErrors()   → lista de campos inválidos com a mensagem de cada um
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacao(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new HashMap<>();

        // Percorre cada campo que falhou na validação
        // e adiciona no map: nome do campo → mensagem de erro
        for (FieldError field : ex.getBindingResult().getFieldErrors()) {
            campos.put(field.getField(), field.getDefaultMessage());
        }

        // Esse handler monta o body manualmente porque tem o campo extra "campos"
        // que o método erro() padrão não contempla
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", 400);
        body.put("mensagem", "Erro de validação");
        body.put("campos", campos); // lista detalhada de quais campos falharam
        return ResponseEntity.badRequest().body(body);
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLER 5 — Último recurso, qualquer erro inesperado → 500
    // ═══════════════════════════════════════════════════════════════
    //
    // 500 Internal Server Error = "algo quebrou no servidor que não foi previsto"
    //
    // Captura QUALQUER exceção que não foi capturada pelos handlers anteriores:
    // NullPointerException, SQLException, banco fora do ar, etc.
    //
    // REGRA DE OURO DE SEGURANÇA:
    // Nunca expõe detalhes do erro interno para o cliente!
    // O stack trace e a mensagem real vão apenas para o log do servidor.
    // Se expuser, um atacante pode usar essas informações para explorar o sistema.
    //
    // Fluxo de exemplo:
    //   Banco de dados cai no meio de uma operação
    //         ↓
    //   JPA lança DataAccessException (erro técnico, não de negócio)
    //         ↓
    //   Nenhum handler específico captura
    //         ↓
    //   Esse handler captura como Exception genérica
    //         ↓
    //   Cliente recebe: { "status": 500, "mensagem": "Erro interno do servidor" }
    //   Servidor loga: DataAccessException: Connection refused to host: localhost:5432
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenerico(Exception ex) {
        // TODO: adicionar log aqui quando implementar o sistema de logs
        // log.error("Erro inesperado: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR) // 500
                .body(erro("Erro interno do servidor", 500));
    }
}