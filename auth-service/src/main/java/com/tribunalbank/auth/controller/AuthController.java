package com.tribunalbank.auth.controller;

import com.tribunalbank.auth.dto.AuthResponse;
import com.tribunalbank.auth.dto.LoginRequest;
import com.tribunalbank.auth.dto.RefreshRequest;
import com.tribunalbank.auth.dto.RegisterRequest;
import com.tribunalbank.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// ═══════════════════════════════════════════════════════════
// AUTH CONTROLLER — Endpoints de autenticação
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA:
// Receber as requisições HTTP, delegar para o AuthService
// e devolver a resposta correta.
//
// O Controller NÃO tem regra de negócio — só orquestra:
// → Recebe o request
// → Valida com @Valid (Bean Validation)
// → Chama o Service
// → Retorna o ResponseEntity com status HTTP correto
//
// ENDPOINTS:
// POST /auth/register  → cadastro         → 201 Created
// POST /auth/login     → login            → 200 OK
// POST /auth/refresh   → renovar token    → 200 OK
// POST /auth/logout    → logout           → 204 No Content
//
// @Tag → agrupa os endpoints no Swagger UI
//        aparece como seção "Autenticação" na documentação
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(
        name = "Autenticação",
        description = "Endpoints de cadastro, login, renovação de token e logout"
)
public class AuthController {

    private final AuthService authService;

    // ═══════════════════════════════════════════════════════
    // POST /auth/register — Cadastro de novo usuário
    // ═══════════════════════════════════════════════════════
    //
    // @Operation  → descreve o endpoint no Swagger UI
    // @ApiResponses → documenta os possíveis retornos
    // @Valid       → aciona o Bean Validation no RegisterRequest
    //               se falhar lança MethodArgumentNotValidException
    //               que o GlobalExceptionHandler captura → 400
    //
    // Retorna 201 Created com os tokens
    // O usuário cadastrou e já está autenticado
    @Operation(
            summary = "Cadastrar novo usuário",
            description = "Registra um novo usuário e retorna os tokens JWT"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "422", description = "Email já cadastrado")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        // Delega para o AuthService — zero regra de negócio aqui
        AuthResponse response = authService.cadastrar(request);

        // 201 Created → recurso foi criado com sucesso
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ═══════════════════════════════════════════════════════
    // POST /auth/login — Autenticação
    // ═══════════════════════════════════════════════════════
    //
    // Retorna 200 OK com os tokens
    // Se as credenciais forem inválidas o AuthService lança
    // BadCredentialsException → GlobalExceptionHandler → 401
    @Operation(
            summary = "Realizar login",
            description = "Autentica o usuário e retorna access token e refresh token"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Email ou senha incorretos")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }

    // ═══════════════════════════════════════════════════════
    // POST /auth/refresh — Renovação do access token
    // ═══════════════════════════════════════════════════════
    //
    // Retorna 200 OK com novos tokens
    // Se o refresh token for inválido o AuthService lança
    // TokenInvalidoException → GlobalExceptionHandler → 401
    @Operation(
            summary = "Renovar access token",
            description = """
                Usa o refresh token para gerar um novo access token.
                O refresh token antigo é revogado (Refresh Token Rotation).
                """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token renovado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido ou expirado")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request) {

        return ResponseEntity.ok(authService.refresh(request));
    }

    // ═══════════════════════════════════════════════════════
    // POST /auth/logout — Logout
    // ═══════════════════════════════════════════════════════
    //
    // Recebe o refresh token no body e o revoga no banco
    // O access token expira naturalmente em 15 minutos
    //
    // @SecurityRequirement → indica no Swagger que esse endpoint
    //                        precisa de JWT para ser chamado
    //                        aparece o cadeado fechado no Swagger UI
    //
    // Retorna 204 No Content — operação realizada, sem corpo
    // É o status correto para operações que não retornam dados
    @Operation(
            summary = "Realizar logout",
            description = "Revoga o refresh token do dispositivo atual"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logout realizado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshRequest request) {

        authService.logout(request.refreshToken());

        // 204 No Content → operação realizada com sucesso, sem corpo
        return ResponseEntity.noContent().build();
    }
}