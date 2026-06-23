package com.tribunalbank.conta.service;

import com.tribunalbank.conta.client.ClienteClient;
import com.tribunalbank.conta.client.ClienteResponse;
import com.tribunalbank.conta.dto.ContaRequest;
import com.tribunalbank.conta.dto.ContaResponse;
import com.tribunalbank.conta.dto.SaldoResponse;
import com.tribunalbank.conta.entity.Conta;
import com.tribunalbank.conta.entity.TipoConta;
import com.tribunalbank.conta.exception.*;
import com.tribunalbank.conta.repository.ContaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

// ═══════════════════════════════════════════════════════════
// CONTA SERVICE — Lógica de negócio do conta-service
// ═══════════════════════════════════════════════════════════
//
// Orquestra o fluxo completo:
// 1. Valida o cliente via FeignClient (Cliente Service)
// 2. Valida regras de negócio (tipo único por cliente)
// 3. Gera número da conta (NumeroContaService)
// 4. Persiste no banco
// 5. Retorna DTO de resposta
//
// @Slf4j → logger para rastreabilidade em produção
// @RequiredArgsConstructor → injeção via construtor (campos final)
// ═══════════════════════════════════════════════════════════
@Slf4j
@Service
@RequiredArgsConstructor
public class ContaService {

    private final ContaRepository contaRepository;
    private final NumeroContaService numeroContaService;
    private final ClienteClient clienteClient;

    // ── Abrir conta ──────────────────────────────────────
    //
    // @Transactional → se qualquer passo falhar → rollback
    // Garante que a conta só é criada se tudo der certo
    //
    // clienteId vem do JWT — extraído no controller
    // Garante que usuário só abre conta para si mesmo
    @Transactional
    public ContaResponse abrir(ContaRequest request, String clienteId) {
        log.info("Abrindo conta {} para cliente: {}",
                request.tipo(), clienteId);

        // 1. Valida o cliente via FeignClient
        // Se Cliente Service estiver fora → ClienteServiceException → 503
        // Se cliente não existir → ClienteNotFoundException → 404
        ClienteResponse cliente = clienteClient.buscarPorUsuarioId(clienteId);

        // 2. Verifica se o cliente está ativo
        // Soft delete no Cliente Service — cliente pode existir mas inativo
        if (!cliente.ativo()) {
            log.warn("Tentativa de abrir conta para cliente inativo: {}",
                    clienteId);
            throw new ClienteNotFoundException(clienteId);
        }

        // 3. Verifica se cliente já tem conta desse tipo
        // Constraint no banco (uk_cliente_tipo) também garante
        // mas verificamos antes para dar uma mensagem clara
        if (contaRepository.existsByClienteIdAndTipo(
                clienteId, request.tipo())) {
            log.warn("Cliente {} já possui conta do tipo: {}",
                    clienteId, request.tipo());
            throw new TipoContaJaExisteException(request.tipo().name());
        }

        // 4. Gera o número da conta via SEQUENCE do PostgreSQL
        // [0] = agencia, [1] = numeroConta, [2] = digito
        String[] numeroConta = numeroContaService.gerar();

        // 5. Trata o limite — null no request → ZERO
        BigDecimal limite = request.limite() != null
                ? request.limite()
                : BigDecimal.ZERO;

        // 6. Constrói e persiste a entidade
        Conta conta = Conta.builder()
                .clienteId(cliente.id())    // ← ID do cliente (UUID do cliente-service)
                .usuarioId(clienteId)       // ← adiciona essa linha! clienteId = email do JWT
                .numeroAgencia(numeroConta[0])
                .numeroConta(numeroConta[1])
                .digitoVerificador(numeroConta[2])
                .tipo(request.tipo())
                .saldo(BigDecimal.ZERO)
                .limite(limite)
                .ativa(true)
                .build();

        contaRepository.save(conta);

        log.info("Conta aberta com sucesso: {}-{}-{} | Cliente: {}",
                numeroConta[0], numeroConta[1], numeroConta[2], clienteId);

        return ContaResponse.from(conta);
    }

    // ── Buscar conta por ID ──────────────────────────────
    @Transactional(readOnly = true)
    public ContaResponse buscarPorId(String id) {
        log.debug("Buscando conta por id: {}", id);

        Conta conta = contaRepository.findById(id)
                .orElseThrow(() -> new ContaNotFoundException(id));

        return ContaResponse.from(conta);
    }

    // ── Listar contas do cliente autenticado ─────────────
    //
    // Retorna List e não Page — cliente tem no máximo 4 contas
    // (uma de cada tipo) — paginação desnecessária
    @Transactional(readOnly = true)
    public List<ContaResponse> listarPorCliente(String clienteId) {
        log.debug("Listando contas do cliente: {}", clienteId);

        return contaRepository
                .findByClienteIdAndAtivaTrue(clienteId)
                .stream()
                .map(ContaResponse::from)
                .toList();
    }

    // ── Consultar saldo ──────────────────────────────────
    //
    // Endpoint específico para saldo — retorna SaldoResponse
    // com menos dados que ContaResponse (mais enxuto)
    @Transactional(readOnly = true)
    public SaldoResponse consultarSaldo(String id) {
        log.debug("Consultando saldo da conta: {}", id);

        Conta conta = contaRepository.findById(id)
                .orElseThrow(() -> new ContaNotFoundException(id));

        return SaldoResponse.from(conta);
    }

    // ── Atualizar limite ─────────────────────────────────
    //
    // Apenas ADMIN pode alterar o limite de uma conta
    // Verificado via @PreAuthorize no controller
    @Transactional
    public ContaResponse atualizarLimite(String id,
                                         BigDecimal novoLimite) {
        log.info("Atualizando limite da conta: {} → R$ {}",
                id, novoLimite);

        Conta conta = contaRepository.findById(id)
                .orElseThrow(() -> new ContaNotFoundException(id));

        // Verifica se a conta está ativa
        if (!conta.isAtiva()) {
            throw new ContaInativaException(
                    conta.getNumeroAgencia() + "-" +
                            conta.getNumeroConta()   + "-" +
                            conta.getDigitoVerificador()
            );
        }

        conta.setLimite(novoLimite);
        contaRepository.save(conta);

        log.info("Limite atualizado com sucesso. Conta: {}", id);

        return ContaResponse.from(conta);
    }

    // ── Encerrar conta (soft delete) ─────────────────────
    //
    // Apenas ADMIN pode encerrar uma conta
    // Conta com saldo negativo não pode ser encerrada
    @Transactional
    public void encerrar(String id) {
        log.info("Encerrando conta: {}", id);

        Conta conta = contaRepository.findById(id)
                .orElseThrow(() -> new ContaNotFoundException(id));

        // Não pode encerrar conta com saldo negativo
        // Cliente deve quitar o débito primeiro
        if (conta.getSaldo().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Tentativa de encerrar conta com saldo negativo: {}",
                    id);
            throw new SaldoInsuficienteException(
                    "Conta com saldo negativo não pode ser encerrada. " +
                            "Saldo atual: R$ " + conta.getSaldo()
            );
        }

        // Soft delete — conta nunca é deletada fisicamente
        // Regulação bancária exige preservar o histórico
        conta.setAtiva(false);
        contaRepository.save(conta);

        log.info("Conta encerrada com sucesso: {}", id);
    }

    // ── Verificar se conta pertence ao cliente ───────────
    //
    // Usado pelo @PreAuthorize no controller:
    // @PreAuthorize("@contaService.pertenceAoCliente(#id, authentication.name)")
    //
    // authentication.name → subject do JWT → clienteId do usuário
    // Retorna true → usuário pode acessar
    // Retorna false → Spring lança 403 Forbidden
    public boolean pertenceAoCliente(String contaId, String usuarioId) {
        return contaRepository.findById(contaId)
                .map(conta -> conta.getUsuarioId().equals(usuarioId))
                .orElse(false);
    }

    // ── Buscar por número bancário ───────────────────────
    @Transactional(readOnly = true)
    public ContaResponse buscarPorNumero(String agencia,
                                         String numeroConta) {
        log.debug("Buscando conta por número: {}-{}", agencia, numeroConta);

        Conta conta = contaRepository
                .findByNumeroAgenciaAndNumeroConta(agencia, numeroConta)
                .orElseThrow(() -> new ContaNotFoundException(
                        "Conta não encontrada: " + agencia + "-" + numeroConta
                ));

        return ContaResponse.from(conta);
    }

    // ── Listar contas de outro cliente (ADMIN) ───────────
    //
    // Verifica se o solicitante tem role ADMIN no JWT
    // antes de permitir listar contas de outro cliente
    @Transactional(readOnly = true)
    public List<ContaResponse> listarPorClienteComoAdmin(
            String clienteId, String usuarioId) {

        log.debug("Listando contas do cliente {} pelo admin {}",
                clienteId, usuarioId);

        // A verificação de ROLE_ADMIN é feita pelo Spring Security
        // via @PreAuthorize no controller — aqui só executa
        return contaRepository
                .findByClienteIdAndAtivaTrue(clienteId)
                .stream()
                .map(ContaResponse::from)
                .toList();
    }
}