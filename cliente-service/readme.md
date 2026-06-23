# Cliente Service — Documentação Completa de Estudo

> Serviço de gerenciamento de clientes e endereços do **Tribunal Bank API**.
> Use este documento para estudar, revisar e entender cada decisão de arquitetura.

---

## Índice

1. [A ideia do Cliente Service](#1-a-ideia-do-cliente-service)
2. [Como pensamos para desenvolver](#2-como-pensamos-para-desenvolver)
3. [Conhecimentos aplicados neste projeto](#3-conhecimentos-aplicados-neste-projeto)
4. [Stack e dependências](#4-stack-e-dependências)
5. [Como rodar localmente](#5-como-rodar-localmente)
6. [Fluxo de desenvolvimento — fase a fase](#6-fluxo-de-desenvolvimento--fase-a-fase)
7. [Relacionamento Cliente e Endereço — foco principal](#7-relacionamento-cliente-e-endereço--foco-principal)
8. [Endpoints da API](#8-endpoints-da-api)
9. [Fluxos completos detalhados](#9-fluxos-completos-detalhados)
10. [Fluxo de erros](#10-fluxo-de-erros)
11. [Checklist de conceitos implementados](#11-checklist-de-conceitos-implementados)
12. [Estrutura de pacotes](#12-estrutura-de-pacotes)
13. [Tabela de decisões arquiteturais](#13-tabela-de-decisões-arquiteturais)

---

## 1. A ideia do Cliente Service

O **Cliente Service** é o segundo microsserviço do Tribunal Bank. Enquanto o Auth Service cuida de **quem pode entrar** no sistema (autenticação), o Cliente Service cuida dos **dados pessoais de quem entrou** (perfil de cliente).

### O que ele faz

```
→ Gerencia o perfil pessoal de cada cliente (nome, CPF, email, telefone)
→ Gerencia os endereços de cada cliente (RESIDENCIAL e COMERCIAL)
→ Valida CPF com o algoritmo real dos dígitos verificadores
→ Protege dados via JWT gerado pelo Auth Service
→ Garante que usuário só acessa o próprio perfil
→ Permite que administradores gerenciem todos os clientes
```

### O que ele NÃO faz

```
→ Não autentica usuários → Auth Service
→ Não gerencia contas bancárias → Conta Service (próximo)
→ Não processa transações → Transação Service (futuro)
→ Não envia emails → Notification Service (V2)
```

### Posição no ecossistema

```
                    ┌─────────────────┐
                    │   API Gateway   │ (porta 8080 — futuro)
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
    ┌─────────▼──────┐  ┌────▼─────┐  ┌────▼──────┐
    │  Auth Service  │  │ Cliente  │  │  Conta    │
    │  (porta 8081)  │  │ Service  │  │  Service  │
    │  ✅ PRONTO     │  │ (8082)   │  │ (futuro)  │
    └────────────────┘  │ ✅ PRONTO│  └───────────┘
                        └──────────┘
              │              │
    ┌─────────▼──────┐  ┌────▼─────┐
    │   auth_db      │  │cliente_db│
    │  (porta 5433)  │  │(porta    │
    │                │  │  5434)   │
    └────────────────┘  └──────────┘
```

---

## 2. Como pensamos para desenvolver

Antes de escrever uma linha de código, definimos:

### Decisões tomadas antes do código

**1. Endereço embutido (`@Embeddable`) ou tabela separada?**

```
OPÇÃO A — @Embeddable (endereço na mesma tabela):
CREATE TABLE clientes (
    id, nome, cpf,
    res_logradouro, res_numero,    ← RESIDENCIAL
    com_logradouro, com_numero ... ← COMERCIAL
)
→ Colunas duplicadas, difícil de manter ❌

OPÇÃO B — Tabela separada (@OneToMany):
CREATE TABLE clientes (id, nome, cpf...)
CREATE TABLE enderecos (id, cliente_id, tipo, logradouro...)
→ Limpo, extensível, normalizado ✅

DECISÃO: tabela separada com @OneToMany
MOTIVO: cliente pode ter RESIDENCIAL e COMERCIAL
        adicionar novos tipos no futuro é só um registro
```

**2. Soft delete ou hard delete?**

```
Hard delete → DELETE FROM clientes WHERE id = ?
→ Dados perdidos para sempre
→ Auditoria impossível
→ Histórico de transações quebrado ❌

Soft delete → UPDATE clientes SET ativo = false WHERE id = ?
→ Dados preservados
→ Auditoria funciona
→ Compliance bancário satisfeito ✅

DECISÃO: soft delete com campo ativo = false
```

**3. Validação de CPF — onde?**

```
OPÇÃO A — @Pattern no DTO:
@Pattern(regexp = "\\d{11}")
→ Valida só o formato, não o algoritmo ❌

OPÇÃO B — Validar no service:
if (!cpfValidator.isValido(cpf)) throw...
→ Funciona mas não é reutilizável ⚠️

OPÇÃO C — @Cpf anotação customizada (implementamos):
@Cpf → valida formato + algoritmo dos dígitos verificadores ✅

DECISÃO: @Cpf customizada + CpfValidatorService
```

**4. Como garantir que usuário só acessa o próprio perfil?**

```
→ usuarioId NÃO vem no body da requisição
→ Extraído do JWT no controller:
  String usuarioId = jwt.getSubject();
→ @PreAuthorize verifica o dono do recurso
→ Impossível criar perfil para outro usuário
```

---

## 3. Conhecimentos aplicados neste projeto

### Java — recursos da linguagem

| Conceito | Onde aparece | Por que foi usado |
|---|---|---|
| **Record** | `ClienteRequest`, `ClienteResponse`, `EnderecoRequest`, `EnderecoResponse` | Imutável por padrão, getters automáticos — ideal para DTOs |
| **Optional** | `ClienteRepository.findByEmail()`, `EnderecoRepository.findByClienteAndTipo()` | Evita `NullPointerException` — força tratar "não encontrado" |
| **Enum** | `TipoEndereco` — RESIDENCIAL, COMERCIAL | Conjunto fixo de valores em tempo de compilação |
| **Generics** | `JpaRepository<Cliente, String>`, `Page<ClienteResponse>` | Tipo seguro em compilação — elimina casts manuais |
| **Lambda** | `.stream().map(EnderecoResponse::from)` | Transforma coleções de forma declarativa |
| **Stream API** | `ClienteResponse.from()` — converte lista de endereços | Código funcional mais expressivo que loops for |
| **Method Reference** | `EnderecoResponse::from`, `AbstractHttpConfigurer::disable` | Atalho para lambda quando o método já existe |
| **UUID** | ID de Cliente e Endereço, UUID do Refresh Token | Identificador único sem precisar do banco para gerar |

---

### Princípios SOLID

| Princípio | Como aparece |
|---|---|
| **S — Single Responsibility** | `CpfValidatorService` só valida CPF. `ClienteService` só orquestra. `CpfValidator` só implementa a anotação. Cada classe tem um motivo para mudar. |
| **O — Open/Closed** | `BusinessException` aberta para extensão (`ClienteNotFoundException`, `CpfJaCadastradoException`) fechada para modificação |
| **L — Liskov Substitution** | Qualquer `BusinessException` pode substituir outra no `GlobalExceptionHandler` |
| **I — Interface Segregation** | `ClienteRepository` e `EnderecoRepository` separados — cada um com seus métodos específicos |
| **D — Dependency Inversion** | `ClienteService` recebe `ClienteRepository` (interface) via construtor — não instancia diretamente |

---

### Design Patterns

| Padrão | Onde aparece |
|---|---|
| **Builder** | `Cliente.builder()`, `Endereco.builder()` via Lombok `@Builder` |
| **Factory (método estático)** | `ClienteResponse.from(cliente)`, `EnderecoResponse.from(endereco)` — conversão centralizada |
| **Singleton** | Todos os `@Bean` e `@Service` — uma instância compartilhada |
| **Chain of Responsibility** | `SecurityFilterChain` — cada filtro processa ou passa adiante |
| **Strategy** | Duas `SecurityFilterChain` com `@Order` — estratégias diferentes por tipo de rota |
| **Observer** | `@EntityListeners(AuditingEntityListener)` — listener observa eventos JPA |

---

### Spring Security

| Conceito | Onde aparece |
|---|---|
| **Duas Filter Chains** | `SecurityConfig` — `@Order(1)` pública (Swagger/Actuator), `@Order(2)` protegida (todos os endpoints de negócio) |
| **oauth2ResourceServer** | `protectedFilterChain` — instala `BearerTokenAuthenticationFilter` |
| **@PreAuthorize** | `ClienteController` — `hasRole('ROLE_ADMIN')` ou `pertenceAoUsuario()` |
| **@EnableMethodSecurity** | `SecurityConfig` — ativa segurança em nível de método |
| **JwtDecoder** | `JwtConfig` — verifica tokens usando APENAS a chave pública RSA |
| **@AuthenticationPrincipal Jwt** | `ClienteController` — injeta JWT do usuário autenticado no parâmetro |
| **STATELESS** | `SessionCreationPolicy.STATELESS` — sem sessão HTTP |

---

### Spring Data JPA / Hibernate

| Conceito | Onde aparece |
|---|---|
| **ORM** | `Cliente` e `Endereco` mapeados para tabelas `clientes` e `enderecos` |
| **@OneToMany** | `Cliente.enderecos` — um cliente tem muitos endereços |
| **@ManyToOne** | `Endereco.cliente` — muitos endereços pertencem a um cliente |
| **mappedBy** | `@OneToMany(mappedBy = "cliente")` — Endereco é o dono da FK |
| **CascadeType.PERSIST/MERGE** | Salvar/atualizar cliente propaga para endereços |
| **orphanRemoval** | Remover endereço da lista → DELETE automático no banco |
| **FetchType.LAZY** | Endereços não carregam junto com o cliente a menos que solicitado |
| **@Transactional** | Métodos que modificam banco — rollback automático se falhar |
| **@Transactional(readOnly)** | Métodos de busca — desativa Dirty Checking, mais eficiente |
| **Derived Queries** | `findByCpf`, `existsByEmail`, `findAllByAtivoTrue` |
| **@Query JPQL** | Busca por nome com LIKE case-insensitive e filtro ativo |
| **Page/Pageable** | Listagem paginada com `totalElements`, `totalPages`, `content` |
| **Soft delete** | `ativo = false` em vez de DELETE físico |
| **@CreatedDate / @LastModifiedDate** | Preenchidos automaticamente pelo `AuditingEntityListener` |

---

### Banco de dados e SQL

| Conceito | Onde aparece |
|---|---|
| **DDL** | Migrations V1, V2 — `CREATE TABLE`, `CREATE INDEX` |
| **Primary Key UUID** | `id VARCHAR(36) NOT NULL PRIMARY KEY` |
| **Foreign Key** | `cliente_id REFERENCES clientes(id) ON DELETE CASCADE` |
| **UNIQUE constraint** | `cpf UNIQUE`, `email UNIQUE`, `(cliente_id, tipo) UNIQUE` |
| **Composite UNIQUE** | `CONSTRAINT uk_cliente_tipo UNIQUE (cliente_id, tipo)` — um tipo por cliente |
| **INDEX** | `idx_clientes_cpf`, `idx_clientes_email`, `idx_clientes_nome` |
| **ON DELETE CASCADE** | Deletar cliente → deleta endereços automaticamente no banco |
| **NOT NULL** | Campos obrigatórios garantidos no banco |

---

### JWT e Criptografia

| Conceito | Onde aparece |
|---|---|
| **Apenas JwtDecoder** | `JwtConfig` — cliente-service só VERIFICA, nunca GERA tokens |
| **Chave pública RSA** | Copiada do auth-service — suficiente para verificar assinaturas |
| **Claims** | `jwt.getSubject()` → email do usuário, `jwt.getClaim("roles")` → permissões |
| **PEM parsing** | `JwtConfig.parsePublicKey()` — converte String PEM → RSAPublicKey |

---

### Arquitetura

| Conceito | Onde aparece |
|---|---|
| **Microsserviços** | Cliente Service separado do Auth Service — banco próprio, porta própria |
| **REST** | Verbos HTTP corretos: POST (criar), GET (buscar), PUT (atualizar), DELETE (desativar) |
| **Status codes** | 201 (criado), 200 (ok), 204 (sem conteúdo), 404 (não encontrado), 409 (conflito) |
| **DTO Pattern** | `ClienteRequest` (entrada) vs `ClienteResponse` (saída) — contrato independente da entidade |
| **Separation of Concerns** | Controller (HTTP) → Service (negócio) → Repository (banco) |
| **FK lógica** | `usuarioId` referencia usuário do Auth Service sem FK real entre bancos |
| **Consistência eventual** | Sem FK entre serviços — consistência garantida pela lógica de negócio |
| **Fail Fast** | `@Valid` no controller valida antes de chegar no service |

---

### Ferramentas e Infraestrutura

| Ferramenta | Uso |
|---|---|
| **Flyway** | Versionamento do schema do banco |
| **Docker / docker-compose** | PostgreSQL isolado na porta 5434 |
| **HikariCP** | Pool de conexões — reutiliza conexões em vez de abrir/fechar |
| **Eureka Client** | Registra o serviço no Discovery Server |
| **Spring Actuator** | Health check em `/actuator/health` |
| **Swagger / OpenAPI** | Documentação em `http://localhost:8082/swagger-ui.html` |
| **Lombok** | `@Data`, `@Builder`, `@RequiredArgsConstructor` |

---

## 4. Stack e dependências

```xml
<!-- Web -->
spring-boot-starter-webmvc

<!-- Segurança + JWT -->
spring-boot-starter-security
spring-boot-starter-security-oauth2-resource-server

<!-- Banco -->
spring-boot-starter-data-jpa
spring-boot-starter-flyway
flyway-database-postgresql
postgresql

<!-- Validação -->
spring-boot-starter-validation

<!-- Monitoramento -->
spring-boot-starter-actuator

<!-- Service Discovery -->
spring-cloud-starter-netflix-eureka-client

<!-- Documentação -->
springdoc-openapi-starter-webmvc-ui:2.8.9

<!-- Utilitários -->
lombok
```

---

## 5. Como rodar localmente

### Pré-requisitos

```
Java 21
Docker Desktop rodando
IntelliJ IDEA
Auth Service rodando (porta 8081) — para gerar tokens JWT
```

### Passo a passo

```bash
# 1. Sobe o banco do cliente-service
cd cliente-service
docker-compose up -d

# 2. Verifica se o banco subiu
docker ps
# deve mostrar cliente-db na porta 5434

# 3. Sobe a aplicação no IntelliJ
# O Flyway executa V1 e V2 automaticamente
```

### Verificar se está rodando

```
Swagger UI:   http://localhost:8082/swagger-ui.html
Health check: http://localhost:8082/actuator/health
```

### Portas dos bancos no projeto

```
5432 → PostgreSQL local do Windows (não usar)
5433 → auth-db (banco do Auth Service)
5434 → cliente-db (banco do Cliente Service)
```

---

## 6. Fluxo de desenvolvimento — fase a fase

### Por que essa ordem importa

Cada fase depende da anterior. Um sênior nunca começa pelo controller — começa pela fundação.

---

### FASE 1 — Planejamento (sem código)

```
Definir o domínio:
→ O que o serviço faz?
→ Quais são os endpoints?
→ Quais são as entidades?
→ Quais são as regras de negócio?
→ Endereço: @Embeddable ou tabela separada?
→ Soft delete ou hard delete?
→ Validação de CPF: onde e como?
```

---

### FASE 2 — Migrations (Flyway)

```
src/main/resources/db/migration/
├── V1__create_clientes.sql
└── V2__create_enderecos.sql
```

**Por que começar pelo banco:**
O banco é a fundação. SQL primeiro → Java depois garante que o mapeamento JPA está correto.

**V1__create_clientes.sql:**
```sql
CREATE TABLE clientes (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    usuario_id      VARCHAR(36)  NOT NULL UNIQUE,
    nome            VARCHAR(255) NOT NULL,
    cpf             VARCHAR(11)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    telefone        VARCHAR(20),
    data_nascimento DATE         NOT NULL,
    ativo           BOOLEAN      NOT NULL DEFAULT true,
    criado_em       TIMESTAMP    NOT NULL,
    atualizado_em   TIMESTAMP    NOT NULL
);

CREATE INDEX idx_clientes_cpf   ON clientes(cpf);
CREATE INDEX idx_clientes_email ON clientes(email);
CREATE INDEX idx_clientes_nome  ON clientes(nome);
```

**V2__create_enderecos.sql:**
```sql
CREATE TABLE enderecos (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    cliente_id    VARCHAR(36)  NOT NULL
        REFERENCES clientes(id) ON DELETE CASCADE,
    tipo          VARCHAR(20)  NOT NULL,
    logradouro    VARCHAR(255) NOT NULL,
    numero        VARCHAR(10)  NOT NULL,
    complemento   VARCHAR(100),
    bairro        VARCHAR(100) NOT NULL,
    cidade        VARCHAR(100) NOT NULL,
    estado        CHAR(2)      NOT NULL,
    cep           VARCHAR(8)   NOT NULL,
    principal     BOOLEAN      NOT NULL DEFAULT false,
    criado_em     TIMESTAMP    NOT NULL,
    atualizado_em TIMESTAMP    NOT NULL,

    CONSTRAINT uk_cliente_tipo UNIQUE (cliente_id, tipo)
);
```

---

### FASE 3 — Entidades JPA

```
entity/
├── TipoEndereco.java   ← enum primeiro (sem dependências)
├── Endereco.java       ← usa TipoEndereco e Cliente
└── Cliente.java        ← usa Endereco (@OneToMany)
```

**Por que depois do banco:**
Entidades espelham as tabelas. Criar depois do SQL garante que o mapeamento está correto.

---

### FASE 4 — Repositories

```
repository/
├── ClienteRepository.java
└── EnderecoRepository.java
```

**Por que antes do service:**
Os repositories são injetados nos services. Criar antes para poder referenciar.

Métodos criados:
```java
// ClienteRepository
Optional<Cliente> findByCpf(String cpf);
Optional<Cliente> findByUsuarioId(String usuarioId);
boolean existsByCpf(String cpf);
boolean existsByEmail(String email);
Page<Cliente> findByNomeContendoEAtivo(String nome, Pageable pageable);
Page<Cliente> findAllByAtivoTrue(Pageable pageable);

// EnderecoRepository
List<Endereco> findByCliente(Cliente cliente);
Optional<Endereco> findByClienteAndTipo(Cliente cliente, TipoEndereco tipo);
boolean existsByClienteAndTipo(Cliente cliente, TipoEndereco tipo);
Optional<Endereco> findByClienteAndPrincipalTrue(Cliente cliente);
```

---

### FASE 5 — DTOs

```
dto/
├── ClienteRequest.java    ← entrada do cadastro/atualização
├── ClienteResponse.java   ← saída com método from(Cliente)
├── EnderecoRequest.java   ← entrada de endereço
└── EnderecoResponse.java  ← saída com método from(Endereco)
```

**Por que separar Request e Response:**
```
Request → o que o cliente MANDA (sem id, sem timestamps)
Response → o que o cliente RECEBE (com id, com timestamps)
Mudanças internas na entidade não quebram o contrato da API
```

**usuarioId NÃO está no ClienteRequest:**
```java
// Errado — cliente poderia falsificar o usuarioId
{ "nome": "João", "usuarioId": "id-de-outro-usuario" }

// Correto — extraído do JWT no controller
String usuarioId = jwt.getSubject(); // email do usuário autenticado
```

---

### FASE 6 — Exceptions

```
exception/
├── BusinessException.java           ← abstract, base de todas
├── ClienteNotFoundException.java    ← 404
├── CpfJaCadastradoException.java    ← 409
├── EmailJaCadastradoException.java  ← 409
├── EnderecoNotFoundException.java   ← 404
├── TipoEnderecoJaExisteException.java ← 409
└── GlobalExceptionHandler.java      ← @RestControllerAdvice
```

**Por que criar antes dos services:**
Os services precisam lançar exceções específicas. Criar antes evita usar `RuntimeException` genérica.

---

### FASE 7 — Configurações

```
config/
├── JpaAuditingConfig.java  ← @EnableJpaAuditing
├── JwtConfig.java          ← apenas JwtDecoder (só chave pública)
├── SecurityConfig.java     ← duas filter chains
└── SwaggerConfig.java      ← Bearer token no Swagger UI
```

**Diferença do Auth Service:**
```
Auth Service:  JwtEncoder + JwtDecoder (gera E verifica)
               Precisa da chave PRIVADA + PÚBLICA

Cliente Service: apenas JwtDecoder (só verifica)
                 Precisa APENAS da chave PÚBLICA
                 NUNCA deve ter a chave privada
```

---

### FASE 8 — Services

```
service/
├── CpfValidatorService.java  ← algoritmo dos dígitos verificadores
└── ClienteService.java       ← orquestra todo o fluxo de negócio
```

**Por que `CpfValidatorService` separado:**
Single Responsibility — validação de CPF é responsabilidade independente.
Pode ser reutilizado em outros serviços sem duplicar código.

**Algoritmo do CPF:**
```
CPF: 529.982.247-25
Base: 52998224 (9 dígitos)
Verificadores: 7 e 25 (espera 2 dígitos)

1º dígito:
5×10 + 2×9 + 9×8 + 9×7 + 8×6 + 2×5 + 2×4 + 4×3 + 7×2
= 50+18+72+63+48+10+8+12+14 = 295
295 % 11 = 9 → resto >= 2 → dígito = 11-9 = 2 (bate!) ✅

2º dígito:
5×11 + 2×10 + 9×9 + 9×8 + 8×7 + 2×6 + 2×5 + 4×4 + 7×3 + 2×2
= 55+20+81+72+56+12+10+16+21+4 = 347
347 % 11 = 6 → resto >= 2 → dígito = 11-6 = 5 (bate!) ✅
```

---

### FASE 9 — Controller

```
controller/
└── ClienteController.java
```

**Por que por último:**
Controller é a fachada — recebe HTTP e delega para o service. Criar por último porque depende de tudo.

---

## 7. Relacionamento Cliente e Endereço — foco principal

Este é o conceito mais importante do Cliente Service. Entender bem evita os erros mais comuns com JPA.

---

### Por que tabela separada

```
SE FOSSE @Embeddable (mesma tabela):
CREATE TABLE clientes (
    id, nome, cpf,
    res_logradouro, res_numero, res_cep,   ← RESIDENCIAL
    com_logradouro, com_numero, com_cep    ← COMERCIAL
)
→ 6+ colunas duplicadas
→ Adicionar tipo CORRESPONDÊNCIA = nova migração inteira
→ Consultar endereços separadamente = impossível
→ Índices em endereços = complicado

COM TABELA SEPARADA:
CREATE TABLE enderecos (
    id, cliente_id, tipo, logradouro, numero, cep...
)
→ Limpo e normalizado
→ Adicionar tipo = só um novo registro
→ Consultar endereços = query simples
→ Índices normais
```

---

### Os dois lados do relacionamento

**Regra fundamental:**
```
Quem tem a FK no banco → lado DONO → @ManyToOne + @JoinColumn
Quem NÃO tem a FK → lado INVERSO → @OneToMany + mappedBy
```

```sql
-- A FK está em enderecos:
CREATE TABLE enderecos (
    cliente_id VARCHAR(36) REFERENCES clientes(id)  ← FK aqui
)
-- Logo: Endereco é o lado DONO
```

```java
// LADO DONO — Endereco (tem a FK)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "cliente_id", nullable = false)
private Cliente cliente;

// LADO INVERSO — Cliente (não tem FK)
@OneToMany(
    mappedBy = "cliente",       // nome do campo em Endereco
    cascade = {PERSIST, MERGE}, // sem REMOVE (soft delete)
    fetch = FetchType.LAZY,
    orphanRemoval = true
)
@Builder.Default
private List<Endereco> enderecos = new ArrayList<>();
```

---

### Onde fica cada configuração

```
@OneToMany (Cliente — LADO PAI):
→ cascade      ✅ propaga operações para os filhos
→ fetch        ✅ define quando carrega
→ orphanRemoval ✅ monitora a lista
→ mappedBy     ✅ aponta para o campo no filho

@ManyToOne (Endereco — LADO FILHO):
→ fetch        ✅ define quando carrega o pai
→ @JoinColumn  ✅ nome da FK no banco
→ cascade      ❌ não faz sentido no filho
→ orphanRemoval ❌ não tem lista para monitorar
```

---

### CascadeType — nossa decisão

```
Usamos: cascade = {CascadeType.PERSIST, CascadeType.MERGE}

PERSIST → clienteRepository.save(clienteNovo)
          → salva os endereços novos junto automaticamente

MERGE   → clienteRepository.save(clienteExistente)
          → atualiza os endereços modificados junto

REMOVE  → NÃO usamos — soft delete
          delete(cliente) NÃO deleta endereços fisicamente
          Controlamos com ativo = false no service

ALL     → NÃO usamos — inclui REMOVE que é perigoso
          com soft delete
```

---

### orphanRemoval — como funciona

**orphanRemoval vs CascadeType.REMOVE:**
```
CascadeType.REMOVE → deleta TODOS os filhos quando o PAI é deletado
orphanRemoval      → deleta o filho específico que saiu da LISTA
```

```java
// CENÁRIO 1 — remover endereço específico
cliente.getEnderecos().remove(enderecoComercial);
clienteRepository.save(cliente);
// → DELETE FROM enderecos WHERE id = 'id-comercial'
// Só esse endereço é deletado — RESIDENCIAL continua

// CENÁRIO 2 — trocar todos
cliente.getEnderecos().clear();
clienteRepository.save(cliente);
// → DELETE FROM enderecos WHERE cliente_id = 'id-cliente'
```

---

### FetchType.LAZY — por que é crítico

```java
// ERRO COMUM — LazyInitializationException:
public ClienteResponse buscar(String id) {
    Cliente cliente = repository.findById(id).get();
    // Sessão do Hibernate fechou aqui
    return ClienteResponse.from(cliente);
    // from() acessa cliente.getEnderecos() → ERRO!
    // Sessão já fechou — LAZY não consegue carregar
}

// SOLUÇÃO — @Transactional mantém a sessão aberta:
@Transactional(readOnly = true)
public ClienteResponse buscar(String id) {
    Cliente cliente = repository.findById(id).get();
    return ClienteResponse.from(cliente); // ✅ sessão ainda aberta
}
```

---

### Constraint UNIQUE composta

```sql
-- Um cliente só pode ter UM endereço de cada tipo
CONSTRAINT uk_cliente_tipo UNIQUE (cliente_id, tipo)

-- Permite:
cliente A → RESIDENCIAL ✅
cliente A → COMERCIAL   ✅
cliente B → RESIDENCIAL ✅

-- Não permite:
cliente A → RESIDENCIAL (segunda vez) ❌ → erro de banco
```

No service verificamos antes de inserir:
```java
if (enderecoRepository.existsByClienteAndTipo(cliente, request.tipo())) {
    throw new TipoEnderecoJaExisteException(request.tipo().name());
}
```

---

### Diagrama completo do relacionamento

```
┌─────────────────────────────────┐
│          CLIENTES               │
├─────────────────────────────────┤
│ id (PK)          VARCHAR(36)    │
│ usuario_id       VARCHAR(36)    │ ← referência lógica ao Auth Service
│ nome             VARCHAR(255)   │
│ cpf              VARCHAR(11)    │ ← UNIQUE
│ email            VARCHAR(255)   │ ← UNIQUE
│ telefone         VARCHAR(20)    │
│ data_nascimento  DATE           │
│ ativo            BOOLEAN        │ ← soft delete
│ criado_em        TIMESTAMP      │
│ atualizado_em    TIMESTAMP      │
└────────────────┬────────────────┘
                 │ 1
                 │
                 │ N
┌────────────────▼────────────────┐
│          ENDERECOS              │
├─────────────────────────────────┤
│ id (PK)          VARCHAR(36)    │
│ cliente_id (FK)  VARCHAR(36)    │ ← REFERENCES clientes(id)
│ tipo             VARCHAR(20)    │ ← RESIDENCIAL ou COMERCIAL
│ logradouro       VARCHAR(255)   │
│ numero           VARCHAR(10)    │
│ complemento      VARCHAR(100)   │
│ bairro           VARCHAR(100)   │
│ cidade           VARCHAR(100)   │
│ estado           CHAR(2)        │
│ cep              VARCHAR(8)     │
│ principal        BOOLEAN        │
│ criado_em        TIMESTAMP      │
│ atualizado_em    TIMESTAMP      │
│                                 │
│ UNIQUE (cliente_id, tipo)       │ ← um tipo por cliente
└─────────────────────────────────┘
```

---

## 8. Endpoints da API

Base URL: `http://localhost:8082`
Swagger UI: `http://localhost:8082/swagger-ui.html`

### Clientes

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| `POST` | `/clientes` | Cadastrar cliente | Usuário autenticado |
| `GET` | `/clientes/{id}` | Buscar por ID | Próprio usuário ou ADMIN |
| `GET` | `/clientes/cpf/{cpf}` | Buscar por CPF | Apenas ADMIN |
| `GET` | `/clientes?nome=&page=&size=` | Listar com paginação | Apenas ADMIN |
| `PUT` | `/clientes/{id}` | Atualizar dados | Próprio usuário ou ADMIN |
| `DELETE` | `/clientes/{id}` | Desativar (soft delete) | Apenas ADMIN |

### Endereços

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| `POST` | `/clientes/{id}/enderecos` | Adicionar endereço | Próprio usuário ou ADMIN |
| `PUT` | `/clientes/{id}/enderecos/{endId}` | Atualizar endereço | Próprio usuário ou ADMIN |
| `DELETE` | `/clientes/{id}/enderecos/{endId}` | Remover endereço | Próprio usuário ou ADMIN |

### Exemplo de request — Cadastrar cliente

```json
POST /clientes
Authorization: Bearer eyJhbGci...

{
  "nome": "João Silva",
  "cpf": "52998224725",
  "email": "joao@email.com",
  "telefone": "11987654321",
  "dataNascimento": "1990-05-15",
  "enderecos": [
    {
      "tipo": "RESIDENCIAL",
      "logradouro": "Rua das Flores",
      "numero": "123",
      "complemento": "Apto 42",
      "bairro": "Centro",
      "cidade": "São Paulo",
      "estado": "SP",
      "cep": "01310100",
      "principal": true
    }
  ]
}
```

### Exemplo de response — 201 Created

```json
{
  "id": "uuid-gerado",
  "nome": "João Silva",
  "cpf": "52998224725",
  "email": "joao@email.com",
  "telefone": "11987654321",
  "dataNascimento": "1990-05-15",
  "ativo": true,
  "enderecos": [
    {
      "id": "uuid-endereco",
      "tipo": "RESIDENCIAL",
      "logradouro": "Rua das Flores",
      "numero": "123",
      "complemento": "Apto 42",
      "bairro": "Centro",
      "cidade": "São Paulo",
      "estado": "SP",
      "cep": "01310100",
      "principal": true
    }
  ],
  "criadoEm": "2026-06-21T16:43:17",
  "atualizadoEm": "2026-06-21T16:43:17"
}
```

### Paginação — como usar

```
GET /clientes?page=0&size=10&sort=nome,asc
GET /clientes?nome=joao&page=1&size=5

Resposta:
{
  "content": [...],       ← lista de clientes
  "totalElements": 150,   ← total de registros
  "totalPages": 15,       ← total de páginas
  "number": 0,            ← página atual (começa em 0)
  "size": 10,             ← tamanho da página
  "first": true,          ← é a primeira página?
  "last": false           ← é a última página?
}
```

---

## 9. Fluxos completos detalhados

### Fluxo 1 — Cadastro de cliente bem-sucedido

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PASSO 1: Usuário se registra no Auth Service

POST http://localhost:8081/auth/register
{ "email": "joao@email.com", "senha": "senha123" }

Auth Service:
→ Cria usuário no banco auth_db
→ Gera accessToken (JWT RS256, 15min)
→ Gera refreshToken (UUID, 7 dias)

Resposta:
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "uuid-xyz-...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PASSO 2: Usuário cadastra perfil de cliente

POST http://localhost:8082/clientes
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
{ "nome": "João", "cpf": "52998224725", ... }
     ↓
publicFilterChain → NÃO bate (não é Swagger/Actuator)
     ↓
protectedFilterChain (@Order 2)
→ BearerTokenAuthenticationFilter extrai o token
→ NimbusJwtDecoder.decode(token):
   ✓ Verifica assinatura com chave PÚBLICA RSA
     (mesma chave do Auth Service — só o público)
   ✓ Verifica expiração (exp > agora)
   ✓ Verifica integridade do payload
→ Token válido → SecurityContext populado
     ↓
ClienteController.cadastrar(request, jwt)
→ String usuarioId = jwt.getSubject()
  → "joao@email.com" (claim "sub" do JWT)
→ @Valid valida o DTO:
   ✓ nome não vazio
   ✓ CPF formato 11 dígitos + algoritmo dígitos verificadores
   ✓ email formato válido
   ✓ dataNascimento no passado
     ↓
ClienteService.cadastrar(request, usuarioId)
     ↓
1. cpfValidator.isValido("52998224725") → true ✅
2. clienteRepository.existsByCpf("52998224725") → false ✅
3. clienteRepository.existsByEmail("joao@email.com") → false ✅
4. clienteRepository.findByUsuarioId("joao@email.com") → vazio ✅
     ↓
5. Cliente.builder()
   .usuarioId("joao@email.com")
   .nome("João Silva")
   .cpf("52998224725")
   .ativo(true)
   .build()
     ↓
6. Para cada endereço no request:
   Endereco.builder()
   .cliente(cliente)
   .tipo(RESIDENCIAL)
   .logradouro("Rua das Flores")
   ...build()
   cliente.getEnderecos().add(endereco)
     ↓
7. clienteRepository.save(cliente)
   → INSERT INTO clientes VALUES (...)
   → CascadeType.PERSIST → INSERT INTO enderecos VALUES (...)
   → @CreatedDate e @LastModifiedDate preenchidos automaticamente
     ↓
8. return ClienteResponse.from(cliente)
   → Converte entidade → DTO
   → enderecos.stream().map(EnderecoResponse::from).toList()
     ↓
HTTP 201 Created
{ "id": "uuid", "nome": "João", ... }
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Fluxo 2 — Buscar cliente autenticado (dono do recurso)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
GET /clientes/uuid-do-cliente
Authorization: Bearer eyJhbGci...
     ↓
protectedFilterChain → valida JWT → OK
     ↓
@PreAuthorize("hasRole('ROLE_ADMIN') or
  @clienteService.pertenceAoUsuario(#id, authentication.name)")
     ↓
Spring avalia a expressão ANTES de entrar no método:
→ authentication.name = "joao@email.com" (do JWT)
→ clienteService.pertenceAoUsuario("uuid-do-cliente", "joao@email.com")
   → findByUsuarioId("joao@email.com") → encontrou o cliente
   → cliente.getId().equals("uuid-do-cliente") → true ✅
     ↓
ClienteController.buscarPorId("uuid-do-cliente")
     ↓
ClienteService.buscarPorId("uuid-do-cliente")
→ @Transactional(readOnly = true) → sessão aberta
→ clienteRepository.findById("uuid-do-cliente")
   → SELECT * FROM clientes WHERE id = ?
→ Cliente encontrado
→ ClienteResponse.from(cliente)
   → acessa cliente.getEnderecos() (LAZY carrega agora)
   → SELECT * FROM enderecos WHERE cliente_id = ?
   → converte cada Endereco → EnderecoResponse
     ↓
HTTP 200 OK { "id": "uuid", "nome": "João", ... }
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Fluxo 3 — Adicionar endereço COMERCIAL

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
POST /clientes/uuid-do-cliente/enderecos
Authorization: Bearer eyJhbGci...
{
  "tipo": "COMERCIAL",
  "logradouro": "Av. Paulista",
  "numero": "1000",
  "bairro": "Bela Vista",
  "cidade": "São Paulo",
  "estado": "SP",
  "cep": "01310100",
  "principal": false
}
     ↓
protectedFilterChain → valida JWT → OK
@PreAuthorize → pertenceAoUsuario → true ✅
     ↓
ClienteService.adicionarEndereco("uuid-do-cliente", request)
     ↓
1. clienteRepository.findById("uuid-do-cliente") → encontrou ✅
2. enderecoRepository.existsByClienteAndTipo(cliente, COMERCIAL)
   → SELECT count FROM enderecos WHERE cliente_id = ? AND tipo = ?
   → false → pode adicionar ✅
3. request.principal() = false → não precisa remover principal atual
4. Endereco.builder().cliente(cliente).tipo(COMERCIAL)...build()
5. cliente.getEnderecos().add(novoEndereco)
6. clienteRepository.save(cliente)
   → CascadeType.PERSIST → INSERT INTO enderecos
     ↓
HTTP 201 Created
{ "id": "uuid-endereco", "tipo": "COMERCIAL", ... }
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Fluxo 4 — Remover endereço (orphanRemoval)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DELETE /clientes/uuid-cliente/enderecos/uuid-endereco
Authorization: Bearer eyJhbGci...
     ↓
protectedFilterChain → valida JWT → OK
@PreAuthorize → pertenceAoUsuario → true ✅
     ↓
ClienteService.removerEndereco("uuid-cliente", "uuid-endereco")
     ↓
1. clienteRepository.findById("uuid-cliente") → cliente ✅
2. enderecoRepository.findById("uuid-endereco") → endereco ✅
3. cliente.getEnderecos().remove(endereco)
   → Endereco virou "órfão" — perdeu o vínculo com o cliente
4. clienteRepository.save(cliente)
   → Hibernate detecta o órfão (orphanRemoval = true)
   → DELETE FROM enderecos WHERE id = 'uuid-endereco'
   → Só esse endereço é deletado — os outros permanecem
     ↓
HTTP 204 No Content
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Fluxo 5 — Desativar cliente (soft delete)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DELETE /clientes/uuid-do-cliente
Authorization: Bearer eyJhbGci... (ADMIN)
     ↓
protectedFilterChain → valida JWT → OK
@PreAuthorize("hasRole('ROLE_ADMIN')") → ADMIN ✅
     ↓
ClienteService.desativar("uuid-do-cliente")
     ↓
1. clienteRepository.findById("uuid-do-cliente") → cliente ✅
2. cliente.setAtivo(false)
   → NÃO deleta fisicamente
   → Dado preservado para auditoria
3. clienteRepository.save(cliente)
   → UPDATE clientes SET ativo = false WHERE id = ?
   → @LastModifiedDate atualizado automaticamente
     ↓
HTTP 204 No Content

RESULTADO:
→ Cliente não consegue mais fazer login (Auth Service verifica ativo)
→ Dados preservados no banco ✅
→ Histórico de transações intacto ✅
→ Compliance bancário satisfeito ✅
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Fluxo 6 — CPF inválido

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
POST /clientes
{ "nome": "João", "cpf": "11111111111", ... }
     ↓
@Valid no controller ativa Bean Validation
→ @Cpf → CpfValidator.isValid("11111111111")
→ "11111111111".matches("(\\d)\\1{10}") → true
→ Sequência de dígitos iguais → INVÁLIDO
→ isValid retorna false
→ ConstraintViolationException
→ MethodArgumentNotValidException
     ↓
GlobalExceptionHandler.handleValidacao()
→ Coleta mensagem do @Cpf: "CPF inválido"
     ↓
HTTP 400 Bad Request
{
  "mensagem": "CPF inválido",
  "timestamp": "2026-06-21T16:43:17",
  "status": 400
}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Fluxo 7 — Usuário tenta acessar perfil de outro

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
GET /clientes/uuid-de-outro-cliente
Authorization: Bearer eyJhbGci... (token de joao@email.com)
     ↓
protectedFilterChain → valida JWT → OK
     ↓
@PreAuthorize("hasRole('ROLE_ADMIN') or
  @clienteService.pertenceAoUsuario(#id, authentication.name)")
     ↓
→ hasRole('ROLE_ADMIN') → false (João é ROLE_USER)
→ pertenceAoUsuario("uuid-de-outro-cliente", "joao@email.com")
   → findByUsuarioId("joao@email.com") → cliente do João
   → cliente.getId().equals("uuid-de-outro-cliente") → FALSE ❌
     ↓
@PreAuthorize retorna false
→ Spring lança AccessDeniedException
→ GlobalExceptionHandler não captura (Spring Security captura antes)
     ↓
HTTP 403 Forbidden
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 10. Fluxo de erros

### Mapeamento de exceções

| Exceção | HTTP | Quando |
|---|---|---|
| `ClienteNotFoundException` | 404 | Cliente não encontrado por ID ou CPF |
| `EnderecoNotFoundException` | 404 | Endereço não encontrado |
| `CpfJaCadastradoException` | 409 | CPF já existe no banco |
| `EmailJaCadastradoException` | 409 | Email já existe no banco |
| `TipoEnderecoJaExisteException` | 409 | Cliente já tem endereço desse tipo |
| `MethodArgumentNotValidException` | 400 | Campos inválidos (@Valid) |
| `AccessDeniedException` | 403 | Usuário sem permissão |
| `Exception` | 500 | Erro inesperado |

### Formato padronizado de erro

```json
{
  "mensagem": "CPF já cadastrado: 52998224725",
  "timestamp": "2026-06-21T16:43:17.958",
  "status": 409
}
```

---

## 11. Checklist de conceitos implementados

### Java e Spring

- [x] **Records** — DTOs imutáveis (`ClienteRequest`, `ClienteResponse`, `EnderecoRequest`, `EnderecoResponse`)
- [x] **Optional** — evita NullPointerException nos repositories
- [x] **Enum** — `TipoEndereco` (RESIDENCIAL, COMERCIAL)
- [x] **Stream API** — conversão de lista de endereços em `ClienteResponse.from()`
- [x] **Builder Pattern** — criação de entidades via `Cliente.builder()`
- [x] **@RequiredArgsConstructor** — injeção de dependência via construtor
- [x] **@Transactional** — atomicidade nas operações de escrita
- [x] **@Transactional(readOnly = true)** — otimização nas operações de leitura

### Spring Security

- [x] **Duas SecurityFilterChain** — pública (Swagger/Actuator) e protegida (negócio)
- [x] **@Order(1) e @Order(2)** — prioridade entre as chains
- [x] **oauth2ResourceServer** — validação de JWT na chain protegida
- [x] **JwtDecoder apenas** — cliente-service só verifica, nunca gera tokens
- [x] **Chave pública RSA** — mesma do auth-service, copiada no properties
- [x] **@PreAuthorize** — controle de acesso por role e por dono do recurso
- [x] **@EnableMethodSecurity** — ativa segurança em nível de método
- [x] **@AuthenticationPrincipal Jwt** — extrai dados do usuário autenticado
- [x] **STATELESS session** — sem sessão HTTP no servidor

### JPA e Relacionamento

- [x] **@OneToMany** — Cliente tem lista de Enderecos
- [x] **@ManyToOne** — Endereco pertence a um Cliente
- [x] **mappedBy** — Endereco é o lado dono da FK
- [x] **@JoinColumn** — define nome da FK no banco
- [x] **CascadeType.PERSIST e MERGE** — propaga save para endereços
- [x] **FetchType.LAZY** — carregamento sob demanda
- [x] **orphanRemoval = true** — deleta endereço removido da lista
- [x] **Soft delete** — `ativo = false` em vez de DELETE físico
- [x] **@CreatedDate / @LastModifiedDate** — auditoria automática
- [x] **@EnableJpaAuditing** — ativa o sistema de auditoria
- [x] **@Builder.Default** — inicializa lista vazia e ativo=true no Builder

### Banco de dados

- [x] **Flyway** — V1 (clientes) e V2 (enderecos) automaticamente
- [x] **UUID como PK** — sem expor volume de dados
- [x] **UNIQUE constraint** — CPF, email, (cliente_id, tipo)
- [x] **Composite UNIQUE** — um tipo de endereço por cliente
- [x] **INDEX** — nos campos de busca mais comuns
- [x] **FK com ON DELETE CASCADE** — enderecos deletados com cliente
- [x] **NOT NULL** — campos obrigatórios garantidos no banco
- [x] **Porta separada (5434)** — banco isolado do auth-service

### Validação

- [x] **@Valid** — ativa Bean Validation no controller
- [x] **@NotBlank** — campos texto obrigatórios
- [x] **@Email** — formato de email
- [x] **@Pattern** — regex para CEP (`\\d{8}`) e estado (`[A-Z]{2}`)
- [x] **@Past** — data de nascimento no passado
- [x] **@Size** — tamanho máximo de campos
- [x] **@Cpf** — anotação customizada que valida algoritmo real
- [x] **CpfValidatorService** — algoritmo Módulo 11 dos dígitos verificadores
- [x] **CpfValidator** — implementa `ConstraintValidator<Cpf, String>`

### Arquitetura REST

- [x] **POST** — criar recursos (201 Created)
- [x] **GET** — buscar recursos (200 OK)
- [x] **PUT** — atualizar recursos (200 OK)
- [x] **DELETE** — desativar recursos (204 No Content)
- [x] **Status codes corretos** — 201, 200, 204, 400, 403, 404, 409, 500
- [x] **DTO Pattern** — Request separado de Response
- [x] **Método `from()` estático** — conversão entidade → DTO centralizada
- [x] **Paginação** — `Pageable`, `Page<T>`, `@PageableDefault`
- [x] **Filtro por nome** — `?nome=joao` na listagem
- [x] **Nested resources** — `/clientes/{id}/enderecos/{endId}`

### Boas práticas (SRP e DRY)

- [x] **Single Responsibility** — cada classe tem uma responsabilidade
- [x] **DRY** — `construirEndereco()` privado evita duplicação
- [x] **Fail Fast** — validação no DTO antes de chegar no service
- [x] **GlobalExceptionHandler** — tratamento centralizado de erros
- [x] **FK lógica** — `usuarioId` sem FK real entre microsserviços
- [x] **Consistência eventual** — padrão de microsserviços
- [x] **Separation of Concerns** — Controller → Service → Repository

### Ferramentas

- [x] **HikariCP** — pool de conexões (automático pelo Spring Boot)
- [x] **Eureka Client** — registro no Discovery Server
- [x] **Actuator** — health check em `/actuator/health`
- [x] **Swagger** — documentação com Bearer token em `/swagger-ui.html`
- [x] **Lombok** — `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Builder.Default`
- [x] **Docker** — PostgreSQL isolado via docker-compose

---

## 12. Estrutura de pacotes

```
cliente-service/
│
├── src/main/java/com/tribunalbank/cliente/
│   │
│   ├── ClienteApplication.java
│   │
│   ├── config/
│   │   ├── JpaAuditingConfig.java      # @EnableJpaAuditing
│   │   ├── JwtConfig.java              # apenas JwtDecoder (chave pública)
│   │   ├── SecurityConfig.java         # duas filter chains
│   │   └── SwaggerConfig.java          # Bearer token no Swagger
│   │
│   ├── controller/
│   │   └── ClienteController.java      # /clientes/* endpoints
│   │
│   ├── dto/
│   │   ├── ClienteRequest.java         # record {nome, cpf, email, ...}
│   │   ├── ClienteResponse.java        # record + from(Cliente)
│   │   ├── EnderecoRequest.java        # record {tipo, logradouro, ...}
│   │   └── EnderecoResponse.java       # record + from(Endereco)
│   │
│   ├── entity/
│   │   ├── TipoEndereco.java           # enum RESIDENCIAL, COMERCIAL
│   │   ├── Cliente.java                # tabela: clientes
│   │   └── Endereco.java               # tabela: enderecos
│   │
│   ├── exception/
│   │   ├── BusinessException.java      # abstract — base de todas
│   │   ├── ClienteNotFoundException.java   # 404
│   │   ├── CpfJaCadastradoException.java   # 409
│   │   ├── EmailJaCadastradoException.java # 409
│   │   ├── EnderecoNotFoundException.java  # 404
│   │   ├── TipoEnderecoJaExisteException.java # 409
│   │   └── GlobalExceptionHandler.java # @RestControllerAdvice
│   │
│   ├── repository/
│   │   ├── ClienteRepository.java      # JpaRepository<Cliente, String>
│   │   └── EnderecoRepository.java     # JpaRepository<Endereco, String>
│   │
│   ├── service/
│   │   ├── CpfValidatorService.java    # algoritmo Módulo 11
│   │   └── ClienteService.java         # lógica de negócio
│   │
│   └── validator/
│       ├── Cpf.java                    # anotação @Cpf customizada
│       └── CpfValidator.java           # ConstraintValidator<Cpf, String>
│
├── src/main/resources/
│   ├── application.properties          # spring.application.name + profiles
│   ├── application-dev.properties      # porta, banco, JWT, Eureka, Swagger
│   └── db/migration/
│       ├── V1__create_clientes.sql
│       └── V2__create_enderecos.sql
│
└── docker-compose.yml                  # PostgreSQL porta 5434
```

---

## 13. Tabela de decisões arquiteturais

| Decisão | Alternativa | Motivo |
|---|---|---|
| **Tabela separada** para endereços | `@Embeddable` na mesma tabela | Cliente tem dois tipos de endereço — colunas duplicadas ficaria feio e difícil de manter |
| **`@OneToMany` + `@ManyToOne`** | `@ManyToMany` | Relacionamento direto — endereço pertence a um único cliente |
| **`CascadeType.PERSIST + MERGE`** sem REMOVE | `CascadeType.ALL` | Soft delete — não queremos deletar endereços fisicamente ao desativar cliente |
| **`orphanRemoval = true`** | Deletar manualmente no service | Endereço sem cliente é lixo — deve ser deletado quando sair da lista |
| **`FetchType.LAZY`** em todos | `FetchType.EAGER` | Evita carregar endereços em toda consulta de cliente — performance |
| **Soft delete** (`ativo = false`) | Hard delete (`DELETE FROM`) | Dados bancários precisam de histórico para auditoria e compliance |
| **`usuarioId` como FK lógica** | FK real entre bancos | Microsserviços têm bancos separados — FK real impossível entre serviços |
| **`@Cpf` anotação customizada** | `@Pattern(regexp="\\d{11}")` | Valida formato E algoritmo dos dígitos verificadores em um lugar só |
| **`CpfValidatorService` separado** | Validar direto no `ClienteService` | SRP — validação de CPF é responsabilidade independente |
| **Apenas `JwtDecoder`** | `JwtEncoder` + `JwtDecoder` | Cliente-service só verifica tokens — nunca gera. Nunca deve ter a chave privada |
| **Chave pública RSA copiada** | Chamar Auth Service para validar | Performance — sem chamada de rede para validar cada token |
| **`@PreAuthorize` no controller** | Verificar no service | Separação de responsabilidades — segurança na entrada, negócio no service |
| **`Page<ClienteResponse>`** | `List<ClienteResponse>` | Tabela grande — sem paginação retornaria milhares de registros |
| **`CONSTRAINT uk_cliente_tipo`** | Só verificar no código | Banco como última linha de defesa — mesmo se o código falhar, o banco garante |
| **Porta 5434** | Usar a 5432 | PostgreSQL local na 5432 — cada serviço tem sua porta isolada |

---

*Tribunal Bank API — Cliente Service v1.0*
*Documentação de estudo — Junho 2026*