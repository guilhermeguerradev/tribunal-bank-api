package com.tribunalbank.conta.controller;

import com.tribunalbank.conta.client.ClienteResponse;
import com.tribunalbank.conta.dto.ContaRequest;
import com.tribunalbank.conta.dto.ContaResponse;
import com.tribunalbank.conta.dto.LimiteRequest;
import com.tribunalbank.conta.dto.SaldoResponse;
import com.tribunalbank.conta.service.ContaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// ═══════════════════════════════════════════════════════════
// CONTA CONTROLLER — Endpoints REST do conta-service
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA:
// → Recebe a requisição HTTP
// → Extrai dados do JWT (@AuthenticationPrincipal)
// → Valida o DTO (@Valid)
// → Delega para o service
// → Retorna a resposta HTTP
//
// NUNCA coloca lógica de negócio aqui.
// ═══════════════════════════════════════════════════════════
@Slf4j
@RestController
@RequestMapping("/contas")
@RequiredArgsConstructor
@Tag(name = "Contas", description = "Gerenciamento de contas bancárias")
public class ContaController {

    private final ContaService contaService;

    // ── POST /contas ─────────────────────────────────────
    //
    // Abre uma nova conta para o usuário autenticado.
    // usuarioId extraído do JWT — nunca do body.
    // Garante que usuário só abre conta para si mesmo.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abrir nova conta bancária",
            description = "Abre uma conta para o usuário autenticado")
    public ContaResponse abrir(
            @Valid @RequestBody ContaRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String usuarioId = jwt.getSubject();
        log.debug("Abrindo conta. Tipo: {} | Usuário: {}",
                request.tipo(), usuarioId);

        return contaService.abrir(request, usuarioId);
    }

    // ── GET /contas/{id} ─────────────────────────────────
    //
    // Busca conta pelo ID interno (UUID).
    // Dono da conta ou ADMIN podem acessar.
    @GetMapping("/{id}")
    @Operation(summary = "Buscar conta por ID")
    @PreAuthorize("hasRole('ROLE_ADMIN') or " +
            "@contaService.pertenceAoCliente(#id, authentication.name)")
    public ContaResponse buscarPorId(@PathVariable String id) {
        log.debug("Buscando conta: {}", id);
        return contaService.buscarPorId(id);
    }

    // ── GET /contas ──────────────────────────────────────
    //
    // Lista contas com suporte a filtro por clienteId para ADMIN.
    //
    // Sem clienteId → lista do usuário autenticado
    // Com clienteId → ADMIN lista contas de outro cliente
    //
    // Exemplos:
    // GET /contas                     → minhas contas
    // GET /contas?clienteId=uuid-xyz  → contas de outro (ADMIN)
    @GetMapping
    @Operation(summary = "Listar contas",
            description = "Sem parâmetro: lista as próprias contas. " +
                    "Com clienteId (ADMIN): lista contas de outro cliente.")
    public List<ContaResponse> listar(
            @RequestParam(required = false) String clienteId,
            @AuthenticationPrincipal Jwt jwt) {

        // Se veio clienteId → apenas ADMIN pode filtrar por outro cliente
        if (clienteId != null) {
            log.debug("ADMIN listando contas do cliente: {}", clienteId);

            // Verifica manualmente se tem role ADMIN
            // @PreAuthorize não funciona bem com lógica condicional
            // Delegamos a verificação para o service
            return contaService.listarPorClienteComoAdmin(
                    clienteId, jwt.getSubject());
        }

        // Sem clienteId → lista as próprias contas
        String usuarioId = jwt.getSubject();
        log.debug("Listando contas do usuário: {}", usuarioId);

        return contaService.listarPorCliente(usuarioId);
    }

    // ── GET /contas/{id}/saldo ───────────────────────────
    //
    // Endpoint dedicado para saldo — mais enxuto que buscarPorId.
    // Bancos têm endpoint dedicado para saldo por rastreabilidade.
    @GetMapping("/{id}/saldo")
    @Operation(summary = "Consultar saldo da conta")
    @PreAuthorize("hasRole('ROLE_ADMIN') or " +
            "@contaService.pertenceAoCliente(#id, authentication.name)")
    public SaldoResponse consultarSaldo(@PathVariable String id) {
        log.debug("Consultando saldo da conta: {}", id);
        return contaService.consultarSaldo(id);
    }

    // ── GET /contas/numero/{agencia}/{numeroConta} ───────
    //
    // Busca conta pelo número bancário (agência + conta).
    // O cliente não conhece o UUID interno — conhece o número.
    // Usado pelo Transação Service para transferências:
    // "Transferir para ag. 0001 conta 00000001"
    //
    // Restrito a ADMIN e serviços internos.
    // Em produção adicionaria autenticação de serviço (service token).
    @GetMapping("/numero/{agencia}/{numeroConta}")
    @Operation(summary = "Buscar conta por número bancário",
            description = "Usado internamente pelo Transação Service")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ContaResponse buscarPorNumero(
            @PathVariable String agencia,
            @PathVariable String numeroConta) {

        log.debug("Buscando conta por número: {}-{}", agencia, numeroConta);
        return contaService.buscarPorNumero(agencia, numeroConta);
    }

    // ── PATCH /contas/{id}/limite ────────────────────────
    //
    // Atualiza o limite do cheque especial.
    // Restrito a ADMIN — cliente não define seu próprio limite.
    //
    // Por que PATCH e não PUT?
    // PUT → substitui o recurso inteiro
    // PATCH → atualiza parcialmente (só o limite)
    //
    // Por que valor no body e não query param?
    // Query param aparece em logs de servidor e proxies.
    // Valor monetário NUNCA deve aparecer em URLs.
    // Body é mais seguro para dados financeiros.
    @PatchMapping("/{id}/limite")
    @Operation(summary = "Atualizar limite do cheque especial",
            description = "Restrito a administradores")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ContaResponse atualizarLimite(
            @PathVariable String id,
            @Valid @RequestBody LimiteRequest request) {

        log.info("Admin atualizando limite. Conta: {} → R$ {}",
                id, request.valor());
        return contaService.atualizarLimite(id, request.valor());
    }

    // ── DELETE /contas/{id} ──────────────────────────────
    //
    // Encerra a conta (soft delete — ativa = false).
    // Restrito a ADMIN — cliente solicita ao banco.
    // Conta com saldo negativo não pode ser encerrada.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerrar conta bancária",
            description = "Soft delete — restrito a administradores")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void encerrar(@PathVariable String id) {
        log.info("Admin encerrando conta: {}", id);
        contaService.encerrar(id);
    }


}