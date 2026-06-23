package com.tribunalbank.conta.repository;

import com.tribunalbank.conta.entity.Conta;
import com.tribunalbank.conta.entity.TipoConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// ═══════════════════════════════════════════════════════════
// CONTA REPOSITORY — Acesso ao banco para a entidade Conta
// ═══════════════════════════════════════════════════════════
//
// JpaRepository<Conta, String>:
// → Conta  → entidade que esse repository gerencia
// → String → tipo do ID (UUID armazenado como String)
//
// O Spring Data gera a implementação automaticamente em runtime.
// Você não escreve SQL — o Spring interpreta o nome do método
// e gera o SQL equivalente (Derived Queries).
//
// Métodos herdados do JpaRepository (alguns exemplos):
// → save(entity)     → INSERT ou UPDATE
// → findById(id)     → SELECT WHERE id = ?
// → findAll()        → SELECT * (cuidado em tabelas grandes)
// → deleteById(id)   → DELETE WHERE id = ? (hard delete)
// → existsById(id)   → SELECT COUNT WHERE id = ?
// ═══════════════════════════════════════════════════════════
public interface ContaRepository extends JpaRepository<Conta, String> {

    // ── Busca todas as contas de um cliente ──────────────
    //
    // Usado para listar as contas do cliente logado
    // Gera: SELECT * FROM contas WHERE cliente_id = ?
    //
    // Retorna List e não Page porque um cliente tem no máximo
    // 4 contas (uma de cada tipo) — paginação desnecessária
    List<Conta> findByClienteId(String clienteId);

    // ── Busca conta por cliente e tipo ───────────────────
    //
    // Verifica se o cliente já tem uma conta do tipo solicitado
    // antes de criar uma nova.
    //
    // Gera: SELECT * FROM contas
    //       WHERE cliente_id = ? AND tipo = ? LIMIT 1
    Optional<Conta> findByClienteIdAndTipo(String clienteId,
                                           TipoConta tipo);

    // ── Verifica existência por cliente e tipo ───────────
    //
    // Mais eficiente que findByClienteIdAndTipo quando
    // só precisa saber SE existe sem carregar o objeto
    //
    // Gera: SELECT CASE WHEN COUNT(*) > 0
    //       THEN true ELSE false END
    //       FROM contas WHERE cliente_id = ? AND tipo = ?
    boolean existsByClienteIdAndTipo(String clienteId,
                                     TipoConta tipo);

    // ── Busca conta por número completo ──────────────────
    //
    // Usado para transferências — cliente informa agência + conta
    // Gera: SELECT * FROM contas
    //       WHERE numero_agencia = ? AND numero_conta = ? LIMIT 1
    Optional<Conta> findByNumeroAgenciaAndNumeroConta(
            String numeroAgencia, String numeroConta);

    // ── Gera próximo número de conta (thread-safe) ───────
    //
    // SEQUENCE do PostgreSQL garante unicidade mesmo com
    // múltiplas threads gerando números ao mesmo tempo.
    // nextval() é atômico — impossível gerar o mesmo número duas vezes.
    //
    // nativeQuery = true → SQL nativo (não JPQL)
    // Necessário porque SEQUENCE é específico do PostgreSQL
    // não existe equivalente em JPQL padrão
    @Query(value = "SELECT nextval('seq_numero_conta')",
            nativeQuery = true)
    Long proximoNumeroConta();

    // ── Lista contas ativas de um cliente ────────────────
    //
    // Gera: SELECT * FROM contas
    //       WHERE cliente_id = ? AND ativa = true
    List<Conta> findByClienteIdAndAtivaTrue(String clienteId);
}