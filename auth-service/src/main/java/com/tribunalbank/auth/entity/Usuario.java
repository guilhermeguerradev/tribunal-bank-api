package com.tribunalbank.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

// Marca a classe como uma entidade JPA — ou seja, representa uma tabela no banco
@Entity

// Define o nome da tabela no banco de dados
// Sem isso o JPA usaria o nome da classe (Usuario) como nome da tabela
@Table(name = "usuarios")

// Lombok — gera automaticamente getters, setters, toString, equals e hashCode
@Data

// Lombok — permite construir objetos com o padrão builder:
// Usuario.builder().email("x@x.com").build()
@Builder

// Lombok — gera o construtor sem argumentos, obrigatório para o JPA funcionar
// O JPA precisa instanciar a classe com new Usuario() internamente
@NoArgsConstructor

// Lombok — gera o construtor com todos os argumentos
@AllArgsConstructor

// Ativa o sistema de auditoria do Spring Data JPA
// Sem isso as anotações @CreatedDate e @LastModifiedDate não funcionam
@EntityListeners(AuditingEntityListener.class)
public class Usuario {

    // Marca o campo como chave primária da tabela
    @Id

    // Gera o ID automaticamente usando UUID
    // UUID é um identificador único universal — ex: "550e8400-e29b-41d4-a716-446655440000"
    // Mais seguro que ID numérico sequencial (1, 2, 3...) porque não expõe
    // quantos usuários o sistema tem e é impossível de adivinhar
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // nullable = false → campo obrigatório no banco (NOT NULL)
    // unique = true    → não permite dois usuários com o mesmo email
    @Column(nullable = false, unique = true)
    private String email;


    @Column(nullable = false)
    private String senha;

    // Controla se o usuário pode acessar o sistema
    // false = conta bloqueada pelo administrador
    // Mais seguro do que deletar o usuário — preserva o histórico
    @Column(nullable = false)
    private boolean ativo;

    // @ElementCollection → coleção de valores simples vinculados a esta entidade
    // Diferente de @ManyToMany, não cria uma entidade separada para Role
    // O JPA cria automaticamente uma tabela "usuario_roles" com as colunas:
    // usuario_id | role
    // abc-123    | ROLE_USER
    // abc-123    | ROLE_ADMIN
    @ElementCollection(fetch = FetchType.EAGER)

    // Define o nome da tabela que vai armazenar as roles
    // e qual coluna referencia o usuário dono dessas roles
    @CollectionTable(
            name = "usuario_roles",
            joinColumns = @JoinColumn(name = "usuario_id")
    )

    // Salva o Enum como String no banco ("ROLE_USER") em vez de número (0, 1)
    // Se salvar como número e você mudar a ordem do Enum, todos os dados corrompem
    // Como String é legível e seguro contra mudanças na ordem do Enum
    @Enumerated(EnumType.STRING)

    // @Builder.Default → necessário quando usa @Builder com valor inicial
    // Sem isso o builder ignora o "= new HashSet<>()" e o campo vem null
    @Builder.Default

    // Por que Set e não List ou Array?
    //
    // Array      → tamanho fixo, ruim para coleções que crescem
    // List       → permite duplicatas: [ROLE_USER, ROLE_USER, ROLE_ADMIN]
    //              um usuário poderia ter a mesma role duas vezes — não faz sentido
    // Set        → não permite duplicatas por definição matemática
    //              garante que cada role aparece no máximo uma vez
    //
    // HashSet especificamente porque:
    // → busca em O(1) — tempo constante, muito rápido
    // → não precisa de ordem (roles não têm ordenação natural)
    // → LinkedHashSet se quiséssemos manter ordem de inserção
    // → TreeSet se quiséssemos ordem alfabética
    private Set<Role> roles = new HashSet<>();

    // Preenchido automaticamente pelo Spring Data quando o registro é criado
    // updatable = false → nunca é alterado depois da criação, mesmo que alguém tente
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    // Atualizado automaticamente pelo Spring Data sempre que o registro é salvo
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime atualizadoEm;
}