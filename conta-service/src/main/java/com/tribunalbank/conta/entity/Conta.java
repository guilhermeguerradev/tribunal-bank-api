package com.tribunalbank.conta.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ═══════════════════════════════════════════════════════════
// CONTA — Entidade que representa uma conta bancária
// ═══════════════════════════════════════════════════════════
//
// PADRÃO ADOTADO — Anemic Domain Model:
// A entidade contém apenas dados (campos + getters/setters).
// Toda lógica de negócio fica no ContaService.
// É o padrão mais comum no ecossistema Spring Boot
// e o que o mercado brasileiro espera ver.
//
// Por que não @Data?
// @Data gera toString() que inclui TODOS os campos.
// Em entidades JPA isso causa dois problemas:
// → LazyInitializationException: acessa campos LAZY
//   só para gerar o toString (ex: ao logar a entidade)
// → StackOverflow: se houver referência circular entre entidades
//   (A → B → A) o toString entra em loop infinito
// Solução: usar @Getter + @Setter separados.
// Se precisar de equals/hashCode, sobrescrever manualmente
// baseado apenas no id (chave primária).
//
// MODELAGEM DO NÚMERO DE CONTA:
// Formato exibido: Ag. 0001 | Conta 12345678-9
// 3 campos separados no banco:
// → numeroAgencia    → permite filtrar/agrupar por agência
// → numeroConta      → sequencial gerado pelo NumeroConta service
// → digitoVerificador → calculado e salvo (não recalculado sempre)
//
// FK LÓGICA (clienteId sem FK real):
// Cada microsserviço tem seu próprio banco.
// FK real entre bancos diferentes é impossível.
// clienteId referencia o Cliente Service via FeignClient
// no momento da criação — depois fica salvo aqui.
// Padrão de microsserviços: consistência eventual.
//
// VALORES MONETÁRIOS — BigDecimal obrigatório:
// Double → ponto flutuante binário → impreciso:
//   0.1 + 0.2 = 0.30000000000000004 ← ERRADO para dinheiro
// BigDecimal → precisão exata → obrigatório em sistemas financeiros
// DECIMAL(19,2) no banco = BigDecimal(precision=19, scale=2) no Java
// ═══════════════════════════════════════════════════════════

// @Entity → marca a classe como entidade JPA
// Hibernate mapeia essa classe para a tabela "contas"
@Entity

// @Table → define o nome exato da tabela no banco
// Sem isso o Hibernate usaria o nome da classe
@Table(name = "contas")

// @Getter → Lombok gera getters para todos os campos
// Separado do @Setter para ter controle granular:
// podemos marcar um campo como @Setter(AccessLevel.NONE)
// para torná-lo imutável após a criação
@Getter

// @Setter → Lombok gera setters para todos os campos
// Em vez de @Data — evita o toString problemático em JPA
@Setter

// @Builder → permite construir objetos com o padrão Builder:
// Conta.builder().clienteId("uuid").tipo(CORRENTE).build()
// Muito mais legível que construtores com muitos parâmetros
@Builder

// @NoArgsConstructor → gera construtor sem argumentos
// O JPA EXIGE esse construtor para instanciar a entidade
// Sem ele o Hibernate lança InstantiationException em runtime
@NoArgsConstructor

// @AllArgsConstructor → gera construtor com todos os argumentos
// Necessário quando @Builder está presente com @NoArgsConstructor
// O Lombok usa ele internamente para o Builder funcionar
@AllArgsConstructor

// @EntityListeners → registra o listener de auditoria
// O AuditingEntityListener preenche automaticamente:
// → @CreatedDate quando o registro é inserido
// → @LastModifiedDate quando o registro é salvo
// SEM essa anotação os campos de auditoria ficam NULL
// Também precisa de @EnableJpaAuditing em uma @Configuration
@EntityListeners(AuditingEntityListener.class)
public class Conta {

    // ── Identificador ────────────────────────────────────

    // @Id → chave primária da tabela
    @Id

    // @GeneratedValue(UUID) → Spring/Hibernate gera UUID automaticamente
    // UUID é mais seguro que Long sequencial:
    // → Não expõe volume de dados do sistema
    // → Impossível de adivinhar — sem enumeration attacks
    // → Pode ser gerado sem consultar o banco
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // ── Vínculo com o Cliente Service ───────────────────

    // Referência lógica ao Cliente Service — sem FK real entre bancos
    //
    // FLUXO DE VALIDAÇÃO:
    // 1. POST /contas chega com clienteId
    // 2. ContaService chama FeignClient → GET /clientes/{clienteId}
    // 3. Cliente existe e está ativo? → prossegue
    // 4. Salva o clienteId aqui — não consulta mais
    //
    // Após a criação, se o cliente for desativado:
    // → A conta ainda existe (consistência eventual)
    // → O Transação Service vai verificar se conta está ativa
    // → Sem FK o banco não bloqueia — a lógica de negócio bloqueia
    @Column(nullable = false)
    private String clienteId;

    // ── Número da conta ──────────────────────────────────

    // Agência fixa para todo o sistema — nosso banco tem uma agência
    // "0001" para todos os clientes
    // Separado em campo próprio para permitir filtros por agência
    // e facilitar a migração futura para múltiplas agências
    @Column(nullable = false, length = 4)
    private String numeroAgencia;

    // Número sequencial gerado pelo NumeroConta service
    // Formato: 8 dígitos com zeros à esquerda
    // 00000001, 00000002, 00000003...
    // UNIQUE junto com agência — ver constraint na migration
    @Column(nullable = false, length = 8)
    private String numeroConta;

    // Dígito verificador calculado a partir do número da conta
    // Gerado pelo NumeroConta service no momento da criação
    // Salvo no banco — não recalculado a cada leitura
    // Algoritmo: Módulo 10 ou 11 (similar ao CPF)
    @Column(nullable = false, length = 1)
    private String digitoVerificador;

    // ── Tipo da conta ────────────────────────────────────

    // @Enumerated(STRING) → salva o nome do enum no banco:
    // "CORRENTE", "POUPANCA", "SALARIO", "INVESTIMENTO"
    // NUNCA use EnumType.ORDINAL:
    // Se você reordenar o enum, todos os dados ficam errados
    // Ex: CORRENTE=0, POUPANCA=1 → troca ordem → POUPANCA vira CORRENTE
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoConta tipo;

    // ── Valores financeiros ──────────────────────────────

    // Saldo atual da conta em Reais
    //
    // DECIMAL(19,2) no banco → precision=19, scale=2 no Java
    // precision = 19 → total de dígitos significativos
    // scale = 2     → casas decimais (centavos)
    // Máximo: R$ 99.999.999.999.999.999,99
    //
    // @Builder.Default → necessário com @Builder para ter valor inicial
    // Sem isso: Conta.builder().build() → saldo = null
    // Com isso:  Conta.builder().build() → saldo = R$ 0,00
    //
    // BigDecimal.ZERO é uma constante imutável — thread-safe
    // Sempre use constantes do BigDecimal: ZERO, ONE, TEN
    // em vez de new BigDecimal("0") — evita objetos desnecessários
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal saldo = BigDecimal.ZERO;

    // Limite do cheque especial
    // Saldo disponível real = saldo + limite
    //
    // Exemplos:
    // saldo =  500, limite =    0 → disponível =  500 (sem cheque especial)
    // saldo =    0, limite =  500 → disponível =  500 (usando o limite)
    // saldo = -200, limite =  500 → disponível =  300 (no vermelho mas dentro do limite)
    // saldo = -600, limite =  500 → disponível = -100 (passou do limite → bloqueado)
    //
    // Contas POUPANCA e SALARIO geralmente têm limite = 0
    // Conta CORRENTE pode ter limite configurado pelo gerente
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal limite = BigDecimal.ZERO;

    // ── Status da conta ──────────────────────────────────

    // Soft delete — conta nunca é deletada fisicamente
    //
    // Regulação bancária (Banco Central) exige:
    // → Manter histórico de todas as contas por 5+ anos
    // → Preservar todas as transações associadas
    // → Auditoria deve ser possível mesmo em contas encerradas
    //
    // ativa = false → conta encerrada
    // ativa = true  → conta operacional
    //
    // @Builder.Default → conta sempre começa ativa
    @Column(nullable = false)
    @Builder.Default
    private boolean ativa = true;

    // Adicionar na entidade Conta:
    @Column(nullable = false)
    private String usuarioId; // subject do JWT — para o @PreAuthorize

    // ── Auditoria automática ─────────────────────────────

    // @CreatedDate → preenchido pelo AuditingEntityListener
    // quando o registro é inserido pela primeira vez
    //
    // updatable = false → NUNCA atualizado após a criação
    // Mesmo que alguém chame .save() repetidamente,
    // esse campo permanece com o valor original
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    // @LastModifiedDate → atualizado automaticamente
    // em toda operação de .save() via JPA
    //
    // ATENÇÃO: NÃO atualiza com queries nativas (nativeQuery = true)
    // Sempre prefira .save() para que a auditoria funcione
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime atualizadoEm;
}