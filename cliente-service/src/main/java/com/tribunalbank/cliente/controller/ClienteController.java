package com.tribunalbank.cliente.controller;

import com.tribunalbank.cliente.dto.*;
import com.tribunalbank.cliente.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

// ═══════════════════════════════════════════════════════════
// CLIENTE CONTROLLER — Endpoints REST do cliente-service
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA:
// O controller APENAS:
// → Recebe a requisição HTTP
// → Extrai dados do JWT (@AuthenticationPrincipal)
// → Valida o DTO (@Valid)
// → Delega para o service
// → Retorna a resposta HTTP
//
// NUNCA coloca lógica de negócio aqui.
// Se o controller tem if/else de negócio → está errado.
//
// @Tag — agrupa os endpoints no Swagger UI
// Todos os endpoints desse controller aparecem juntos
// sob o grupo "Clientes" na documentação
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Gerenciamento de clientes e endereços")
public class ClienteController {

    private final ClienteService clienteService;

    // ── POST /clientes ───────────────────────────────────
    //
    // Cadastra um novo cliente vinculado ao usuário autenticado.
    //
    // @AuthenticationPrincipal Jwt jwt:
    // Injeta o JWT do usuário autenticado diretamente no parâmetro.
    // jwt.getSubject() → retorna o claim "sub" = email do usuário
    // jwt.getClaim("usuarioId") → retorna o ID do usuário no auth-service
    //
    // Por que não pedir o usuarioId no body?
    // Segurança — se o cliente mandasse o usuarioId no body,
    // poderia criar perfil para outro usuário.
    // Extrair do JWT garante que é sempre o usuário autenticado.
    //
    // @ResponseStatus(CREATED) → retorna HTTP 201 em vez de 200
    // Padrão REST: POST que cria recurso retorna 201 Created
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar novo cliente",
            description = "Cria o perfil de cliente para o usuário autenticado")
    public ClienteResponse cadastrar(
            @Valid @RequestBody ClienteRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        // Extrai o ID do usuário do token JWT
        // Esse é o usuarioId armazenado na entidade Cliente
        String usuarioId = jwt.getSubject();

        return clienteService.cadastrar(request, usuarioId);
    }

    // ── GET /clientes/{id} ───────────────────────────────
    //
    // @PreAuthorize — verifica permissão ANTES do método executar
    //
    // "hasRole('ADMIN') or @clienteService.pertenceAoUsuario(#id, authentication.name)"
    // → ADMIN pode ver qualquer cliente
    // → Usuário comum só pode ver o próprio perfil
    //
    // Por que @PreAuthorize e não verificar no service?
    // Separação de responsabilidades:
    // → Controller/Segurança: quem pode acessar
    // → Service: o que fazer quando acessar
    // @PreAuthorize rejeita antes de entrar no service — mais eficiente
    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID")
    @PreAuthorize("hasRole('ROLE_ADMIN') or " +
            "@clienteService.pertenceAoUsuario(#id, authentication.name)")
    public ClienteResponse buscarPorId(@PathVariable String id) {
        return clienteService.buscarPorId(id);
    }

    // ── GET /clientes/cpf/{cpf} ──────────────────────────
    //
    // Busca por CPF — restrito a administradores
    // Um usuário comum não precisa buscar outro pelo CPF
    // hasRole('ROLE_ADMIN') → só ADMIN pode acessar
    @GetMapping("/cpf/{cpf}")
    @Operation(summary = "Buscar cliente por CPF")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ClienteResponse buscarPorCpf(@PathVariable String cpf) {
        return clienteService.buscarPorCpf(cpf);
    }

    // ── GET /clientes ────────────────────────────────────
    //
    // Lista todos os clientes ativos com paginação.
    // Restrito a administradores — expõe dados de todos os clientes.
    //
    // @PageableDefault — define valores padrão da paginação
    // size = 10    → 10 registros por página
    // sort = "nome" → ordenado por nome por padrão
    //
    // Pageable é montado automaticamente pelo Spring a partir dos
    // query params da URL:
    // GET /clientes?page=0&size=20&sort=nome,asc
    // GET /clientes?nome=joão&page=1&size=5
    //
    // Page<ClienteResponse> retorna:
    // {
    //   "content": [...],        ← lista de clientes
    //   "totalElements": 150,    ← total de registros
    //   "totalPages": 15,        ← total de páginas
    //   "number": 0,             ← página atual
    //   "size": 10               ← tamanho da página
    // }
    @GetMapping
    @Operation(summary = "Listar clientes com paginação",
            description = "Parâmetros: ?nome=&page=0&size=10&sort=nome,asc")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Page<ClienteResponse> listar(
            @RequestParam(required = false) String nome,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {

        return clienteService.listar(nome, pageable);
    }

    // ── PUT /clientes/{id} ───────────────────────────────
    //
    // Atualiza dados do cliente.
    // ADMIN pode atualizar qualquer cliente.
    // Usuário comum só atualiza o próprio perfil.
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar dados do cliente")
    @PreAuthorize("hasRole('ROLE_ADMIN') or " +
            "@clienteService.pertenceAoUsuario(#id, authentication.name)")
    public ClienteResponse atualizar(
            @PathVariable String id,
            @Valid @RequestBody ClienteRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String usuarioId = jwt.getSubject();
        return clienteService.atualizar(id, request, usuarioId);
    }

    // ── DELETE /clientes/{id} ────────────────────────────
    //
    // Desativa o cliente (soft delete) — restrito a ADMIN.
    // @ResponseStatus(NO_CONTENT) → HTTP 204 sem body na resposta
    // Padrão REST: DELETE retorna 204 No Content
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desativar cliente (soft delete)")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void desativar(@PathVariable String id) {
        clienteService.desativar(id);
    }

    // ════════════════════════════════════════════════════
    // ENDPOINTS DE ENDEREÇO
    // ════════════════════════════════════════════════════

    // ── POST /clientes/{id}/enderecos ────────────────────
    @PostMapping("/{id}/enderecos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adicionar endereço ao cliente")
    @PreAuthorize("hasRole('ROLE_ADMIN') or " +
            "@clienteService.pertenceAoUsuario(#id, authentication.name)")
    public EnderecoResponse adicionarEndereco(
            @PathVariable String id,
            @Valid @RequestBody EnderecoRequest request) {

        return clienteService.adicionarEndereco(id, request);
    }

    // ── PUT /clientes/{clienteId}/enderecos/{enderecoId} ─
    @PutMapping("/{clienteId}/enderecos/{enderecoId}")
    @Operation(summary = "Atualizar endereço do cliente")
    @PreAuthorize("hasRole('ROLE_ADMIN') or " +
            "@clienteService.pertenceAoUsuario(#clienteId, authentication.name)")
    public EnderecoResponse atualizarEndereco(
            @PathVariable String clienteId,
            @PathVariable String enderecoId,
            @Valid @RequestBody EnderecoRequest request) {

        return clienteService.atualizarEndereco(clienteId, enderecoId, request);
    }

    // ── DELETE /clientes/{clienteId}/enderecos/{enderecoId}
    @DeleteMapping("/{clienteId}/enderecos/{enderecoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover endereço do cliente")
    @PreAuthorize("hasRole('ROLE_ADMIN') or " +
            "@clienteService.pertenceAoUsuario(#clienteId, authentication.name)")
    public void removerEndereco(
            @PathVariable String clienteId,
            @PathVariable String enderecoId) {

        clienteService.removerEndereco(clienteId, enderecoId);
    }
}