package com.tribunalbank.cliente.repository;

import com.tribunalbank.cliente.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// ═══════════════════════════════════════════════════════════
// CLIENTE REPOSITORY — Acesso ao banco para a entidade Cliente
// ═══════════════════════════════════════════════════════════
//
// JpaRepository<Cliente, String>:
// → Cliente → entidade que esse repository gerencia
// → String  → tipo do ID (UUID armazenado como String)
//
// O Spring Data gera a implementação automaticamente em runtime.
// Você não escreve SQL — o Spring interpreta o nome do método
// e gera o SQL equivalente. Isso é o padrão "Derived Queries".
//
// Métodos herdados do JpaRepository (alguns exemplos):
// → save(entity)           → INSERT ou UPDATE
// → findById(id)           → SELECT WHERE id = ?
// → findAll()              → SELECT * (cuidado em tabelas grandes)
// → deleteById(id)         → DELETE WHERE id = ? (hard delete)
// → existsById(id)         → SELECT COUNT WHERE id = ?
// → count()                → SELECT COUNT(*)
// ═══════════════════════════════════════════════════════════
public interface ClienteRepository extends JpaRepository<Cliente, String> {

    // ── Busca por CPF ────────────────────────────────────
    //
    // Spring Data interpreta "findBy" + "Cpf" e gera:
    // SELECT * FROM clientes WHERE cpf = ? LIMIT 1
    //
    // Retorna Optional<Cliente> — força o chamador a tratar
    // o caso de "não encontrado" explicitamente com .orElseThrow()
    // Em vez de retornar null e causar NullPointerException
    Optional<Cliente> findByCpf(String cpf);

    // ── Busca por usuarioId ──────────────────────────────
    //
    // Usado para verificar se um usuário já tem perfil de cliente
    // e para buscar o cliente pelo token JWT (que contém o usuarioId)
    //
    // SELECT * FROM clientes WHERE usuario_id = ? LIMIT 1
    Optional<Cliente> findByUsuarioId(String usuarioId);

    // ── Verifica existência por CPF ──────────────────────
    //
    // Mais eficiente que findByCpf() quando só precisa saber SE existe
    // Gera: SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
    //       FROM clientes WHERE cpf = ?
    // Não carrega o objeto inteiro — só verifica existência
    boolean existsByCpf(String cpf);

    // ── Verifica existência por email ────────────────────
    //
    // Usado no cadastro para validar email único antes de salvar
    // SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
    // FROM clientes WHERE email = ?
    boolean existsByEmail(String email);

    // ── Listar com paginação e filtro por nome ───────────
    //
    // Pageable → objeto que contém: página atual, tamanho, ordenação
    // Page<Cliente> → resultado com: content, totalElements, totalPages
    //
    // Por que @Query aqui e não Derived Query?
    // O nome do método para busca com LIKE ficaria:
    // findByNomeContainingIgnoreCaseAndAtivoTrue(String nome, Pageable p)
    // → Funciona mas é difícil de ler e manter
    // @Query é mais legível para consultas complexas
    //
    // LOWER() → busca case-insensitive: "joão" encontra "João", "JOÃO"
    // :nome   → parâmetro nomeado via @Param — mais seguro que posicional (?)
    // ativo = true → só retorna clientes ativos (soft delete)
    @Query("SELECT c FROM Cliente c " +
            "WHERE LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
            "AND c.ativo = true")
    Page<Cliente> findByNomeContendoEAtivo(@Param("nome") String nome, Pageable pageable);

    // ── Listar todos ativos com paginação ────────────────
    //
    // Derived Query para buscar todos os clientes ativos
    // Spring gera: SELECT * FROM clientes WHERE ativo = true
    // Com paginação: adiciona LIMIT e OFFSET automaticamente
    Page<Cliente> findAllByAtivoTrue(Pageable pageable);
}