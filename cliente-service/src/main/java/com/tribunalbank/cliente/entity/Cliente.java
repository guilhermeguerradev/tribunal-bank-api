package com.tribunalbank.cliente.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ═══════════════════════════════════════════════════════════
// CLIENTE — Entidade principal do cliente-service
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE:
// Representa os dados pessoais de um cliente do banco.
// Cada Cliente está vinculado a um usuário do Auth Service
// via usuarioId — referência lógica (sem FK real entre bancos).
//
// POR QUE SEM FK REAL PARA O AUTH SERVICE?
// Em microsserviços, cada serviço tem seu próprio banco.
// Não é possível criar FK entre bancos diferentes.
// A consistência é garantida pela lógica de negócio:
// → Ao criar cliente, verificamos se o usuário existe no Auth
// → Se o usuário for deletado, desativamos o cliente (ativo=false)
// Isso é chamado de "consistência eventual" — padrão em microsserviços.
//
// SOFT DELETE:
// Clientes nunca são deletados fisicamente do banco.
// Quando "deletados", apenas ativo=false é setado.
// Isso preserva o histórico de transações e auditoria.
// ═══════════════════════════════════════════════════════════
@Entity
@Table(name = "clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Cliente {

    // ── Identificador ────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // ── Vínculo com o Auth Service ───────────────────────

    // ID do usuário no Auth Service — referência lógica, não FK real
    // unique = true → um usuário só pode ter UM perfil de cliente
    // Sem isso, o mesmo usuário poderia criar múltiplos clientes
    //
    // Por que String e não Long?
    // O Auth Service usa UUID como ID de usuário (também String)
    // Manter o mesmo tipo evita conversões e erros de tipo
    @Column(nullable = false, unique = true)
    private String usuarioId;

    // ── Dados pessoais ───────────────────────────────────

    @Column(nullable = false)
    private String nome;

    // CPF armazenado SEM formatação — apenas os 11 dígitos: "12345678901"
    // unique = true → constraint de unicidade no banco
    // length = 11 → otimiza o tipo da coluna (VARCHAR(11) vs VARCHAR(255))
    //
    // Por que sem formatação?
    // "123.456.789-01" e "12345678901" são o mesmo CPF
    // Armazenar sem máscara evita duplicatas por diferença de formatação
    // A formatação para exibição é responsabilidade do front-end
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    // Email pode ser diferente do usado no Auth Service
    // Um cliente pode ter email pessoal e email de login diferentes
    // unique = true → sem dois clientes com o mesmo email
    @Column(nullable = false, unique = true)
    private String email;

    // Telefone é opcional no cadastro
    // Pode ser null — cliente pode informar depois
    // Armazenado sem máscara: "11987654321" (DDD + número)
    private String telefone;

    // LocalDate — apenas data, sem hora (data de nascimento não tem hora)
    // Diferente de LocalDateTime que inclui hora:minuto:segundo
    // Mapeado para tipo DATE no PostgreSQL (mais eficiente que TIMESTAMP)
    @Column(nullable = false)
    private LocalDate dataNascimento;

    // Soft delete — false = cliente desativado
    // @Builder.Default garante que o Builder inicializa como true
    // Sem isso: Cliente.builder().build() → ativo = false (default boolean)
    // Com isso: Cliente.builder().build() → ativo = true (nosso default)
    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    // ── Relacionamento com Endereços ─────────────────────

    // @OneToMany — lado pai do relacionamento
    // "Um cliente tem muitos endereços"
    //
    // mappedBy = "cliente" → o campo "cliente" da entidade Endereco
    // é o dono do relacionamento (onde está o @ManyToOne e a FK)
    // Sem mappedBy o Hibernate criaria uma tabela de junção desnecessária
    //
    // cascade = {PERSIST, MERGE}:
    // → CascadeType.PERSIST → save(clienteNovo) salva endereços novos junto
    // → CascadeType.MERGE   → save(clienteExistente) atualiza endereços junto
    // → SEM CascadeType.REMOVE → delete(cliente) NÃO deleta endereços
    //   fisicamente — usamos soft delete (ativo=false) e controlamos no service
    //   CascadeType.ALL incluiria REMOVE — perigoso com soft delete
    //
    // fetch = FetchType.LAZY → endereços NÃO são carregados com o cliente
    // Só carregam quando você acessa cliente.getEnderecos()
    // Evita carregar lista de endereços em toda consulta de cliente
    // EAGER carregaria sempre — ineficiente para listas grandes
    //
    // orphanRemoval = true → se um endereço sair da lista do cliente,
    // ele é deletado do banco automaticamente
    // "Órfão" = endereço sem dono = lixo no banco
    // Ex: cliente.getEnderecos().remove(end) + save() → DELETE no banco
    // Diferente do CascadeType.REMOVE que deleta TUDO ao deletar o pai
    @OneToMany(
            mappedBy = "cliente",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )

    // @Builder.Default — inicializa a lista vazia quando usar o Builder
    // Sem isso: Cliente.builder().build() → enderecos = null
    // Com isso: Cliente.builder().build() → enderecos = []
    // NullPointerException evitado ao chamar cliente.getEnderecos().add(...)
    @Builder.Default
    private List<Endereco> enderecos = new ArrayList<>();

    // ── Auditoria ────────────────────────────────────────

    // @CreatedDate — preenchido automaticamente pelo AuditingEntityListener
    // quando o registro é inserido pela primeira vez no banco
    // updatable = false → essa coluna NUNCA é alterada após a criação
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    // @LastModifiedDate — atualizado automaticamente em todo .save() via JPA
    // ATENÇÃO: não atualiza com queries nativas (nativeQuery = true)
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime atualizadoEm;
}