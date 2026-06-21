package com.tribunalbank.cliente.repository;

import com.tribunalbank.cliente.entity.Cliente;
import com.tribunalbank.cliente.entity.Endereco;
import com.tribunalbank.cliente.entity.TipoEndereco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// ═══════════════════════════════════════════════════════════
// ENDERECO REPOSITORY — Acesso ao banco para a entidade Endereco
// ═══════════════════════════════════════════════════════════
//
// Complementa o ClienteRepository para operações específicas
// de endereço que não fazem sentido via cliente.getEnderecos()
//
// Quando usar EnderecoRepository vs cliente.getEnderecos()?
// → EnderecoRepository: buscar endereço por ID, verificar tipo
// → cliente.getEnderecos(): quando já tem o cliente carregado
//   e quer manipular a lista (add, remove, iterate)
// ═══════════════════════════════════════════════════════════
public interface EnderecoRepository extends JpaRepository<Endereco, String> {

    // ── Busca todos os endereços de um cliente ───────────
    //
    // Gera: SELECT * FROM enderecos WHERE cliente_id = ?
    // Útil quando você só tem o objeto Cliente e quer os endereços
    // sem carregar o cliente inteiro novamente
    List<Endereco> findByCliente(Cliente cliente);

    // ── Busca endereço por cliente e tipo ────────────────
    //
    // Garante a constraint: um cliente só pode ter um RESIDENCIAL
    // e um COMERCIAL. Usado antes de adicionar novo endereço:
    // se já existe do mesmo tipo → lança exceção ou substitui
    //
    // Gera: SELECT * FROM enderecos
    //       WHERE cliente_id = ? AND tipo = ? LIMIT 1
    Optional<Endereco> findByClienteAndTipo(Cliente cliente,
                                            TipoEndereco tipo);

    // ── Verifica se existe endereço de um tipo para o cliente ──
    //
    // Mais eficiente que findByClienteAndTipo() quando
    // só precisa saber SE existe sem carregar o objeto
    //
    // Gera: SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
    //       FROM enderecos WHERE cliente_id = ? AND tipo = ?
    boolean existsByClienteAndTipo(Cliente cliente, TipoEndereco tipo);

    // ── Busca o endereço principal de um cliente ─────────
    //
    // Gera: SELECT * FROM enderecos
    //       WHERE cliente_id = ? AND principal = true LIMIT 1
    // Usado para exibir o endereço padrão em correspondências
    Optional<Endereco> findByClienteAndPrincipalTrue(Cliente cliente);
}