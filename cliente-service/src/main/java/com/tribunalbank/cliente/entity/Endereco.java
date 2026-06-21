package com.tribunalbank.cliente.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// ═══════════════════════════════════════════════════════════
// ENDERECO — Entidade que representa um endereço de cliente
// ═══════════════════════════════════════════════════════════
//
// DECISÃO DE MODELAGEM — tabela separada vs @Embeddable:
// Endereço em tabela separada porque um cliente pode ter
// RESIDENCIAL e COMERCIAL — dois endereços de tipos diferentes.
// Com @Embeddable teríamos colunas duplicadas na tabela clientes:
// res_logradouro, res_numero, com_logradouro, com_numero...
// Tabela separada é mais limpa, extensível e normalizada.
//
// RELACIONAMENTO:
// Endereco é o lado FILHO do relacionamento com Cliente.
// Um Cliente tem muitos Enderecos (@OneToMany no Cliente).
// Um Endereco pertence a um Cliente (@ManyToOne aqui).
// A FK cliente_id fica na tabela enderecos — padrão SQL correto.
// ═══════════════════════════════════════════════════════════

// @Entity — marca a classe como entidade JPA
// O Hibernate vai mapear essa classe para a tabela "enderecos"
// Sem essa anotação o Spring Data ignora a classe completamente
@Entity

// @Table — define o nome exato da tabela no banco
// Sem ela o Hibernate usaria o nome da classe: "Endereco"
// Boa prática: sempre definir explicitamente para evitar surpresas
@Table(name = "enderecos")

// @Data — Lombok gera: getters, setters, toString, equals, hashCode
// Não use @Data em entidades com relacionamentos bidirecionais sem cuidado
// O toString gerado pode causar StackOverflow ao incluir o cliente
// Solução: sobrescrever toString ou usar @ToString(exclude = "cliente")
@Data

// @Builder — permite construir objetos com o padrão Builder:
// Endereco.builder().logradouro("Rua X").numero("123").build()
// Muito mais legível que construtores com 10+ parâmetros
@Builder

// @NoArgsConstructor — gera construtor sem argumentos
// O JPA EXIGE esse construtor para instanciar a entidade internamente
// Sem ele o Hibernate lança InstantiationException em runtime
@NoArgsConstructor

// @AllArgsConstructor — gera construtor com todos os argumentos
// Necessário quando @Builder está presente com @NoArgsConstructor
// O Lombok usa ele internamente para o Builder funcionar
@AllArgsConstructor

// @EntityListeners — registra listeners de eventos JPA na entidade
// O AuditingEntityListener é o listener do Spring Data que:
// → Preenche @CreatedDate quando o registro é inserido
// → Atualiza @LastModifiedDate quando o registro é salvo
// SEM essa anotação os campos de auditoria ficam NULL
// Também é necessário @EnableJpaAuditing em uma @Configuration
@EntityListeners(AuditingEntityListener.class)
public class Endereco {

    // ── Identificador ────────────────────────────────────
    // @Id — marca o campo como chave primária da tabela
    @Id

    // @GeneratedValue(UUID) — o Spring/Hibernate gera o UUID automaticamente
    // UUID é mais seguro que Long sequencial:
    // → Não expõe quantos registros existem no banco
    // → Pode ser gerado sem consultar o banco (offline generation)
    // → Impossível de adivinhar — sem enumeration attacks
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // ── Relacionamento com Cliente ───────────────────────

    // @ManyToOne — define o lado filho do relacionamento
    // "Muitos endereços pertencem a um cliente"
    // Gera a coluna cliente_id (FK) na tabela enderecos
    //
    // FetchType.LAZY — o Cliente NÃO é carregado junto com o Endereço
    // O carregamento acontece sob demanda (quando você acessa .getCliente())
    // LAZY é o padrão recomendado para @ManyToOne e @OneToMany
    // EAGER carregaria o Cliente em TODA consulta de Endereço — desnecessário
    // e pode causar N+1 queries em listas de endereços
    @ManyToOne(fetch = FetchType.LAZY)

    // @JoinColumn — define o nome da coluna FK no banco
    // name = "cliente_id" → cria a coluna cliente_id na tabela enderecos
    // nullable = false → toda linha de endereço DEVE ter um cliente_id
    // Sem @JoinColumn o Hibernate geraria um nome automático (imprevisível)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // ── Tipo do endereço ─────────────────────────────────

    // @Enumerated(EnumType.STRING) — salva o enum como texto no banco
    // → Armazena "RESIDENCIAL" ou "COMERCIAL" (legível)
    // → NUNCA use EnumType.ORDINAL (salva 0 ou 1):
    //   se você reordenar o enum, TODOS os dados ficam errados
    //   Ex: RESIDENCIAL=0, COMERCIAL=1 → reordena → COMERCIAL=0, RESIDENCIAL=1
    //   Todos os endereços residenciais viram comerciais silenciosamente
    @Enumerated(EnumType.STRING)

    // length = 20 → limita a coluna a 20 chars (RESIDENCIAL tem 11)
    // Economiza espaço no banco sem risco de truncamento
    @Column(nullable = false, length = 20)
    private TipoEndereco tipo;

    // ── Campos de endereço ───────────────────────────────

    // nullable = false → NOT NULL no banco
    // O banco é a última linha de defesa — mesmo se o código falhar
    // a constraint do banco impede dados inválidos
    @Column(nullable = false)
    private String logradouro;

    // Número pode ser alfanumérico: "123", "S/N", "123-A"
    // length = 10 é suficiente para qualquer formato brasileiro
    @Column(nullable = false, length = 10)
    private String numero;

    // Complemento é OPCIONAL — sem nullable = false
    // "Apto 42", "Bloco B", "Casa dos fundos" ou null
    // Não colocar @Column aqui usa os defaults do Hibernate
    private String complemento;

    @Column(nullable = false)
    private String bairro;

    @Column(nullable = false)
    private String cidade;

    // UF brasileira: "SP", "RJ", "MG", "RS" etc
    // CHAR(2) no banco — sempre exatamente 2 caracteres
    // length = 2 garante isso na constraint do Hibernate
    @Column(nullable = false, length = 2)
    private String estado;

    // Armazena APENAS os 8 dígitos numéricos: "01310100"
    // Sem hífen, sem pontos — formatação é responsabilidade do front-end
    // Evita inconsistências: "01310-100" vs "01310100" causariam bugs em buscas
    @Column(nullable = false, length = 8)
    private String cep;

    // Flag que indica o endereço principal do cliente
    // Um cliente pode marcar um como principal para correspondência
    // false por default — definido explicitamente no service
    // A lógica "só um pode ser principal" fica no ClienteService
    @Column(nullable = false)
    private boolean principal;

    // ── Auditoria ────────────────────────────────────────

    // @CreatedDate — preenchido automaticamente pelo AuditingEntityListener
    // quando o registro é inserido pela primeira vez no banco
    //
    // updatable = false — essa coluna NUNCA é atualizada após a criação
    // Mesmo que alguém chame .save() no entity, esse campo não muda
    // Garante a imutabilidade do timestamp de criação
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    // @LastModifiedDate — atualizado automaticamente pelo listener
    // em TODA operação de .save() via JPA
    // ATENÇÃO: NÃO atualiza com queries nativas (nativeQuery = true)
    // nem com .saveAll() em alguns casos — sempre prefira .save() individual
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime atualizadoEm;
}