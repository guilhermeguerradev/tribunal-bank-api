# Auth Service — Documentação Completa de Estudo

> Serviço de autenticação do **Tribunal Bank API**.
> Use este documento para estudar, revisar e entender cada decisão de arquitetura.

---

## Conhecimentos aplicados neste projeto

Esta seção mapeia cada conceito de programação que você encontra neste projeto — onde ele aparece e por que foi usado. Use como guia de estudo.

---

### Orientação a Objetos (POO)

| Conceito            | Onde aparece no projeto                                                    | Como é usado                                                                                                                                            |
| ------------------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Herança**         | `EmailJaCadastradoException extends BusinessException`                     | Todas as exceções herdam de `BusinessException`. Permite capturar qualquer exceção de negócio com um único `@ExceptionHandler(BusinessException.class)` |
| **Polimorfismo**    | `UsuarioDetailsService implements UserDetailsService`                      | O Spring Security chama `loadUserByUsername()` sem saber que é a nossa implementação — só sabe que é um `UserDetailsService`                            |
| **Encapsulamento**  | Campos `private` nas entidades + getters via Lombok                        | O `senha` nunca é acessado diretamente — sempre via `passwordEncoder.matches()`                                                                         |
| **Abstração**       | `UsuarioRepository extends JpaRepository`                                  | Você usa `findByEmail()` sem saber como o SQL é gerado — Spring Data abstrai o banco                                                                    |
| **Interface**       | `UserDetailsService`, `JpaRepository`, `PasswordEncoder`                   | Spring Security, Spring Data e BCrypt são interfaces — você troca a implementação sem mudar quem usa                                                    |
| **Classe abstrata** | `BusinessException extends RuntimeException`                               | Serve como base para exceções específicas — define comportamento comum (mensagem de erro)                                                               |
| **Composição**      | `AuthService` tem `JwtService`, `RefreshTokenService`, `UsuarioRepository` | `AuthService` não herda — ele usa (compõe) os outros serviços                                                                                           |

---

### Java — recursos da linguagem

| Recurso                          | Onde aparece                                                       | Por que foi usado                                                                                |
| -------------------------------- | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| **Record**                       | `RegisterRequest`, `LoginRequest`, `AuthResponse`                  | Imutável por padrão, getters automáticos, `equals/hashCode` automáticos — ideal para DTOs        |
| **Optional**                     | `UsuarioRepository.findByEmail()` retorna `Optional<Usuario>`      | Evita `NullPointerException` — força tratar o caso de "não encontrado" explicitamente            |
| **Enum**                         | `Role.java` — `ROLE_USER`, `ROLE_ADMIN`                            | Conjunto fixo de valores conhecidos em tempo de compilação — impossível passar um valor inválido |
| **Generics**                     | `JpaRepository<Usuario, String>`, `Set<Role>`, `Optional<Usuario>` | Tipo seguro em tempo de compilação — elimina casts manuais e erros em runtime                    |
| **Lambda**                       | `.map(usuario -> ...)`, `.stream().filter(...)`                    | Código funcional mais expressivo que loops `for` tradicionais                                    |
| **Stream API**                   | `usuarios.getRoles().stream().map(Role::name).toList()`            | Transforma coleções de forma declarativa — o quê, não como                                       |
| **Method Reference**             | `Role::name`, `AbstractHttpConfigurer::disable`                    | Atalho para lambda quando o método já existe — mais limpo que `role -> role.name()`              |
| **var** (inferência)             | Pode usar em métodos locais onde o tipo é óbvio                    | Java 11+ infere o tipo automaticamente — menos verbosidade                                       |
| **instanceof pattern**           | `if (ex instanceof EmailJaCadastradoException e)`                  | Java 16+ — evita cast manual depois do instanceof                                                |
| **String.format / interpolação** | Mensagens de exceção com o valor problemático                      | Contexto no erro: "Email já cadastrado: joao@test.com" em vez de "Email já cadastrado"           |
| **UUID**                         | `UUID.randomUUID().toString()` no RefreshToken                     | Identificador único universal sem precisar de banco para gerar — garante unicidade distribuída   |
| **Base64**                       | `PemKeyParser` decodifica as chaves RSA                            | Chaves RSA são dados binários — Base64 é a forma de representar binário como texto               |
| **KeyFactory / KeySpec**         | `PemKeyParser.parsePublicKey()`                                    | API padrão do Java (`java.security`) para trabalhar com criptografia RSA                         |

---

### Princípios SOLID

| Princípio                     | Como aparece no projeto                                                                                                                                                      |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **S — Single Responsibility** | `PemKeyParser` só converte chaves. `JwtConfig` só configura beans. `JwtService` só gera/lê tokens. `AuthService` só orquestra o fluxo. Cada classe tem um motivo para mudar. |
| **O — Open/Closed**           | `BusinessException` é aberta para extensão (novas exceções herdam dela) e fechada para modificação (não precisa mudar ela para adicionar novos tipos de erro)                |
| **L — Liskov Substitution**   | `UsuarioDetailsService` substitui qualquer `UserDetailsService` — o Spring Security funciona igual sem saber que é a nossa implementação                                     |
| **I — Interface Segregation** | Usamos `PasswordEncoder` (não `BCryptPasswordEncoder`) como tipo do campo — dependemos da interface menor, não da implementação concreta                                     |
| **D — Dependency Inversion**  | `AuthService` recebe `UsuarioRepository` (interface) via construtor — não instancia `new UsuarioRepositoryImpl()`. O Spring injeta.                                          |

---

### Design Patterns (Padrões de Projeto)

| Padrão                      | Onde aparece                                                                                  | Explicação                                                                                           |
| --------------------------- | --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| **Builder**                 | `Usuario.builder().email(...).build()` via Lombok `@Builder`                                  | Constrói objetos complexos com muitos campos opcionais sem construtores gigantes                     |
| **Factory**                 | `KeyFactory.getInstance("RSA")` no `PemKeyParser`                                             | Cria objetos de um tipo sem expor a lógica de criação — você pede uma chave RSA, ele sabe como fazer |
| **Singleton**               | Todos os `@Bean` do Spring são singletons por padrão                                          | Uma única instância de `AuthService`, `JwtService` etc compartilhada por todas as threads            |
| **Chain of Responsibility** | `SecurityFilterChain` — cada filtro processa ou passa adiante                                 | `BearerTokenFilter` → `AuthorizationFilter` → Controller. Cada elo decide se trata ou passa          |
| **Template Method**         | `JpaRepository` — Spring Data gera os métodos como `findByEmail`                              | O "template" já existe (lógica de query), você só define o nome do método                            |
| **Decorator**               | `@Transactional` envolve o método com comportamento de transação sem mudar o código do método | Adiciona comportamento sem herança                                                                   |
| **Observer**                | `@EntityListeners(AuditingEntityListener.class)`                                              | O listener "observa" eventos de save/update do JPA e preenche `criadoEm`/`atualizadoEm`              |
| **Strategy**                | `PasswordEncoder` — pode trocar BCrypt por Argon2 sem mudar `AuthService`                     | A estratégia de criptografia é injetada, não acoplada                                                |
| **Proxy**                   | Spring cria proxies para `@Transactional`, `@PreAuthorize`, `@Cacheable`                      | Você chama o método normalmente, o Spring intercepta via proxy para adicionar comportamento          |

---

### Spring Framework

| Conceito                  | Onde aparece                                                          | O que é                                                                                                                        |
| ------------------------- | --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| **IoC Container**         | Todo o projeto                                                        | O Spring gerencia a criação e injeção de objetos — você não usa `new NomeDaClasse()` na maioria dos casos                      |
| **Dependency Injection**  | Construtores com `@Autowired` ou injeção por construtor               | Spring injeta as dependências automaticamente — `AuthService` recebe `JwtService` sem instanciar                               |
| **Bean**                  | Tudo anotado com `@Service`, `@Repository`, `@Configuration`, `@Bean` | Objetos gerenciados pelo container Spring — criados, configurados e destruídos pelo Spring                                     |
| **AOP (Aspect-Oriented)** | `@Transactional`, `@PreAuthorize`                                     | Comportamento transversal adicionado via aspectos sem poluir o código de negócio                                               |
| **Auto-Configuration**    | Spring Boot configura HikariCP, Hibernate, Flyway automaticamente     | Você não precisa configurar `DataSource`, `EntityManagerFactory` manualmente — Spring Boot detecta as dependências e configura |
| **Profiles**              | `application-dev.properties`, `application-prod.properties`           | Configurações diferentes por ambiente — Spring ativa o arquivo certo                                                           |
| **Value Injection**       | `@Value("${jwt.private-key}")`                                        | Injeta propriedades de configuração diretamente em campos                                                                      |
| **Event System**          | `AuditingEntityListener` escuta eventos JPA                           | Publicação e consumo de eventos internos do Spring                                                                             |

---

### Spring Security

| Conceito                   | Onde aparece                                               | O que é                                                                                        |
| -------------------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **Filter Chain**           | `SecurityConfig` com duas chains `@Order(1)` e `@Order(2)` | Pipeline de filtros HTTP que cada requisição atravessa antes de chegar no controller           |
| **Authentication**         | `authManager.authenticate(...)` no login                   | Processo de verificar "quem é você?" — valida credenciais e retorna um objeto `Authentication` |
| **Authorization**          | `.authorizeHttpRequests()`, `@PreAuthorize`                | Processo de verificar "você pode fazer isso?" — verifica permissões após autenticação          |
| **UserDetails**            | `UsuarioDetailsService implements UserDetailsService`      | Contrato do Spring Security para carregar dados do usuário do banco                            |
| **SecurityContext**        | Populado pelo `BearerTokenAuthenticationFilter`            | Armazena o usuário autenticado durante o ciclo de vida da requisição (por thread)              |
| **OAuth2 Resource Server** | `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`         | Configuração que instala o filtro JWT e conecta ao `JwtDecoder`                                |
| **BCrypt**                 | `BCryptPasswordEncoder` no `SecurityConfig`                | Algoritmo de hash de senha lento por design — dificulta brute force                            |
| **STATELESS**              | `SessionCreationPolicy.STATELESS`                          | Sem sessão HTTP — cada requisição é independente, autenticada via JWT                          |

---

### Spring Data JPA / Hibernate

| Conceito                   | Onde aparece                                         | O que é                                                                               |
| -------------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------------------- |
| **ORM**                    | Entidades `@Entity` mapeadas para tabelas            | Object-Relational Mapping — converte Java ↔ SQL automaticamente                       |
| **JPQL / Derived Queries** | `findByEmail`, `existsByEmail`                       | Spring Data gera SQL a partir do nome do método — sem escrever SQL                    |
| **Transaction**            | `@Transactional` em `AuthService.registrar()`        | Garante que save do usuário + save do refresh token ocorrem juntos — ou tudo ou nada  |
| **Lazy vs Eager**          | `@ManyToOne(fetch = FetchType.LAZY)` no RefreshToken | Lazy = carrega só quando acessado. Eager = carrega junto. Trade-off de performance    |
| **Cascade**                | `ON DELETE CASCADE` nas migrations                   | Quando usuário é deletado, seus tokens e roles são deletados automaticamente          |
| **Auditing**               | `@CreatedDate`, `@LastModifiedDate`                  | Spring Data preenche automaticamente timestamps de criação e atualização              |
| **ElementCollection**      | `Set<Role> roles` na entidade `Usuario`              | Coleção de tipos simples em tabela separada sem criar entidade — gera `usuario_roles` |

---

### Banco de dados e SQL

| Conceito              | Onde aparece                                        | O que é                                                                  |
| --------------------- | --------------------------------------------------- | ------------------------------------------------------------------------ |
| **DDL**               | Migrations V1, V2, V3                               | Data Definition Language — `CREATE TABLE`, `ALTER TABLE`                 |
| **DML**               | Migration V4                                        | Data Manipulation Language — `INSERT`, `UPDATE`, `DELETE`                |
| **Primary Key**       | `id VARCHAR(36) PRIMARY KEY`                        | Identificador único de cada registro                                     |
| **Foreign Key**       | `REFERENCES usuarios(id)`                           | Relacionamento entre tabelas — integridade referencial                   |
| **Composite Key**     | `PRIMARY KEY (usuario_id, role)` na tabela de roles | Chave primária composta — impede roles duplicadas para o mesmo usuário   |
| **Constraint UNIQUE** | `email VARCHAR(255) UNIQUE`                         | Banco garante unicidade — segunda camada de proteção além do código Java |
| **ON DELETE CASCADE** | `REFERENCES usuarios(id) ON DELETE CASCADE`         | Deleta registros filhos automaticamente quando o pai é deletado          |
| **NOT NULL**          | Na maioria das colunas                              | Banco recusa inserção sem valor — integridade dos dados                  |
| **Boolean**           | `ativo BOOLEAN`, `revogado BOOLEAN`                 | Flags de estado — mais semântico que 0/1                                 |

---

### Criptografia e Segurança

| Conceito                       | Onde aparece                                   | O que é                                                                                   |
| ------------------------------ | ---------------------------------------------- | ----------------------------------------------------------------------------------------- |
| **Criptografia assimétrica**   | Par de chaves RSA (privada + pública)          | Dois valores matematicamente relacionados — assinar com privada, verificar com pública    |
| **Hash**                       | BCrypt na senha do usuário                     | Função unidirecional — impossível reverter para a senha original                          |
| **Salt**                       | Embutido automaticamente pelo BCrypt           | Valor aleatório adicionado antes do hash — mesmo senha gera hashes diferentes             |
| **JWT (JSON Web Token)**       | Access token gerado pelo `JwtService`          | Token autocontido com claims assinadas — não precisa de banco para validar                |
| **Base64**                     | Codificação das chaves PEM e das partes do JWT | Converte dados binários para caracteres texto transmissíveis via HTTP                     |
| **Claims**                     | `sub`, `roles`, `exp`, `iat` dentro do JWT     | "Afirmações" dentro do token — informações sobre o usuário e o token                      |
| **PKCS#8**                     | Formato da chave privada RSA                   | Padrão de empacotamento de chaves privadas — usado pelo Java e OpenSSL moderno            |
| **X.509**                      | Formato da chave pública RSA                   | Padrão de certificados e chaves públicas — usado pelo Java (`X509EncodedKeySpec`)         |
| **XSS (Cross-Site Scripting)** | Razão para não usar localStorage para tokens   | Ataque onde scripts maliciosos roubam dados do localStorage                               |
| **CSRF**                       | Desabilitado no `SecurityConfig`               | Cross-Site Request Forgery — protege formulários HTML com sessão, irrelevante em APIs JWT |

---

### Arquitetura e Boas Práticas

| Conceito                          | Onde aparece                                                    | O que é                                                                                                    |
| --------------------------------- | --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **Microsserviços**                | Auth Service separado de Cliente Service, Conta Service etc     | Cada serviço tem responsabilidade única, deploy independente, escala independente                          |
| **REST**                          | Endpoints `/auth/register`, `/auth/login` etc                   | Arquitetura de API usando HTTP — verbos (GET, POST), status codes, JSON                                    |
| **Stateless**                     | JWT + `SessionCreationPolicy.STATELESS`                         | Servidor não guarda estado entre requisições — cada uma é independente                                     |
| **Service Discovery**             | Eureka Client registrando o Auth Service                        | Serviços se encontram dinamicamente — sem IP hardcoded                                                     |
| **Health Check**                  | `/actuator/health`                                              | Endpoint que indica se o serviço está saudável — usado por Eureka e Kubernetes                             |
| **Separation of Concerns**        | Controller → Service → Repository                               | Cada camada tem uma responsabilidade — controller recebe HTTP, service tem lógica, repository acessa banco |
| **DTO Pattern**                   | `RegisterRequest`, `AuthResponse`                               | Objetos de transferência — separa o contrato da API da estrutura interna das entidades                     |
| **Exception Hierarchy**           | `BusinessException` → exceções específicas                      | Hierarquia permite tratar categorias de erro com um único handler                                          |
| **Fail Fast**                     | Validação no DTO com `@Valid` antes de chegar no service        | Detecta erros o mais cedo possível — antes de qualquer processamento                                       |
| **DRY (Don't Repeat Yourself)**   | `PUBLIC_ENDPOINTS` constante usada nas duas chains              | Uma única lista de rotas públicas — muda em um lugar, afeta tudo                                           |
| **Convention over Configuration** | Spring Boot configura automaticamente com base nas dependências | Menos configuração manual — mais foco no negócio                                                           |

---

### Testes

| Conceito             | Onde aparece                                       | O que é                                                                            |
| -------------------- | -------------------------------------------------- | ---------------------------------------------------------------------------------- |
| **Unit Test**        | `AuthServiceTest` com Mockito                      | Testa uma única unidade isolada — sem banco, sem Spring                            |
| **Mock**             | `@Mock UsuarioRepository`                          | Objeto falso que simula o comportamento real — controla o que retorna              |
| **Stub**             | `when(repo.findByEmail(...)).thenReturn(...)`      | Define o retorno do mock para um cenário específico                                |
| **Verify**           | `verify(repo, times(1)).save(any())`               | Verifica que o mock foi chamado — testa comportamento, não só resultado            |
| **Integration Test** | `AuthControllerTest` com `@SpringBootTest`         | Testa múltiplas camadas juntas com Spring Context real                             |
| **Testcontainers**   | PostgreSQL real em Docker nos testes de integração | Banco de dados real nos testes — sem H2 fake que tem comportamento diferente       |
| **@DataJpaTest**     | `UsuarioRepositoryTest`                            | Sobe apenas a camada JPA com H2 — mais rápido que `@SpringBootTest` completo       |
| **TDD (opcional)**   | Escrever o teste antes do código                   | Red → Green → Refactor — garante que o código é testável por design                |
| **AAA Pattern**      | Given/When/Then nos testes                         | Arrange (prepara) → Act (executa) → Assert (verifica) — estrutura padrão de testes |

---

## Índice

1. [O que é este serviço](#1-o-que-é-este-serviço)
2. [Stack e dependências](#2-stack-e-dependências)
3. [Como rodar localmente](#3-como-rodar-localmente)
4. [Flyway vs Spring DDL — a comparação definitiva](#4-flyway-vs-spring-ddl--a-comparação-definitiva)
5. [Fluxo de desenvolvimento — fase a fase com arquivos](#5-fluxo-de-desenvolvimento--fase-a-fase-com-arquivos)
6. [Chaves RSA — por que não HMAC](#6-chaves-rsa--por-que-não-hmac)
7. [Access Token e Refresh Token — os riscos de fazer errado](#7-access-token-e-refresh-token--os-riscos-de-fazer-errado)
8. [Como o front-end usa os tokens](#8-como-o-front-end-usa-os-tokens)
9. [Endpoints da API](#9-endpoints-da-api)
10. [Fluxos completos detalhados](#10-fluxos-completos-detalhados)
11. [Fluxo de erros](#11-fluxo-de-erros)
12. [Testes — unitários e de integração](#12-testes--unitários-e-de-integração)
13. [Anotações importantes do Spring](#13-anotações-importantes-do-spring)
14. [Arquitetura de segurança — duas Filter Chains](#14-arquitetura-de-segurança--duas-filter-chains)
15. [Estrutura de pacotes](#15-estrutura-de-pacotes)
16. [Tabela de decisões arquiteturais](#16-tabela-de-decisões-arquiteturais)
17. [Spring Actuator — Health Check e Monitoramento](#17-spring-actuator--health-check-e-monitoramento)

---

## 1. O que é este serviço

O **Auth Service** é o portão de entrada de todo o sistema. Nenhum usuário acessa qualquer outro microsserviço sem passar por ele primeiro.

**Responsabilidades:**

- Registrar novos usuários com senha criptografada em BCrypt
- Autenticar usuários (email + senha) e emitir tokens JWT assinados com RSA
- Renovar o access token usando o refresh token sem precisar de novo login
- Revogar sessões (logout) invalidando o refresh token no banco
- Proteger endpoints que exigem autenticação via Bearer token

**O que ele NÃO faz:**

- Gerenciar contas bancárias → Cliente Service
- Processar transações → Transação Service
- Enviar e-mails de confirmação → Notification Service (V2)

---

## 2. Stack e dependências

| Tecnologia                               | Versão      | Por quê                                            |
| ---------------------------------------- | ----------- | -------------------------------------------------- |
| Java                                     | 21          | LTS com Virtual Threads, Records, Pattern Matching |
| Spring Boot                              | 4.0.7       | Versão estável com suporte ao Spring Cloud Oakwood |
| Spring Security + OAuth2 Resource Server | 7.0.6       | Filtros JWT, BCrypt, AuthenticationManager         |
| Spring Data JPA + Hibernate              | 4.0.6 / 7.2 | ORM — mapeia entidades Java ↔ tabelas PostgreSQL   |
| Flyway                                   | 11.x        | Versionamento de schema do banco                   |
| PostgreSQL                               | 16          | Banco relacional — via Docker                      |
| Nimbus JOSE JWT                          | 10.x        | Biblioteca de referência para JWT/JWK/RSA          |
| Lombok                                   | 1.18        | Elimina boilerplate (getters, setters, builders)   |
| SpringDoc OpenAPI                        | 2.8.9       | Gera a documentação Swagger automaticamente        |
| Eureka Client                            | 5.0.2       | Registra o serviço no Discovery Server             |

---

## 3. Como rodar localmente

### Pré-requisitos

- Java 21 instalado
- Docker Desktop rodando
- IntelliJ IDEA (recomendado)
- Sem PostgreSQL local na porta 5432 (ou mudar para 5433 no compose)

### Passo a passo

```bash
# 1. Sobe o banco PostgreSQL via Docker
cd auth-service
docker-compose up -d

# 2. Define a senha do postgres (necessário por causa do pg_hba.conf)
docker exec -it auth-db psql -U postgres -c "ALTER USER postgres WITH PASSWORD 'postgres';"

# 3. Sobe o Discovery Server primeiro (porta 8761)
cd ../discovery-server
./mvnw spring-boot:run

# 4. Sobe o Auth Service (porta 8081)
cd ../auth-service
./mvnw spring-boot:run
```

### Variáveis de ambiente (IntelliJ)

Configure em **Run → Edit Configurations → Environment Variables:**

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/auth_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
JWT_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----\n...
JWT_PUBLIC_KEY=-----BEGIN PUBLIC KEY-----\n...
JWT_ACCESS_TOKEN_EXPIRATION=900000
JWT_REFRESH_TOKEN_EXPIRATION=604800000
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka
```

> **Por que porta 5433?** Havia um PostgreSQL instalado localmente na 5432. O Docker usa 5433 para evitar conflito. Em produção o banco fica em servidor dedicado — sem conflito.

### Verificar se está rodando

- Swagger UI: http://localhost:8081/swagger-ui.html
- Health check: http://localhost:8081/actuator/health
- Eureka dashboard: http://localhost:8761

---

## 4. Flyway vs Spring DDL — a comparação definitiva

Esta é uma das decisões mais importantes em qualquer projeto Java profissional.

### O que o Spring oferece sem Flyway

O Spring Boot + Hibernate tem uma propriedade chamada `spring.jpa.hibernate.ddl-auto` que controla automaticamente o schema do banco:

```properties
spring.jpa.hibernate.ddl-auto=create
# → Dropa e recria todas as tabelas ao iniciar
# → DESTRÓI todos os dados em produção
# → Nunca use em produção

spring.jpa.hibernate.ddl-auto=create-drop
# → Cria ao iniciar, DROPA ao desligar
# → Útil para testes com H2 em memória
# → Inútil para dados reais

spring.jpa.hibernate.ddl-auto=update
# → Tenta atualizar o schema automaticamente
# → Parece bom mas é PERIGOSO — veja abaixo
# → Nunca use em produção

spring.jpa.hibernate.ddl-auto=validate
# → Apenas verifica se o schema bate com as entidades
# → Não cria nem altera nada
# → Útil em produção para detectar inconsistências

spring.jpa.hibernate.ddl-auto=none
# → Não faz NADA com o banco
# → Você controla 100% via Flyway
# → Padrão recomendado em produção
```

### Por que o `ddl-auto=update` é perigoso?

```
CENÁRIO REAL:
Você tem a entidade Usuario com o campo "email".
No banco, a coluna se chama "email".
Você refatora e renomeia o campo para "emailUsuario".

COM ddl-auto=update:
→ Hibernate cria a coluna "email_usuario" (nova)
→ Deixa a coluna "email" (antiga) intacta
→ Todos os dados antigos ficam na coluna "email"
→ A coluna "email_usuario" começa vazia
→ Resultado: aplicação em produção sem dados de email
→ Rollback impossível sem backup manual

COM Flyway:
→ Você escreve a migration:
   ALTER TABLE usuarios RENAME COLUMN email TO email_usuario;
→ Flyway executa exatamente isso
→ Os dados são MIGRADOS junto com a coluna
→ Todos os ambientes executam exatamente o mesmo SQL
→ Rollback possível com uma migration de rollback
```

### Comparação direta: ddl-auto=update vs Flyway

| Critério                   | ddl-auto=update                              | Flyway                                  |
| -------------------------- | -------------------------------------------- | --------------------------------------- |
| **Colunas novas**          | Adiciona automaticamente ✅                  | Você escreve o ALTER TABLE ⚠️           |
| **Colunas removidas**      | NÃO remove ❌                                | Você controla ✅                        |
| **Renomear coluna**        | Cria nova, deixa antiga cheia de dados ❌    | RENAME COLUMN migra os dados ✅         |
| **Índices**                | Ignora completamente ❌                      | Você cria como quiser ✅                |
| **Dados iniciais**         | Não tem como inserir ❌                      | INSERT nos scripts de migration ✅      |
| **Histórico de mudanças**  | Zero — ninguém sabe o que mudou ❌           | Versionado, rastreável, em Git ✅       |
| **Ambientes consistentes** | Cada desenvolvedor tem um banco diferente ❌ | Todos executam os mesmos scripts ✅     |
| **Rollback**               | Impossível sem backup ❌                     | Migration de rollback ou Flyway Undo ✅ |
| **Code review**            | Ninguém revisa mudanças no banco ❌          | PR contém o SQL — revisável ✅          |
| **Uso em produção**        | Absolutamente NÃO ❌                         | Padrão da indústria ✅                  |

### Como o Flyway funciona internamente

```
Aplicação sobe pela primeira vez
     ↓
Flyway verifica se a tabela flyway_schema_history existe
→ Não existe → cria a tabela automaticamente
     ↓
Flyway escaneia classpath:db/migration
→ Encontra: V1, V2, V3, V4
     ↓
Compara com flyway_schema_history
→ Tabela vazia → nenhuma migration executada
     ↓
Executa V1 → registra na tabela com checksum
Executa V2 → registra na tabela com checksum
Executa V3 → registra na tabela com checksum
Executa V4 → registra na tabela com checksum
     ↓
Aplicação continua inicializando normalmente


Aplicação sobe pela segunda vez
     ↓
Flyway verifica flyway_schema_history
→ V1, V2, V3, V4 já executadas
→ Calcula checksum de cada arquivo
→ Compara com o checksum salvo
→ Todos iguais → sem mudanças → não faz nada
     ↓
Aplicação inicializa normalmente


Novo desenvolvedor entra no time
     ↓
Clona o repositório
→ As migrations estão no código
     ↓
Sobe o banco do zero
     ↓
Flyway executa todas as migrations em ordem
→ Schema idêntico ao de todos os outros ambientes
     ↓
Trabalha imediatamente sem precisar de dump do banco
```

### A tabela flyway_schema_history

```sql
-- O Flyway cria e gerencia essa tabela automaticamente
-- Você nunca edita ela manualmente

SELECT * FROM flyway_schema_history;

-- Resultado:
-- installed_rank | version | description              | type | script                        | checksum    | success
-- 1              | 1       | create usuarios          | SQL  | V1__create_usuarios.sql       | -1234567890 | true
-- 2              | 2       | create usuario roles     | SQL  | V2__create_usuario_roles.sql  | 987654321   | true
-- 3              | 3       | create refresh tokens    | SQL  | V3__create_refresh_tokens.sql | 456789123   | true
-- 4              | 4       | insert roles iniciais    | SQL  | V4__insert_roles_iniciais.sql | 789123456   | true
```

### Regra de ouro — NUNCA edite uma migration executada

```
SITUAÇÃO:
Você criou V1__create_usuarios.sql e subiu para produção.
Descobriu um erro — esqueceu de adicionar índice no email.

ERRADO ❌:
Editar V1__create_usuarios.sql adicionando o índice.

POR QUÊ É ERRADO:
O Flyway calcula o checksum do arquivo ao executar.
Salva esse checksum em flyway_schema_history.
Na próxima inicialização, recalcula e compara.
Se divergir → lança FlywayException e aborta o startup.
A aplicação não sobe em NENHUM ambiente até resolver.

CERTO ✅:
Criar V5__add_index_email_usuarios.sql:

CREATE INDEX idx_usuarios_email ON usuarios(email);

Esse arquivo não existia quando V1 foi executado.
O Flyway executa apenas V5 nos ambientes que ainda não têm.
V1 permanece intocado com seu checksum original.
```

### Nossas migrations — o que cada uma faz

```sql
-- V1__create_usuarios.sql
-- Cria a tabela principal de usuários
CREATE TABLE usuarios (
    id      VARCHAR(36)  NOT NULL PRIMARY KEY,  -- UUID gerado pelo Java
    email   VARCHAR(255) NOT NULL UNIQUE,        -- único, não nulo
    senha   VARCHAR(255) NOT NULL,               -- hash BCrypt (~60 chars)
    ativo   BOOLEAN      NOT NULL DEFAULT true,  -- soft delete
    criado_em    TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL
);

-- V2__create_usuario_roles.sql
-- Tabela gerada pelo @ElementCollection da entidade Usuario
-- Cada linha = uma role de um usuário
CREATE TABLE usuario_roles (
    usuario_id VARCHAR(36) NOT NULL
        REFERENCES usuarios(id) ON DELETE CASCADE,
    role       VARCHAR(50) NOT NULL,
    PRIMARY KEY (usuario_id, role)  -- impede roles duplicadas
);

-- V3__create_refresh_tokens.sql
-- Tokens de renovação de sessão
CREATE TABLE refresh_tokens (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    token      VARCHAR(36)  NOT NULL UNIQUE,  -- UUID aleatório
    usuario_id VARCHAR(36)  NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    expiracao  TIMESTAMP    NOT NULL,
    revogado   BOOLEAN      NOT NULL DEFAULT false
);

-- V4__insert_roles_iniciais.sql
-- Dados iniciais para teste — usuário admin
INSERT INTO usuarios (id, email, senha, ativo, criado_em, atualizado_em)
VALUES (
    'admin-uuid-fixo',
    'admin@tribunalbank.com',
    '$2a$10$hash_do_BCrypt_de_admin123',  -- senha: admin123
    true,
    NOW(),
    NOW()
);

INSERT INTO usuario_roles (usuario_id, role)
VALUES ('admin-uuid-fixo', 'ROLE_ADMIN');
```

---

## 5. Fluxo de desenvolvimento — fase a fase com arquivos

Esta é a ordem que um desenvolvedor sênior segue. A ordem importa porque cada fase depende da anterior.

---

### FASE 1 — Planejamento (sem código)

Antes de escrever uma linha de código, defina:

```
1. O que o serviço faz?
   → Autenticar usuários, emitir JWT, gerenciar sessões

2. Quais são os endpoints?
   → POST /auth/register
   → POST /auth/login
   → POST /auth/refresh
   → POST /auth/logout

3. Quais são as entidades?
   → Usuario (id, email, senha, ativo, roles, timestamps)
   → RefreshToken (id, token, usuario, expiracao, revogado)
   → Role (enum: ROLE_USER, ROLE_ADMIN)

4. Quais são as regras de negócio?
   → Email não pode ser duplicado
   → Senha precisa de no mínimo 8 caracteres
   → Access token expira em 15 minutos
   → Refresh token expira em 7 dias
   → Refresh token pode ser revogado (logout)
```

---

### FASE 2 — Banco de dados (Flyway migrations)

**Por que começar pelo banco?**
O banco é a fundação. Se você começar pelas entidades Java, pode criar uma estrutura que não funciona bem no banco relacional. SQL primeiro → Java depois.

```
auth-service/src/main/resources/db/migration/
├── V1__create_usuarios.sql
├── V2__create_usuario_roles.sql
├── V3__create_refresh_tokens.sql
└── V4__insert_roles_iniciais.sql
```

**O que verificar antes de avançar:**

```bash
# Sobe a aplicação e verifica se as tabelas foram criadas
docker exec -it auth-db psql -U postgres -d auth_db -c "\dt"

# Deve mostrar:
# usuarios, usuario_roles, refresh_tokens, flyway_schema_history
```

---

### FASE 3 — Entidades JPA

**Por que depois do banco?**
As entidades espelham as tabelas. Criar depois do SQL garante que o mapeamento está correto.

```
auth-service/src/main/java/com/tribunalbank/auth/entity/
├── Role.java              ← enum primeiro (sem dependências)
├── Usuario.java           ← usa Role
└── RefreshToken.java      ← usa Usuario
```

**Role.java** — enum que representa as permissões do sistema:

```java
public enum Role {
    ROLE_USER,    // usuário comum
    ROLE_ADMIN,   // administrador
    ROLE_GERENTE  // gerente (futuro)
}
// Por que ROLE_ no prefixo?
// Spring Security espera esse padrão para hasRole() funcionar:
// hasRole('ADMIN') internamente vira ROLE_ADMIN
```

**Usuario.java** — entidade principal:

```java
@Entity
@Table(name = "usuarios")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha; // hash BCrypt — nunca a senha real

    @Column(nullable = false)
    private boolean ativo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_roles",
                     joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role") // ← nome da coluna no banco (singular)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime atualizadoEm;
}
```

**RefreshToken.java** — token de renovação de sessão:

```java
@Entity
@Table(name = "refresh_tokens")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String token; // UUID aleatório — NÃO é um JWT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime expiracao;

    @Column(nullable = false)
    private boolean revogado;
}
```

---

### FASE 4 — Repositories

**Por que antes do service?**
Os repositories são injetados nos services. Criar antes para poder referenciar.

```
auth-service/src/main/java/com/tribunalbank/auth/repository/
├── UsuarioRepository.java
└── RefreshTokenRepository.java
```

**UsuarioRepository.java:**

```java
public interface UsuarioRepository extends JpaRepository<Usuario, String> {
    // Spring Data gera o SQL automaticamente pelo nome do método:
    // SELECT * FROM usuarios WHERE email = ? LIMIT 1
    Optional<Usuario> findByEmail(String email);

    // SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
    // FROM usuarios WHERE email = ?
    boolean existsByEmail(String email);
}
```

**RefreshTokenRepository.java:**

```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    // Busca por UUID do token (não pelo ID da entidade)
    Optional<RefreshToken> findByToken(String token);

    // Deleta todos os tokens de um usuário (limpa sessões antigas)
    void deleteByUsuario(Usuario usuario);

    // Conta tokens ativos (útil para limitar sessões simultâneas - V2)
    long countByUsuarioAndRevogadoFalse(Usuario usuario);
}
```

---

### FASE 5 — DTOs (Data Transfer Objects)

**Por que DTOs e não usar a entidade diretamente?**

- Entidade tem campos sensíveis (senha hash) que não devem ir para o cliente
- Request e Response têm formatos diferentes da entidade
- Validação fica no DTO — não na entidade
- Mudanças na entidade não quebram o contrato da API

```
auth-service/src/main/java/com/tribunalbank/auth/dto/
├── RegisterRequest.java   ← entrada do register
├── LoginRequest.java      ← entrada do login
├── RefreshRequest.java    ← entrada do refresh
└── AuthResponse.java      ← saída de register, login e refresh
```

**RegisterRequest.java:**

```java
public record RegisterRequest(
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    String email,

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
    String senha
) {}
// Por que record?
// Immutable por padrão, getters automáticos, sem Lombok necessário
// Ideal para DTOs que só carregam dados
```

**AuthResponse.java:**

```java
public record AuthResponse(
    String accessToken,
    String refreshToken,   // null no endpoint /auth/refresh
    String tokenType,      // sempre "Bearer"
    long expiresIn         // segundos até expirar (ex: 900)
) {}
```

---

### FASE 6 — Exceptions

**Por que criar antes dos services?**
Os services precisam lançar exceções específicas. Se criar o service antes, você usaria `RuntimeException` genérica e depois teria que refatorar.

```
auth-service/src/main/java/com/tribunalbank/auth/exception/
├── BusinessException.java           ← exceção base (pai de todas)
├── EmailJaCadastradoException.java  ← estende BusinessException
├── UsuarioNotFoundException.java    ← estende BusinessException
├── TokenInvalidoException.java      ← estende BusinessException
└── GlobalExceptionHandler.java      ← captura todas as exceções
```

**BusinessException.java — a base:**

```java
public class BusinessException extends RuntimeException {
    public BusinessException(String mensagem) {
        super(mensagem);
    }
}
// Por que RuntimeException e não Exception?
// RuntimeException = unchecked → não precisa de try/catch obrigatório
// Spring intercepta automaticamente via @ControllerAdvice
// checked Exception forçaria try/catch em cada service — verboso
```

**EmailJaCadastradoException.java:**

```java
public class EmailJaCadastradoException extends BusinessException {
    public EmailJaCadastradoException(String email) {
        super("Email já cadastrado: " + email);
    }
}
```

**GlobalExceptionHandler.java:**

```java
@RestControllerAdvice  // = @ControllerAdvice + @ResponseBody
public class GlobalExceptionHandler {

    // Captura exceção específica → retorna 409
    @ExceptionHandler(EmailJaCadastradoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroResponse handleEmailJaCadastrado(EmailJaCadastradoException ex) {
        return new ErroResponse(ex.getMessage(), HttpStatus.CONFLICT.value());
    }

    // Captura token inválido → retorna 401
    @ExceptionHandler(TokenInvalidoException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErroResponse handleTokenInvalido(TokenInvalidoException ex) {
        return new ErroResponse(ex.getMessage(), 401);
    }

    // Captura validação de campos (@Valid) → retorna 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResponse handleValidacao(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return new ErroResponse(mensagem, 400);
    }

    // Captura qualquer outra exceção → retorna 500
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErroResponse handleGenerico(Exception ex) {
        return new ErroResponse("Erro interno do servidor", 500);
        // Não expõe detalhes internos ao cliente — segurança
    }
}

// DTO de resposta de erro — formato padronizado
record ErroResponse(String mensagem, LocalDateTime timestamp, int status) {
    ErroResponse(String mensagem, int status) {
        this(mensagem, LocalDateTime.now(), status);
    }
}
```

---

### FASE 7 — Configurações

**Por que antes dos services?**
Os services precisam injetar `PasswordEncoder`, `JwtEncoder` etc que vêm das configurações.

```
auth-service/src/main/java/com/tribunalbank/auth/config/
├── JpaAuditingConfig.java    ← habilita @CreatedDate/@LastModifiedDate
├── PemKeyParser.java         ← utilitário de conversão de chaves RSA
├── JwtConfig.java            ← beans JwtEncoder e JwtDecoder
├── SecurityConfig.java       ← duas filter chains (pública e protegida)
└── SwaggerConfig.java        ← personalização da documentação OpenAPI
```

**JpaAuditingConfig.java:**

```java
@Configuration
@EnableJpaAuditing  // ← ativa o sistema de auditoria do Spring Data
public class JpaAuditingConfig {
    // Sem essa anotação, @CreatedDate e @LastModifiedDate não funcionam
    // Esse é um erro comum: esquecer @EnableJpaAuditing
    // Os campos ficariam null mesmo com as anotações na entidade
}
```

**PemKeyParser.java** — separado do JwtConfig (SRP):

```java
// Responsabilidade única: converter String PEM → RSAKey Java
// Separado porque JwtConfig é sobre configuração de beans Spring
// PemKeyParser é sobre transformação de dados — responsabilidades diferentes
final class PemKeyParser {
    private PemKeyParser() {} // não instanciável

    static RSAPublicKey parsePublicKey(String pem) throws Exception {
        String base64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\n", "").replace("\n", "")
            .replaceAll("\\s+", "").trim();
        byte[] decoded = Base64.getDecoder().decode(base64);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(decoded));
    }

    static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "").replace("\n", "")
            .replaceAll("\\s+", "").trim();
        byte[] decoded = Base64.getDecoder().decode(base64);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
}
```

---

### FASE 8 — Services

**Por que depois das configurações?**
Services injetam `PasswordEncoder`, `JwtEncoder`, repositories etc. Tudo isso deve existir primeiro.

```
auth-service/src/main/java/com/tribunalbank/auth/service/
├── UsuarioDetailsService.java   ← implementa UserDetailsService do Spring Security
├── JwtService.java              ← gera e lê tokens JWT
├── RefreshTokenService.java     ← cria, valida e revoga refresh tokens
└── AuthService.java             ← orquestra o fluxo completo (usa os outros)
```

**UsuarioDetailsService.java** — ponte entre Spring Security e seu banco:

```java
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository repository;

    // Spring Security chama esse método durante a autenticação
    // Recebe o "username" — no nosso caso é o email
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return repository.findByEmail(email)
            .map(usuario -> User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenha())      // hash BCrypt
                .roles(usuario.getRoles().stream()
                    .map(Role::name)
                    .toArray(String[]::new))
                .accountLocked(!usuario.isAtivo()) // conta bloqueada se ativo=false
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));
    }
}
```

**JwtService.java** — gera e lê tokens:

```java
@Service
public class JwtService {

    private final JwtEncoder encoder;

    @Value("${jwt.access-token-expiration}")
    private long expiracaoMs;

    public String gerarAccessToken(Usuario usuario) {
        Instant agora = Instant.now();

        // Claims são as "afirmações" dentro do token
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("auth-service")                    // quem emitiu
            .issuedAt(agora)                           // quando foi emitido
            .expiresAt(agora.plusMillis(expiracaoMs))  // quando expira
            .subject(usuario.getEmail())                // identificação do usuário (claim "sub")
            .claim("roles", usuario.getRoles()          // permissões do usuário
                .stream().map(Role::name).toList())
            .build();

        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public String extrairEmail(Jwt jwt) {
        return jwt.getSubject(); // claim "sub"
    }

    public List<String> extrairRoles(Jwt jwt) {
        return jwt.getClaim("roles");
    }
}
```

**RefreshTokenService.java:**

```java
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    @Value("${jwt.refresh-token-expiration}")
    private long expiracaoMs;

    @Transactional
    public RefreshToken criar(Usuario usuario) {
        // Revoga tokens anteriores do usuário (opcional — uma sessão por vez)
        repository.deleteByUsuario(usuario);

        return repository.save(RefreshToken.builder()
            .token(UUID.randomUUID().toString()) // UUID aleatório — NÃO é JWT
            .usuario(usuario)
            .expiracao(LocalDateTime.now().plusSeconds(expiracaoMs / 1000))
            .revogado(false)
            .build());
    }

    public RefreshToken validar(String token) {
        RefreshToken refreshToken = repository.findByToken(token)
            .orElseThrow(() -> new TokenInvalidoException("Refresh token não encontrado"));

        if (refreshToken.isRevogado()) {
            throw new TokenInvalidoException("Refresh token foi revogado (logout realizado)");
        }

        if (refreshToken.getExpiracao().isBefore(LocalDateTime.now())) {
            throw new TokenInvalidoException("Refresh token expirado — faça login novamente");
        }

        return refreshToken;
    }

    @Transactional
    public void revogar(String token) {
        repository.findByToken(token).ifPresent(rt -> {
            rt.setRevogado(true);
            repository.save(rt);
        });
    }
}
```

**AuthService.java** — orquestra tudo:

```java
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;

    @Transactional
    public AuthResponse registrar(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException(request.email());
        }

        Usuario usuario = Usuario.builder()
            .email(request.email())
            .senha(passwordEncoder.encode(request.senha())) // BCrypt aqui
            .ativo(true)
            .roles(Set.of(Role.ROLE_USER)) // role padrão no registro
            .build();

        usuarioRepository.save(usuario);

        String accessToken = jwtService.gerarAccessToken(usuario);
        RefreshToken refreshToken = refreshTokenService.criar(usuario);

        return new AuthResponse(accessToken, refreshToken.getToken(), "Bearer",
                                expiracaoEmSegundos);
    }

    public AuthResponse autenticar(LoginRequest request) {
        // Spring Security valida email + senha automaticamente aqui
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.senha())
        );

        Usuario usuario = usuarioRepository.findByEmail(request.email())
            .orElseThrow(() -> new UsuarioNotFoundException(request.email()));

        String accessToken = jwtService.gerarAccessToken(usuario);
        RefreshToken refreshToken = refreshTokenService.criar(usuario);

        return new AuthResponse(accessToken, refreshToken.getToken(), "Bearer",
                                expiracaoEmSegundos);
    }

    public AuthResponse renovarToken(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenService.validar(refreshTokenStr);
        String novoAccessToken = jwtService.gerarAccessToken(refreshToken.getUsuario());

        return new AuthResponse(novoAccessToken, null, "Bearer", expiracaoEmSegundos);
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenService.revogar(refreshTokenStr);
    }
}
```

---

### FASE 9 — Controllers

**Por que por último?**
O controller é apenas a fachada — recebe a requisição HTTP e delega para o service. Criar por último porque depende de tudo.

```
auth-service/src/main/java/com/tribunalbank/auth/controller/
└── AuthController.java
```

**AuthController.java:**

```java
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Registro, login e gerenciamento de tokens")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra novo usuário",
               description = "Cria conta e retorna tokens de acesso")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.registrar(request);
        // @Valid → ativa validação do DTO antes de entrar no método
        // Se inválido → MethodArgumentNotValidException → GlobalExceptionHandler → 400
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica usuário existente")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.autenticar(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova access token usando refresh token")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.renovarToken(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoga refresh token (invalida a sessão)")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
```

---

### FASE 10 — Testes

**Ver seção 12 para detalhes completos sobre como escrever os testes.**

```
auth-service/src/test/java/com/tribunalbank/auth/
├── service/
│   ├── AuthServiceTest.java           ← testes unitários
│   ├── JwtServiceTest.java
│   └── RefreshTokenServiceTest.java
├── controller/
│   └── AuthControllerTest.java        ← testes de integração
└── repository/
    └── UsuarioRepositoryTest.java      ← testes de repositório (H2)
```

---

## 6. Chaves RSA — por que não HMAC

### O que é criptografia assimétrica

RSA funciona com **dois valores matemáticos relacionados** chamados par de chaves. A relação entre eles é baseada em um problema matemático que computadores demorariam bilhões de anos para resolver (fatoração de números primos grandes):

```
CHAVE PRIVADA → guarda com você, NUNCA compartilha
                 usada para ASSINAR

CHAVE PÚBLICA → distribui para quem precisar
                 usada para VERIFICAR
                 inútil para gerar tokens falsos
```

**A propriedade fundamental:**

```
Qualquer coisa assinada com a PRIVADA
pode ser verificada com a PÚBLICA.

Mas é matematicamente impossível:
→ Descobrir a PRIVADA a partir da PÚBLICA
→ Gerar uma assinatura válida sem a PRIVADA
```

### HMAC (HS256) — o jeito ingênuo

A maioria dos tutoriais de JWT usa HMAC com uma `secret_key` única. Funciona em projetos pequenos, mas falha em microsserviços:

```
Sistema com 5 microsserviços usando HMAC:

Auth Service    → tem a secret_key (para gerar e verificar)
Cliente Service → precisa verificar token → precisa da secret_key
Conta Service   → precisa verificar → mesma secret_key
Transação       → precisa verificar → mesma secret_key
Relatório       → precisa verificar → mesma secret_key

RESULTADO: 5 serviços diferentes têm a MESMA chave secreta.

RISCO:
Se o Relatório Service for comprometido (vulnerabilidade, dev mal-intencionado):
→ Atacante tem a secret_key
→ Pode gerar tokens para QUALQUER usuário (inclusive admin)
→ Pode fazer qualquer operação no sistema como qualquer pessoa
→ Sem deixar rastro — o token parece legítimo
```

### RSA (RS256) — o jeito profissional

```
Sistema com 5 microsserviços usando RSA:

Auth Service    → tem PRIVADA + PÚBLICA (gera e verifica)
Cliente Service → tem SÓ A PÚBLICA (só verifica)
Conta Service   → tem SÓ A PÚBLICA (só verifica)
Transação       → tem SÓ A PÚBLICA (só verifica)
Relatório       → tem SÓ A PÚBLICA (só verifica)

RESULTADO: apenas Auth Service tem o poder de gerar tokens.

RISCO MINIMIZADO:
Se o Relatório Service for comprometido:
→ Atacante tem SÓ a chave pública
→ Pode verificar tokens (já pode fazer isso publicamente)
→ NÃO pode gerar tokens falsos
→ O sistema continua seguro

Só se o AUTH SERVICE for comprometido → problema real.
E o Auth Service é o mais protegido de todos.
```

### O tamanho da chave importa

```
RSA 1024 bits → INSEGURO — computadores modernos conseguem quebrar
RSA 2048 bits → PADRÃO ATUAL — seguro para uso geral (usamos isso)
RSA 4096 bits → MUITO SEGURO — mais lento, usado em dados muito sensíveis
RSA 8192 bits → EXTREMAMENTE SEGURO — impraticável para a maioria dos casos

A matemática: para quebrar RSA 2048 bits com os computadores atuais,
levaria mais tempo que a idade do universo (~13,8 bilhões de anos).
Com computadores quânticos do futuro, o cenário pode mudar → RS512 ou ECDSA.
```

### Como nossas chaves foram geradas

```java
// GerarChaves.java — executado UMA VEZ para gerar o par
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);  // tamanho em bits
KeyPair par = generator.generateKeyPair();

// Chave privada — salva em formato PKCS#8 (padrão Java)
PrivateKey privateKey = par.getPrivate();
// Começa com: -----BEGIN PRIVATE KEY-----

// Chave pública — salva em formato X.509
PublicKey publicKey = par.getPublic();
// Começa com: -----BEGIN PUBLIC KEY-----
```

### Onde as chaves ficam em cada ambiente

```
DESENVOLVIMENTO LOCAL:
→ application-dev.properties (com \n literal entre as linhas)
→ Variáveis de ambiente do IntelliJ
→ NÃO vai para o Git (.gitignore inclui *-dev.properties)

PRODUÇÃO (AWS):
→ AWS Secrets Manager → referenciado como variável de ambiente
→ O Spring lê a variável → PemKeyParser converte → JwtConfig usa
→ A chave nunca aparece em logs ou código

PRODUÇÃO (Kubernetes):
→ Kubernetes Secret → montado como variável de ambiente ou arquivo
→ Mesma abordagem

O QUE NUNCA FAZER:
→ Commitar as chaves no Git (nem privada, nem pública no repositório de produção)
→ Colocar as chaves em variáveis de ambiente visíveis em CI/CD logs
→ Hardcodar as chaves no código Java
→ Compartilhar a chave privada com outros times
```

---

## 7. Access Token e Refresh Token — os riscos de fazer errado

### Cenário 1 — apenas um token de longa duração (ERRADO)

Muitos tutoriais mostram apenas um token JWT que dura 24 horas ou 30 dias.

```
FLUXO:
Usuário faz login → recebe token de 30 dias
Usa o token por 30 dias em todas as requisições

PROBLEMA 1 — Token roubado:
Atacante intercepta a conexão (rede pública, hotel, café)
Captura o token
Token é válido por 30 dias → atacante acesso completo por 30 dias

JWT é STATELESS por design → servidor não guarda estado
Não tem como "invalidar" um JWT específico sem:
→ Trocar as chaves RSA → todos os tokens do sistema invalidados
→ Todos os usuários deslogados imediatamente
→ Inclui você mesmo, todos os admins, todos os clientes

PROBLEMA 2 — Usuário bloqueado:
Admin bloqueia usuário (fraude detectada)
Seta ativo=false no banco
O JWT ainda é válido por 30 dias → usuário continua acessando
O bloqueio não tem efeito imediato

PROBLEMA 3 — Mudança de permissão:
Usuário é promovido a ROLE_ADMIN
O JWT antigo tem ROLE_USER
A promoção só terá efeito quando o JWT expirar (30 dias)
```

### Cenário 2 — token de curta duração sem refresh (CHATO)

```
FLUXO:
Usuário faz login → recebe token de 5 minutos
Após 5 minutos → 401 Unauthorized
Precisa fazer login novamente

PROBLEMA:
Usuário usando o sistema por 1 hora → precisa logar 12 vezes
Usuário em reunião por 30 minutos → precisa logar ao voltar
Experiência horrível
```

### Cenário 3 — dois tokens (ACCESS + REFRESH) ← CORRETO

```
ACCESS TOKEN:
→ JWT assinado com RSA
→ Dura 15 minutos (900 segundos)
→ Stateless — servidor não armazena
→ Enviado em TODA requisição autenticada
→ Se roubado → expira em 15 minutos (janela de ataque pequena)
→ Contém: email do usuário, roles, emissão, expiração

REFRESH TOKEN:
→ UUID aleatório (não é JWT)
→ Dura 7 dias
→ Stateful — salvo na tabela refresh_tokens
→ Enviado APENAS para /auth/refresh
→ PODE SER REVOGADO — basta setar revogado=true no banco
→ Usuário bloqueado? Revogar o refresh token → logout imediato

COMBINAÇÃO:
→ Curta vida do access token limita a janela de ataque
→ Refresh token longo garante boa experiência
→ Refresh pode ser revogado → logout real funciona
→ Mudança de roles tem efeito em no máximo 15 minutos
```

### Por que o refresh token é UUID e não JWT?

```
SE FOSSE JWT:
→ Refresh token seria stateless (sem banco)
→ Não poderia ser revogado individualmente
→ Logout seria apenas "deletar do localStorage" — token ainda válido
→ Atacante com o refresh token pode renovar indefinidamente
→ Bloqueio de conta não teria efeito real

COMO UUID NO BANCO:
→ Logout = setar revogado=true na tabela
→ Próxima chamada para /auth/refresh → token não encontrado ou revogado → 401
→ Sessão encerrada de verdade no servidor
→ Atacante com o refresh token → sistema rejeita imediatamente
```

### Tabela de riscos

| Cenário de ataque                    | Um token longo          | Dois tokens                 |
| ------------------------------------ | ----------------------- | --------------------------- |
| Token roubado em trânsito            | Acesso por 30 dias ❌   | Acesso por até 15 min ✅    |
| Logout não funciona de verdade       | ❌ (só do localStorage) | ✅ (revoga no banco)        |
| Usuário bloqueado continua acessando | Por até 30 dias ❌      | Por até 15 min ✅           |
| Refresh token roubado                | N/A                     | Revogado no logout ✅       |
| Mudança de roles não tem efeito      | Por até 30 dias ❌      | Por até 15 min ✅           |
| Experiência do usuário               | Boa (1 login por mês)   | Boa (refresh automático) ✅ |

---

## 8. Como o front-end usa os tokens

### Onde guardar os tokens

```
localStorage:
→ Persiste entre abas e reloads
→ Acessível via JavaScript → vulnerável a XSS
→ Use apenas se o site tem Content Security Policy forte
→ NÃO RECOMENDADO para tokens de sessão

sessionStorage:
→ Morre quando a aba fecha
→ Acessível via JavaScript → ainda vulnerável a XSS
→ Mais seguro que localStorage para access token

httpOnly Cookie:
→ NÃO acessível via JavaScript (proteção contra XSS)
→ Enviado automaticamente pelo browser
→ MAIS SEGURO — padrão adotado por grandes empresas
→ Requer configuração de CORS e CSRF cuidadosa
→ Use para refresh token

Memória (variável JavaScript):
→ Extremamente seguro — não persiste
→ Perde no reload da página → precisa renovar via refresh token
→ Use para access token
```

**Estratégia recomendada (adotada por Google, GitHub, etc):**

```
accessToken  → memória (variável) — perde no reload, renovado automaticamente
refreshToken → httpOnly cookie — seguro, browser envia sozinho
```

### Implementação com Axios

```javascript
// auth.js — módulo de gerenciamento de tokens

let accessToken = null; // em memória — não persiste no reload

export function setAccessToken(token) {
    accessToken = token;
}

export function getAccessToken() {
    return accessToken;
}

export function clearTokens() {
    accessToken = null;
    // refreshToken está no cookie httpOnly — não precisa limpar aqui
}
```

```javascript
// api.js — configuração do Axios com interceptors

import axios from "axios";
import { getAccessToken, setAccessToken, clearTokens } from "./auth";

const api = axios.create({
    baseURL: "http://localhost:8081",
    withCredentials: true, // envia cookies httpOnly automaticamente
});

// INTERCEPTOR DE REQUEST — adiciona token em toda requisição
api.interceptors.request.use((config) => {
    const token = getAccessToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// INTERCEPTOR DE RESPONSE — renova token automaticamente ao expirar
let renovandoToken = false; // evita múltiplas renovações simultâneas
let filaDeEspera = []; // requisições aguardando nova token

api.interceptors.response.use(
    (response) => response, // sucesso → passa direto

    async (error) => {
        const requisicaoOriginal = error.config;

        // 401 = token expirado (não 403 = sem permissão)
        if (
            error.response?.status === 401 &&
            !requisicaoOriginal._tentouRenovar
        ) {
            requisicaoOriginal._tentouRenovar = true;

            // Se já está renovando, coloca na fila para refazer depois
            if (renovandoToken) {
                return new Promise((resolve, reject) => {
                    filaDeEspera.push({ resolve, reject });
                }).then(() => api(requisicaoOriginal));
            }

            renovandoToken = true;

            try {
                // Cookie httpOnly com refreshToken é enviado automaticamente
                const resposta = await api.post("/auth/refresh");
                const novoAccessToken = resposta.data.accessToken;

                setAccessToken(novoAccessToken);

                // Libera todas as requisições que estavam esperando
                filaDeEspera.forEach(({ resolve }) => resolve());
                filaDeEspera = [];

                // Refaz a requisição original com o novo token
                requisicaoOriginal.headers.Authorization = `Bearer ${novoAccessToken}`;
                return api(requisicaoOriginal);
            } catch (erroRenovacao) {
                // Refresh expirado ou revogado → logout forçado
                filaDeEspera.forEach(({ reject }) => reject(erroRenovacao));
                filaDeEspera = [];
                clearTokens();
                window.location.href = "/login";
                return Promise.reject(erroRenovacao);
            } finally {
                renovandoToken = false;
            }
        }

        return Promise.reject(error);
    },
);

export default api;
```

```javascript
// loginPage.js — uso na prática

import api from "./api";
import { setAccessToken } from "./auth";

async function login(email, senha) {
    const resposta = await api.post("/auth/login", { email, senha });

    // accessToken → salva em memória
    setAccessToken(resposta.data.accessToken);

    // refreshToken → o backend deve setar como cookie httpOnly
    // O browser guarda automaticamente — não aparece no código JavaScript
}

async function logout() {
    try {
        await api.post("/auth/logout"); // cookie é enviado automaticamente → revoga no servidor
    } finally {
        clearTokens(); // limpa memória
        window.location.href = "/login";
    }
}
```

---

## 9. Endpoints da API

Base URL: `http://localhost:8081`
Swagger UI: `http://localhost:8081/swagger-ui.html`

### POST /auth/register

**Request:**

```json
{
    "email": "joao@tribunalbank.com",
    "senha": "minhasenha123"
}
```

**Response 201 Created:**

```json
{
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "tokenType": "Bearer",
    "expiresIn": 900
}
```

**Erros:** `409` email duplicado | `400` campos inválidos

### POST /auth/login

**Request:**

```json
{
    "email": "joao@tribunalbank.com",
    "senha": "minhasenha123"
}
```

**Response 200 OK:** mesmo formato do register

**Erros:** `401` credenciais inválidas | `400` campos faltando

### POST /auth/refresh

**Request:**

```json
{
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response 200 OK:**

```json
{
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "refreshToken": null,
    "tokenType": "Bearer",
    "expiresIn": 900
}
```

**Erros:** `401` token inválido/expirado/revogado

### POST /auth/logout

**Header:** `Authorization: Bearer {accessToken}`

**Request:**

```json
{
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response 204 No Content**

---

## 10. Fluxos completos detalhados

### Fluxo 1 — Registro bem-sucedido

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 CLIENTE                       SPRING SECURITY               AUTH SERVICE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

POST /auth/register
{email, senha}
    │
    ▼
┌─────────────────────────────┐
│  publicFilterChain (@Order1)│
│  securityMatcher bate       │
│  Sem BearerTokenFilter      │
│  .permitAll() → passa direto│
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  AuthController.register()  │
│  @Valid valida o DTO        │
│  email formato ok?    ✓     │
│  senha >= 8 chars?    ✓     │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  AuthService.registrar()    │
│                             │
│  1. existsByEmail(email)    │
│     → SELECT count FROM     │
│       usuarios WHERE email  │
│     → false (não existe)    │
│                             │
│  2. BCrypt.encode(senha)    │
│     → $2a$10$xyz...         │
│     (100ms intencionalmente)│
│                             │
│  3. usuario = Usuario{      │
│     email, senhaHash, ativo │
│     roles: [ROLE_USER]      │
│     }                       │
│                             │
│  4. usuarioRepository.save()│
│     → INSERT INTO usuarios  │
│     → INSERT INTO roles     │
│     → Flyway garantiu que   │
│       as tabelas existem    │
│                             │
│  5. jwtService.gerar()      │
│     → Nimbus monta claims:  │
│       sub: email            │
│       roles: [ROLE_USER]    │
│       iat: agora            │
│       exp: agora + 15min    │
│     → RSA assina com PRIVADA│
│     → eyJhbGciOiJSUzI1NiJ9 │
│                             │
│  6. refreshTokenService     │
│     .criar(usuario)         │
│     → UUID.randomUUID()     │
│     → INSERT INTO           │
│       refresh_tokens        │
│       expiracao: +7 dias    │
│       revogado: false       │
│                             │
│  7. return AuthResponse     │
└─────────────┬───────────────┘
              │
              ▼
HTTP 201 Created
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "uuid-xyz",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### Fluxo 2 — Login bem-sucedido

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
POST /auth/login {email, senha}
    │
    ▼
publicFilterChain → .permitAll() → AuthController.login()
    │
    ▼
AuthService.autenticar()
    │
    ├─ authManager.authenticate(email, senha)
    │       │
    │       ▼
    │  Spring Security internamente:
    │  → UsuarioDetailsService.loadUserByUsername(email)
    │       → SELECT * FROM usuarios WHERE email = ?
    │       → Retorna UserDetails com hash BCrypt
    │  → BCrypt.matches(senhaDigitada, hashSalvo)
    │       → Recalcula o hash com o salt embutido
    │       → Compara byte a byte
    │       → true → autenticação OK
    │       → false → BadCredentialsException → 401
    │
    ├─ Authentication populado → continua
    │
    ├─ usuarioRepository.findByEmail(email)
    │  → Carrega entidade Usuario completa com roles
    │
    ├─ jwtService.gerarAccessToken(usuario)
    │  → Mesmo processo do registro
    │
    ├─ refreshTokenService.criar(usuario)
    │  → Revoga tokens anteriores do usuário (uma sessão por vez)
    │  → Gera novo UUID
    │  → Salva no banco
    │
    └─ return AuthResponse
    │
    ▼
HTTP 200 OK {accessToken, refreshToken, tokenType, expiresIn}
```

### Fluxo 3 — Requisição autenticada (qualquer endpoint protegido)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
GET /contas/123
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
    │
    ▼
┌────────────────────────────────────┐
│  publicFilterChain (@Order 1)      │
│  securityMatcher("/auth/**" etc)   │
│  NÃO bate com /contas/123          │
│  → passa para próxima chain        │
└──────────────────┬─────────────────┘
                   │
                   ▼
┌────────────────────────────────────┐
│  protectedFilterChain (@Order 2)   │
│  Sem securityMatcher → pega tudo   │
│                                    │
│  BearerTokenAuthenticationFilter   │
│  → Extrai "eyJhbGci..." do header  │
│  → Delega para NimbusJwtDecoder    │
│                                    │
│  NimbusJwtDecoder.decode(token):   │
│  1. Base64 decode o header         │
│     → {"alg":"RS256","typ":"JWT"}  │
│  2. Base64 decode o payload        │
│     → {"sub":"joao@...",           │
│         "roles":["ROLE_USER"],     │
│         "exp":1718803200}          │
│  3. Pega a assinatura              │
│  4. Recalcula a assinatura dos     │
│     bytes header.payload com       │
│     RSA + CHAVE PÚBLICA            │
│  5. Compara com a assinatura       │
│     recebida                       │
│  6. Verifica se exp > agora        │
│                                    │
│  ✓ Tudo OK:                        │
│  → Popula SecurityContext          │
│  → JwtAuthenticationToken com      │
│    todas as claims do payload      │
│  → .authenticated() satisfeito     │
└──────────────────┬─────────────────┘
                   │
                   ▼
ContaController.buscarConta(123)
→ @AuthenticationPrincipal Jwt jwt
→ jwt.getSubject() → "joao@tribunalbank.com"
→ Sabe quem está acessando
    │
    ▼
HTTP 200 OK com os dados da conta
```

### Fluxo 4 — Token expirado → renovação automática

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Após 15 minutos do último login:

GET /contas/123
Authorization: Bearer eyJhbGci... (EXPIRADO)
    │
    ▼
BearerTokenAuthenticationFilter
→ NimbusJwtDecoder.decode(token)
→ exp: 1718803200 < agora: 1718804100 ← EXPIRADO
→ JwtValidationException
→ 401 Unauthorized
    │
    ▼
FRONT-END interceptor de response detecta 401
    │
    ▼
POST /auth/refresh
{refreshToken: "uuid-xyz-..."}    (ou httpOnly cookie)
    │
    ▼
publicFilterChain → .permitAll()
    │
    ▼
RefreshTokenService.validar("uuid-xyz-...")
→ SELECT * FROM refresh_tokens WHERE token = ?
→ Encontrou: {revogado: false, expiracao: daqui 6 dias}
→ Token válido
    │
    ▼
jwtService.gerarAccessToken(token.getUsuario())
→ Novo JWT com expiração: agora + 15 minutos
→ Assina com chave privada RSA
    │
    ▼
HTTP 200 {accessToken: "eyJhbGci... NOVO"}
    │
    ▼
FRONT-END:
→ Salva novo accessToken em memória
→ Refaz GET /contas/123 com novo token
→ Usuário não percebeu nada — renovação transparente
```

### Fluxo 5 — Logout

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Usuário clica em "Sair":

POST /auth/logout
Authorization: Bearer eyJhbGci... (token do usuário)
{refreshToken: "uuid-xyz-..."}
    │
    ▼
protectedFilterChain → valida JWT → OK
    │
    ▼
RefreshTokenService.revogar("uuid-xyz-...")
→ UPDATE refresh_tokens
  SET revogado = true
  WHERE token = 'uuid-xyz-...'
    │
    ▼
HTTP 204 No Content
    │
    ▼
FRONT-END:
→ clearTokens() → accessToken = null
→ Redireciona para /login

RESULTADO:
→ Access token ainda "válido" por até 15 minutos
  (mas sem o refresh token revogado, não pode ser renovado)
→ Na prática: em 15 minutos o usuário está completamente deslogado
→ Se o sistema precisar de logout imediato:
  Implementar blacklist de tokens (Redis) — V2 do projeto
```

### Fluxo 6 — Registro com email duplicado

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
POST /auth/register {email: "existente@email.com", senha: "..."}
    │
    ▼
AuthService.registrar()
→ existsByEmail("existente@email.com")
→ SELECT count FROM usuarios WHERE email = ?
→ true (já existe)
    │
    ▼
throw new EmailJaCadastradoException("existente@email.com")
    │
    ▼
GlobalExceptionHandler.handleEmailJaCadastrado()
→ @ExceptionHandler(EmailJaCadastradoException.class)
→ @ResponseStatus(HttpStatus.CONFLICT)
    │
    ▼
HTTP 409 Conflict
{
  "mensagem": "Email já cadastrado: existente@email.com",
  "timestamp": "2026-06-19T15:57:46",
  "status": 409
}
```

### Fluxo 7 — Login com senha errada

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
POST /auth/login {email: "joao@...", senha: "senhaerrada"}
    │
    ▼
AuthService.autenticar()
→ authManager.authenticate(email, senhaerrada)
    │
    ▼
Spring Security internamente:
→ UsuarioDetailsService.loadUserByUsername(email)
→ Encontrou o usuário com hash BCrypt
→ BCrypt.matches("senhaerrada", "$2a$10$hash...")
→ false → senhas não batem
    │
    ▼
Spring lança BadCredentialsException
    │
    ▼
GlobalExceptionHandler.handleBadCredentials()
→ HTTP 401 Unauthorized
{
  "mensagem": "Credenciais inválidas",
  "timestamp": "2026-06-19T15:57:46",
  "status": 401
}

NOTA DE SEGURANÇA:
A mensagem NUNCA diz "email não encontrado" ou "senha incorreta".
Sempre "Credenciais inválidas" — não revela qual parte está errada.
Isso previne enumeração de usuários (saber quais emails existem).
```

---

## 11. Fluxo de erros

### Mapeamento de exceções

| Exceção                           | HTTP | Quando                                                      |
| --------------------------------- | ---- | ----------------------------------------------------------- |
| `EmailJaCadastradoException`      | 409  | Email já cadastrado                                         |
| `UsuarioNotFoundException`        | 404  | Usuário não encontrado                                      |
| `TokenInvalidoException`          | 401  | Refresh token inválido/expirado/revogado                    |
| `BadCredentialsException`         | 401  | Email ou senha incorretos                                   |
| `MethodArgumentNotValidException` | 400  | Campos de validação (@Valid) inválidos                      |
| `BusinessException`               | 400  | Regra de negócio violada (genérico)                         |
| `Exception`                       | 500  | Erro inesperado — log interno, mensagem genérica ao cliente |

### Formato padronizado de erro

```json
{
    "mensagem": "Email já cadastrado: joao@tribunalbank.com",
    "timestamp": "2026-06-19T15:57:46.9585892",
    "status": 409
}
```

---

## 12. Testes — unitários e de integração

Não implementamos testes ainda, mas esta seção mostra como fazer da forma correta.

### Por que testar?

```
SEM TESTES:
→ Muda o AuthService → não sabe se quebrou algo
→ Faz deploy → produção cai → reverte na correria
→ "Funciona na minha máquina" — clássico

COM TESTES:
→ Muda o AuthService → testes rodam em 2 segundos
→ Teste falhou → você sabe exatamente o que quebrou
→ Deploy com confiança
→ Código documentado em forma de testes (melhor que comentário)
```

### Tipos de teste

```
UNITÁRIO:
→ Testa UMA unidade isolada (uma classe/método)
→ Sem banco de dados (usa Mock)
→ Sem Spring Context (mais rápido)
→ Execução: milissegundos
→ Use: JUnit 5 + Mockito

INTEGRAÇÃO:
→ Testa múltiplas camadas juntas
→ Com banco real (H2 em memória ou Testcontainers com PostgreSQL)
→ Com Spring Context
→ Execução: segundos
→ Use: @SpringBootTest + MockMvc

END-TO-END:
→ Testa o sistema completo
→ Chamadas HTTP reais
→ Banco real
→ Execução: minutos
→ Use: RestAssured ou Postman/Newman
```

### Dependências para adicionar no pom.xml

```xml
<dependencies>
    <!-- JUnit 5 + Mockito — já incluídos pelo spring-boot-starter-test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers — banco PostgreSQL real nos testes de integração -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <!-- Versão já gerenciada pelo Spring Boot BOM -->
</dependencies>
```

### FASE 10-A — Testes unitários dos services

```java
// src/test/java/com/tribunalbank/auth/service/AuthServiceTest.java

@ExtendWith(MockitoExtension.class)  // ativa Mockito sem Spring — rápido
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authManager;

    @InjectMocks  // injeta os mocks acima no AuthService
    private AuthService authService;

    // ── REGISTRO ─────────────────────────────────────────

    @Test
    @DisplayName("Deve registrar usuário com sucesso")
    void deveRegistrarUsuarioComSucesso() {
        // GIVEN — preparação do cenário
        RegisterRequest request = new RegisterRequest("joao@test.com", "senha123");

        when(usuarioRepository.existsByEmail("joao@test.com"))
            .thenReturn(false); // email disponível

        when(passwordEncoder.encode("senha123"))
            .thenReturn("$2a$10$hashBCrypt");

        Usuario usuarioSalvo = Usuario.builder()
            .id("uuid-123")
            .email("joao@test.com")
            .senha("$2a$10$hashBCrypt")
            .ativo(true)
            .roles(Set.of(Role.ROLE_USER))
            .build();

        when(usuarioRepository.save(any(Usuario.class)))
            .thenReturn(usuarioSalvo);

        when(jwtService.gerarAccessToken(any(Usuario.class)))
            .thenReturn("eyJhbGci...accessToken");

        RefreshToken refreshToken = RefreshToken.builder()
            .token("uuid-refresh")
            .build();
        when(refreshTokenService.criar(any(Usuario.class)))
            .thenReturn(refreshToken);

        // WHEN — execução do que está sendo testado
        AuthResponse response = authService.registrar(request);

        // THEN — verificação do resultado
        assertNotNull(response);
        assertEquals("eyJhbGci...accessToken", response.accessToken());
        assertEquals("uuid-refresh", response.refreshToken());
        assertEquals("Bearer", response.tokenType());

        // Verifica que o save foi chamado uma vez
        verify(usuarioRepository, times(1)).save(any(Usuario.class));

        // Verifica que a senha foi encodada (nunca salva em texto puro)
        verify(passwordEncoder, times(1)).encode("senha123");
    }

    @Test
    @DisplayName("Deve lançar EmailJaCadastradoException quando email existe")
    void deveLancarExcecaoQuandoEmailJaExiste() {
        // GIVEN
        RegisterRequest request = new RegisterRequest("existente@test.com", "senha123");
        when(usuarioRepository.existsByEmail("existente@test.com")).thenReturn(true);

        // THEN + WHEN — verifica que a exceção é lançada
        assertThrows(
            EmailJaCadastradoException.class,
            () -> authService.registrar(request)
        );

        // Verifica que o save NUNCA foi chamado
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar BadCredentialsException com senha incorreta")
    void deveLancarExcecaoComSenhaIncorreta() {
        // GIVEN
        LoginRequest request = new LoginRequest("joao@test.com", "senhaerrada");
        when(authManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Credenciais inválidas"));

        // THEN + WHEN
        assertThrows(
            BadCredentialsException.class,
            () -> authService.autenticar(request)
        );
    }
}
```

### FASE 10-B — Testes unitários do JwtService

```java
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private JwtEncoder encoder;

    @InjectMocks
    private JwtService jwtService;

    @Test
    @DisplayName("Deve gerar access token com claims corretas")
    void deveGerarAccessTokenComClaimsCorretas() {
        // GIVEN
        Usuario usuario = Usuario.builder()
            .email("joao@test.com")
            .roles(Set.of(Role.ROLE_USER))
            .build();

        Jwt jwtMock = mock(Jwt.class);
        when(jwtMock.getTokenValue()).thenReturn("eyJhbGci...token");

        when(encoder.encode(any(JwtEncoderParameters.class)))
            .thenReturn(jwtMock);

        // WHEN
        String token = jwtService.gerarAccessToken(usuario);

        // THEN
        assertNotNull(token);
        assertEquals("eyJhbGci...token", token);

        // Verifica que o encoder foi chamado com os parâmetros corretos
        ArgumentCaptor<JwtEncoderParameters> captor =
            ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder).encode(captor.capture());

        JwtClaimsSet claims = captor.getValue().getClaims();
        assertEquals("joao@test.com", claims.getSubject());
        assertTrue(claims.<List<String>>getClaim("roles").contains("ROLE_USER"));
    }
}
```

### FASE 10-C — Testes de integração dos endpoints

```java
// src/test/java/com/tribunalbank/auth/controller/AuthControllerTest.java

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers  // usa Docker para subir PostgreSQL real
@Transactional   // cada teste roda em transação que é revertida no final
class AuthControllerTest {

    // Sobe um PostgreSQL real em Docker para os testes
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("auth_test")
        .withUsername("postgres")
        .withPassword("postgres");

    @DynamicPropertySource  // configura as properties dinamicamente
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── REGISTER ─────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/register deve retornar 201 com tokens")
    void deveRegistrarComSucesso() throws Exception {
        RegisterRequest request = new RegisterRequest("novo@test.com", "senha123456");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /auth/register deve retornar 409 com email duplicado")
    void deveRetornar409ComEmailDuplicado() throws Exception {
        // Cria o usuário primeiro
        RegisterRequest request = new RegisterRequest("duplicado@test.com", "senha123456");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Tenta criar de novo com mesmo email
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /auth/register deve retornar 400 com email inválido")
    void deveRetornar400ComEmailInvalido() throws Exception {
        RegisterRequest request = new RegisterRequest("nao-e-um-email", "senha123456");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensagem").value(containsString("Email inválido")));
    }

    @Test
    @DisplayName("POST /auth/login deve retornar 200 com credenciais corretas")
    void deveLoginComSucesso() throws Exception {
        // Registra o usuário
        RegisterRequest register = new RegisterRequest("login@test.com", "senha123456");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isCreated());

        // Faz o login
        LoginRequest login = new LoginRequest("login@test.com", "senha123456");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("POST /auth/login deve retornar 401 com senha errada")
    void deveRetornar401ComSenhaErrada() throws Exception {
        LoginRequest login = new LoginRequest("naoexiste@test.com", "senhaerrada");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isUnauthorized());
    }
}
```

### FASE 10-D — Testes de repositório

```java
@DataJpaTest  // sobe apenas a camada JPA com H2 em memória — muito rápido
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository repository;

    @Test
    @DisplayName("findByEmail deve retornar Optional com usuário")
    void deveBuscarPorEmail() {
        Usuario usuario = Usuario.builder()
            .email("test@test.com")
            .senha("hash")
            .ativo(true)
            .build();
        repository.save(usuario);

        Optional<Usuario> resultado = repository.findByEmail("test@test.com");

        assertTrue(resultado.isPresent());
        assertEquals("test@test.com", resultado.get().getEmail());
    }

    @Test
    @DisplayName("existsByEmail deve retornar true para email existente")
    void deveRetornarTrueParaEmailExistente() {
        repository.save(Usuario.builder()
            .email("existe@test.com").senha("hash").ativo(true).build());

        assertTrue(repository.existsByEmail("existe@test.com"));
        assertFalse(repository.existsByEmail("naoexiste@test.com"));
    }
}
```

### Como rodar os testes

```bash
# Todos os testes
./mvnw test

# Apenas testes unitários (sem @SpringBootTest)
./mvnw test -Dtest="*Test" -DexcludedGroups="integration"

# Apenas testes de integração
./mvnw test -Dgroups="integration"

# Com relatório de cobertura (JaCoCo)
./mvnw verify

# Relatório em: target/site/jacoco/index.html
```

---

## 13. Anotações importantes do Spring

### Segurança

```java
@EnableWebSecurity
// Ativa o módulo de segurança web do Spring.
// Sem ela, as configurações de HttpSecurity são ignoradas.
// Obrigatória em qualquer @Configuration que configure HttpSecurity.

@EnableMethodSecurity
// Ativa segurança em nível de método nos controllers e services.
// Permite:
// @PreAuthorize("hasRole('ADMIN')")           → antes do método
// @PostAuthorize("returnObject.id == #id")    → após o método
// @Secured("ROLE_ADMIN")                      → alternativa mais simples
// Essencial para controle fino de acesso além de rotas URL.

@Order(1) / @Order(2)
// Define prioridade entre múltiplas SecurityFilterChain.
// Spring avalia em ordem crescente.
// Chain com @Order(1) é avaliada primeiro.
// Se securityMatcher bater → usa essa chain → ignora as outras.
// Necessário quando há rotas públicas e protegidas separadas.

@PreAuthorize("hasRole('ADMIN')")
// Verifica permissão ANTES do método executar.
// Lança AccessDeniedException → 403 Forbidden se falhar.
// Permite regras baseadas em parâmetros:
// @PreAuthorize("#usuarioId == authentication.name")
// → usuário só acessa seus próprios dados
```

### Persistência JPA

```java
@ElementCollection(fetch = FetchType.EAGER)
// Mapeia coleção de tipos simples (String, Enum) sem criar entidade separada.
// Gera tabela auxiliar automaticamente (usuario_roles).
// EAGER → carrega junto com a entidade sempre.
// LAZY → carrega sob demanda (cuidado com LazyInitializationException).

@CollectionTable(name = "usuario_roles", joinColumns = @JoinColumn(name = "usuario_id"))
// Personaliza a tabela do @ElementCollection.
// name → nome da tabela no banco.
// joinColumns → FK que referencia o dono da coleção.

@Enumerated(EnumType.STRING)
// Salva Enum como String ("ROLE_USER") em vez de número (0, 1).
// NUNCA use EnumType.ORDINAL — reordenar o enum corromperia os dados.

@Column(name = "role")
// Define o nome da coluna no banco para o @ElementCollection.
// Sem isso, Hibernate usa o nome do campo Java (roles — plural, errado).

@EntityListeners(AuditingEntityListener.class)
// Ativa listener de auditoria. Necessário para @CreatedDate/@LastModifiedDate.
// Precisa de @EnableJpaAuditing em uma classe @Configuration.

@CreatedDate
// Preenchido automaticamente quando o registro é criado.
// Use com updatable = false para nunca ser alterado:
// @Column(nullable = false, updatable = false)

@LastModifiedDate
// Atualizado a cada .save() via JPA.
// NÃO atualiza em queries nativas (nativeQuery = true).
```

### Injeção e Configuração

```java
@Value("${jwt.private-key}")
// Injeta propriedade do application.properties.
// Com fallback: @Value("${jwt.expiration:900000}")
// Para tipos complexos (RSAPrivateKey), injete como String e converta.

@Bean
// Registra o retorno como bean Spring gerenciado.
// Nome do bean = nome do método (pode sobrescrever: @Bean("meuNome")).
// Singleton por padrão — uma instância compartilhada.

@Transactional
// Envolve o método em uma transação de banco de dados.
// Se o método lançar RuntimeException → rollback automático.
// Se completar normalmente → commit automático.
// Use em service methods que fazem múltiplas operações no banco.
```

### Controller e DTO

```java
@RestController
// = @Controller + @ResponseBody.
// Todos os métodos serializam o retorno para JSON automaticamente.

@Valid
// Ativa Bean Validation no parâmetro.
// Valida @NotBlank, @Email, @Size etc do DTO.
// Se falhar → MethodArgumentNotValidException → GlobalExceptionHandler → 400.

@AuthenticationPrincipal Jwt jwt
// Injeta o JWT do usuário autenticado no parâmetro.
// Disponível apenas em endpoints protegidos.
// jwt.getSubject() → email (claim "sub")
// jwt.getClaim("roles") → lista de roles
// Muito mais limpo que SecurityContextHolder.getContext().getAuthentication()

@RestControllerAdvice
// = @ControllerAdvice + @ResponseBody
// Centraliza tratamento de exceções de todos os controllers.
// Cada @ExceptionHandler mapeia um tipo de exceção para uma resposta HTTP.
```

---

## 14. Arquitetura de segurança — duas Filter Chains

### O problema com uma única chain

```java
// ISSO NÃO FUNCIONA COMO ESPERADO:
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/**").permitAll()
        .anyRequest().authenticated()
    )
    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

// MOTIVO:
// oauth2ResourceServer instala BearerTokenAuthenticationFilter
// Esse filtro roda ANTES das regras de authorizeHttpRequests
// Para qualquer requisição sem token → lança exceção imediatamente
// Mesmo que seja .permitAll()
// /auth/register → 401 sem token
```

### A solução com duas chains

```java
// Chain 1: pública — SEM BearerTokenFilter
@Bean @Order(1)
public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/auth/**", "/swagger-ui/**", "/api-docs/**", "/actuator/health")
        // securityMatcher limita ONDE essa chain se aplica
        // Fora desses padrões → não usa essa chain
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        // SEM .oauth2ResourceServer() → sem filtro JWT → funciona
    return http.build();
}

// Chain 2: protegida — COM BearerTokenFilter
@Bean @Order(2)
public SecurityFilterChain protectedFilterChain(HttpSecurity http) throws Exception {
    http
        // Sem securityMatcher → pega tudo que não casou na chain 1
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        // Aqui o BearerTokenFilter faz sentido — toda rota é protegida
    return http.build();
}
```

---

## 15. Estrutura de pacotes

```
auth-service/
│
├── src/main/java/com/tribunalbank/auth/
│   │
│   ├── AuthServiceApplication.java          # main() — ponto de entrada
│   │
│   ├── config/
│   │   ├── JpaAuditingConfig.java           # @EnableJpaAuditing
│   │   ├── PemKeyParser.java                # String PEM → RSAKey (package-private)
│   │   ├── JwtConfig.java                   # JwtEncoder + JwtDecoder beans
│   │   ├── SecurityConfig.java              # Duas filter chains
│   │   └── SwaggerConfig.java               # Personalização OpenAPI
│   │
│   ├── controller/
│   │   └── AuthController.java              # /auth/* endpoints
│   │
│   ├── dto/
│   │   ├── RegisterRequest.java             # record {email, senha}
│   │   ├── LoginRequest.java                # record {email, senha}
│   │   ├── RefreshRequest.java              # record {refreshToken}
│   │   └── AuthResponse.java                # record {accessToken, refreshToken, ...}
│   │
│   ├── entity/
│   │   ├── Role.java                        # enum ROLE_USER, ROLE_ADMIN
│   │   ├── Usuario.java                     # tabela: usuarios
│   │   └── RefreshToken.java                # tabela: refresh_tokens
│   │
│   ├── exception/
│   │   ├── BusinessException.java           # base (extends RuntimeException)
│   │   ├── EmailJaCadastradoException.java  # 409
│   │   ├── UsuarioNotFoundException.java    # 404
│   │   ├── TokenInvalidoException.java      # 401
│   │   └── GlobalExceptionHandler.java      # @RestControllerAdvice
│   │
│   ├── repository/
│   │   ├── UsuarioRepository.java           # JpaRepository<Usuario, String>
│   │   └── RefreshTokenRepository.java      # JpaRepository<RefreshToken, String>
│   │
│   └── service/
│       ├── UsuarioDetailsService.java       # implements UserDetailsService
│       ├── JwtService.java                  # gera e lê JWT
│       ├── RefreshTokenService.java         # cria, valida, revoga refresh tokens
│       └── AuthService.java                 # orquestra tudo
│
├── src/main/resources/
│   ├── application.properties               # comum a todos ambientes
│   ├── application-dev.properties           # desenvolvimento local
│   ├── application-prod.properties          # produção (gitignored)
│   └── db/migration/
│       ├── V1__create_usuarios.sql
│       ├── V2__create_usuario_roles.sql
│       ├── V3__create_refresh_tokens.sql
│       └── V4__insert_roles_iniciais.sql
│
└── src/test/java/com/tribunalbank/auth/
    ├── service/
    │   ├── AuthServiceTest.java             # @ExtendWith(MockitoExtension)
    │   ├── JwtServiceTest.java
    │   └── RefreshTokenServiceTest.java
    ├── controller/
    │   └── AuthControllerTest.java          # @SpringBootTest + @Testcontainers
    └── repository/
        └── UsuarioRepositoryTest.java        # @DataJpaTest
```

---

## 16. Tabela de decisões arquiteturais

| Decisão                              | Alternativa                  | Motivo                                                           |
| ------------------------------------ | ---------------------------- | ---------------------------------------------------------------- |
| **RSA (RS256)** para JWT             | HMAC (HS256)                 | Outros microsserviços verificam sem ter a chave privada          |
| **Dois tokens** (access + refresh)   | Um token de 30 dias          | Access: janela de ataque mínima. Refresh: pode ser revogado      |
| **Refresh token como UUID** no banco | Refresh token como JWT       | JWT não pode ser revogado; UUID no banco pode                    |
| **Duas SecurityFilterChain**         | Uma chain com permitAll      | BearerTokenFilter intercepta antes do permitAll em uma chain     |
| **Flyway** para migrations           | ddl-auto=update do Hibernate | Flyway versiona e é previsível; Hibernate é imprevisível em prod |
| **BCrypt strength 10**               | SHA-256, MD5                 | Projetado para ser lento — dificulta brute force                 |
| **UUID** como ID de entidades        | Long sequencial              | Não expõe volume de dados; impossível de adivinhar               |
| **@ElementCollection** para roles    | @ManyToMany com tabela Role  | Roles são controladas no código; tabela separada não agrega      |
| **PemKeyParser** separado            | Converter no JwtConfig       | SRP — conversão de dados ≠ configuração de beans                 |
| **Record** para DTOs                 | @Data do Lombok              | Imutável por padrão, sem boilerplate, Java nativo                |
| **BusinessException unchecked**      | checked Exception            | Não força try/catch — Spring captura via @ControllerAdvice       |
| **GlobalExceptionHandler** único     | try/catch em cada service    | DRY — tratamento centralizado, respostas consistentes            |

---

## 17. Spring Actuator — Health Check e Monitoramento

### O que é o Spring Actuator

O **Spring Actuator** é um módulo do Spring Boot que expõe endpoints HTTP de monitoramento e gerenciamento da aplicação. Ele permite que ferramentas externas — Eureka, load balancers, Kubernetes, Prometheus — perguntem para a aplicação: _"você está saudável? está funcionando?"_

É adicionado via dependência no `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### O endpoint /actuator/health

```
GET http://localhost:8081/actuator/health
```

Este é o endpoint mais importante. Responde com o **estado de saúde** da aplicação e de todas as suas dependências:

```json
{
    "status": "UP",
    "components": {
        "db": {
            "status": "UP",
            "details": {
                "database": "PostgreSQL",
                "validationQuery": "isValid()"
            }
        },
        "diskSpace": {
            "status": "UP",
            "details": {
                "total": 499963174912,
                "free": 412486246400,
                "threshold": 10485760,
                "exists": true
            }
        },
        "ping": {
            "status": "UP"
        }
    }
}
```

Os possíveis valores de `status` são:

| Status           | Significado                                 | HTTP Code |
| ---------------- | ------------------------------------------- | --------- |
| `UP`             | Tudo funcionando normalmente                | 200       |
| `DOWN`           | Alguma dependência falhou (banco caiu, etc) | 503       |
| `OUT_OF_SERVICE` | Aplicação fora de serviço intencionalmente  | 503       |
| `UNKNOWN`        | Estado indeterminado                        | 200       |

### Como o Eureka usa o health check

O Eureka (Discovery Server) chama `/actuator/health` periodicamente para saber se o serviço ainda está vivo:

```
Auth Service sobe
     ↓
Registra no Eureka: "estou na porta 8081"
     ↓
Eureka começa a monitorar:
→ A cada 30 segundos chama GET /actuator/health
→ status: UP → serviço continua no registry
→ status: DOWN → Eureka remove do registry
→ Requisições param de ser roteadas para este serviço
     ↓
Auth Service cai (crash, OOM, etc)
→ Health check falha 3 vezes consecutivas
→ Eureka remove do registry
→ Outros microsserviços não recebem mais referências para este serviço
→ Sistema se auto-recupera sem intervenção manual
```

### Como Kubernetes e load balancers usam

Em produção com Kubernetes, o health check tem dois tipos diferentes:

```yaml
# deployment.yaml do Kubernetes

livenessProbe:
    # "A aplicação está viva? Precisa reiniciar?"
    # Se falhar → Kubernetes mata o pod e sobe um novo
    httpGet:
        path: /actuator/health/liveness
        port: 8081
    initialDelaySeconds: 30 # espera 30s antes de começar a checar
    periodSeconds: 10 # checa a cada 10s
    failureThreshold: 3 # falha 3 vezes → reinicia

readinessProbe:
    # "A aplicação está pronta para receber tráfego?"
    # Se falhar → Kubernetes para de mandar requisições (mas não reinicia)
    # Útil durante deploy: nova versão sobe, mas só recebe tráfego quando pronta
    httpGet:
        path: /actuator/health/readiness
        port: 8081
    initialDelaySeconds: 10
    periodSeconds: 5
    failureThreshold: 3
```

**A diferença crucial:**

```
LIVENESS (está vivo?):
→ Banco caiu permanentemente → DOWN → reinicia o pod
→ Deadlock de threads → DOWN → reinicia o pod

READINESS (está pronto?):
→ Ainda carregando cache na inicialização → DOWN → não recebe tráfego ainda
→ Banco momentaneamente lento → DOWN → para de receber tráfego temporariamente
→ Banco volta → UP → volta a receber tráfego
```

### Configuração no nosso projeto

No `application.properties` configuramos assim:

```properties
# Quais endpoints do actuator ficam acessíveis via HTTP
management.endpoints.web.exposure.include=health,info

# Mostra os detalhes internos do health check
# always  → sempre mostra (dev)
# never   → nunca mostra (prod — não expõe informações internas)
# when-authorized → só para usuários autenticados
management.endpoint.health.show-details=always
```

**Por que não expor todos os endpoints em produção?**

O Actuator tem muitos endpoints além do health:

| Endpoint             | O que faz                              | Risco                                  |
| -------------------- | -------------------------------------- | -------------------------------------- |
| `/actuator/health`   | Estado de saúde                        | Baixo — expor em prod ✅               |
| `/actuator/info`     | Informações da aplicação               | Baixo — expor em prod ✅               |
| `/actuator/metrics`  | Métricas (CPU, memória, requests)      | Médio — expor apenas internamente      |
| `/actuator/env`      | **Todas as propriedades da aplicação** | **ALTO** — expõe senhas, chaves JWT ❌ |
| `/actuator/beans`    | Todos os beans Spring                  | Alto — expõe arquitetura interna ❌    |
| `/actuator/mappings` | Todos os endpoints da API              | Médio — facilita reconhecimento ❌     |
| `/actuator/loggers`  | Alterar nível de log em runtime        | Alto — pode revelar dados sensíveis ❌ |
| `/actuator/shutdown` | **Desliga a aplicação**                | **CRÍTICO** ❌                         |

**Em produção, exponha apenas `health` e `info`:**

```properties
# application-prod.properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never  # não expõe detalhes internos
```

### O endpoint /actuator/info

Retorna metadados da aplicação — útil para saber qual versão está em produção:

```properties
# application.properties
info.app.name=auth-service
info.app.description=Serviço de autenticação do Tribunal Bank
info.app.version=1.0.0
info.app.java-version=21
```

```json
// GET /actuator/info
{
    "app": {
        "name": "auth-service",
        "description": "Serviço de autenticação do Tribunal Bank",
        "version": "1.0.0",
        "java-version": "21"
    }
}
```

### Por que o /actuator/health está na lista de rotas públicas

No nosso `SecurityConfig.java`, o `/actuator/health` está explicitamente na `publicFilterChain`:

```java
.securityMatcher(
    "/auth/register",
    "/auth/login",
    "/auth/refresh",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/api-docs/**",
    "/actuator/health"   // ← aqui
)
```

**Motivo:** o Eureka e o Kubernetes precisam chamar este endpoint **sem precisar de token JWT**. Se estivesse protegido, o monitoramento quebraria — o Eureka não saberia se o serviço está vivo e removeria do registry, causando falha no sistema inteiro.

### Fluxo completo do health check no ecossistema

```
Auth Service sobe na porta 8081
     ↓
Spring Actuator disponibiliza:
GET /actuator/health → retorna {status: "UP"}
     ↓
Eureka Discovery Server (porta 8761)
→ Auth Service registrou: "estou em localhost:8081"
→ A cada 30s: GET http://localhost:8081/actuator/health
→ UP → mantém no registry
→ DOWN → remove do registry após 3 falhas
     ↓
API Gateway (porta 8080)
→ Pergunta para Eureka: "onde está o auth-service?"
→ Eureka responde: "localhost:8081 (healthy)"
→ Gateway roteia requisições para lá
     ↓
Se Auth Service cair:
→ Eureka detecta falha no health check
→ Remove do registry
→ API Gateway para de rotear para este serviço
→ Kubernetes (em produção) reinicia o pod automaticamente
→ Novo pod sobe, registra no Eureka, health check passa
→ Tráfego volta automaticamente
```

---

_Tribunal Bank API — Auth Service v1.0_
_Documentação de estudo — Junho 2026_
