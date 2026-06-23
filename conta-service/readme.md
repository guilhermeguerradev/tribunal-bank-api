# Conta Service — Documentação Completa de Estudo

> Serviço de gerenciamento de contas bancárias do **Tribunal Bank API**.
> Terceiro microsserviço do ecossistema — depende do Auth Service e do Cliente Service.
> Use este documento para estudar, revisar e entender cada decisão de arquitetura.

---

## Índice

1. [A ideia do Conta Service](#1-a-ideia-do-conta-service)
2. [Como pensamos para desenvolver](#2-como-pensamos-para-desenvolver)
3. [OpenFeign — o grande conceito novo](#3-openfeign--o-grande-conceito-novo)
4. [Logs com @Slf4j — como funciona de verdade](#4-logs-com-slf4j--como-funciona-de-verdade)
5. [Conhecimentos aplicados neste projeto](#5-conhecimentos-aplicados-neste-projeto)
6. [Stack e dependências](#6-stack-e-dependências)
7. [Como rodar localmente](#7-como-rodar-localmente)
8. [Fluxo de desenvolvimento — fase a fase](#8-fluxo-de-desenvolvimento--fase-a-fase)
9. [Endpoints da API](#9-endpoints-da-api)
10. [Fluxos completos detalhados](#10-fluxos-completos-detalhados)
11. [Fluxo de erros](#11-fluxo-de-erros)
12. [Checklist de conceitos implementados](#12-checklist-de-conceitos-implementados)
13. [Estrutura de pacotes](#13-estrutura-de-pacotes)
14. [Tabela de decisões arquiteturais](#14-tabela-de-decisões-arquiteturais)

---

## 1. A ideia do Conta Service

O **Conta Service** é o terceiro microsserviço do Tribunal Bank. Enquanto o Auth Service cuida de **quem pode entrar** e o Cliente Service cuida do **perfil pessoal**, o Conta Service cuida das **contas bancárias** — o produto central de qualquer banco.

### O que ele faz

```
→ Abre contas bancárias (CORRENTE, POUPANCA, SALARIO, INVESTIMENTO)
→ Garante que cada cliente tenha no máximo uma conta de cada tipo
→ Gera números de conta únicos com dígito verificador (Módulo 10)
→ Controla saldo e limite de cheque especial
→ Valida se o cliente existe via FeignClient → Cliente Service
→ Protege dados via JWT gerado pelo Auth Service
→ Expõe saldo de forma isolada (endpoint dedicado)
```

### O que ele NÃO faz

```
→ Não autentica usuários → Auth Service
→ Não gerencia perfil pessoal → Cliente Service
→ Não processa transferências → Transação Service (próximo)
→ Não envia notificações → Notification Service (V2)
→ Não gera tokens JWT → Auth Service
```

### Posição no ecossistema

```
                    ┌─────────────────┐
                    │   API Gateway   │ (porta 8080)
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
┌────────▼───────┐  ┌────────▼───────┐  ┌────────▼───────┐
│  Auth Service  │  │Cliente Service │  │ Conta Service  │
│  (porta 8081)  │  │  (porta 8082)  │  │  (porta 8083)  │
│  ✅ PRONTO     │  │  ✅ PRONTO     │  │  ✅ PRONTO     │
└────────────────┘  └───────┬────────┘  └───────┬────────┘
         │                  │                   │
┌────────▼───────┐  ┌───────▼────────┐  ┌───────▼────────┐
│    auth_db     │  │  cliente_db    │  │   conta_db     │
│  (porta 5433)  │  │  (porta 5434)  │  │  (porta 5435)  │
└────────────────┘  └────────────────┘  └────────────────┘
                             ▲
                             │ FeignClient
                    Conta Service chama
                    Cliente Service aqui
```

### Novidade em relação aos serviços anteriores

```
Auth Service    → geração de JWT, autenticação
Cliente Service → CRUD com relacionamento @OneToMany
Conta Service   → comunicação entre microsserviços via FeignClient ← NOVO
                  Logs estruturados com @Slf4j ← NOVO (ênfase)
                  BigDecimal para valores monetários ← NOVO
                  SEQUENCE do PostgreSQL ← NOVO
```

---

## 2. Como pensamos para desenvolver

### Decisões tomadas antes do código

**1. Como validar se o cliente existe?**

```
OPÇÃO A — Confiar no JWT:
"Se o token é válido, o cliente existe"
→ Rápido, sem chamada de rede
→ Problema: cliente pode ter sido desativado depois do login
→ Janela de até 15 minutos de inconsistência

OPÇÃO B — FeignClient (nossa escolha):
Conta Service chama Cliente Service antes de criar a conta
→ Verifica em tempo real se o cliente existe e está ativo
→ Acoplamento de rede mas consistência garantida

OPÇÃO C — Kafka (V2):
Eventos assíncronos — Conta Service guarda cópia local dos clientes
→ Zero acoplamento, zero latência extra
→ Complexidade maior — reservado para quando a escala exigir

DECISÃO: FeignClient (Opção B)
MOTIVO: ensina o conceito de comunicação entre microsserviços
        consistência em tempo real para operação bancária
```

**2. Como gerar o número de conta?**

```
OPÇÃO A — MAX + 1 no código:
SELECT MAX(numero_conta) FROM contas → incrementa no Java
PROBLEMA DE CONCORRÊNCIA:
Thread 1 busca MAX → "00000005"
Thread 2 busca MAX → "00000005" (ao mesmo tempo!)
Thread 1 salva     → "00000006"
Thread 2 salva     → "00000006" ← DUPLICADO! ❌

OPÇÃO B — SEQUENCE do PostgreSQL (nossa escolha):
SELECT nextval('seq_numero_conta')
→ Atômico por natureza — impossível duplicar
→ O banco gerencia o sequencial
→ Thread-safe sem lock no código

DECISÃO: SEQUENCE do PostgreSQL
```

**3. Tipos de valor monetário — BigDecimal obrigatório**

```
Double em Java usa ponto flutuante binário:
0.1 + 0.2 = 0.30000000000000004 ← ERRADO para dinheiro!

BigDecimal tem precisão exata:
new BigDecimal("0.10").add(new BigDecimal("0.20")) = 0.30 ← CORRETO

REGRA: NUNCA use Double ou Float para valores monetários.
       Sistemas financeiros usam SEMPRE BigDecimal (Java) ou
       DECIMAL(19,2) no banco de dados.

DECIMAL(19,2):
→ 19 dígitos totais
→ 2 casas decimais (centavos)
→ Máximo: R$ 99.999.999.999.999.999,99
```

**4. Soft delete obrigatório**

```
Regulação bancária (Banco Central do Brasil):
→ Contas encerradas devem ser preservadas no banco
→ Histórico de movimentações por no mínimo 5 anos
→ Auditoria deve funcionar mesmo em contas encerradas

DECISÃO: ativa = false (nunca DELETE físico)
```

---

## 3. OpenFeign — o grande conceito novo

### O que é o OpenFeign

**OpenFeign** é um cliente HTTP declarativo do Spring Cloud. Em vez de escrever código para fazer chamadas HTTP manualmente, você define uma **interface Java** e o Spring implementa automaticamente em runtime.

É a forma mais elegante e profissional de fazer comunicação síncrona entre microsserviços no ecossistema Spring.

---

### Por que os sêniores usam FeignClient

**Sem Feign — jeito manual com RestTemplate:**

```java
// Verboso, propenso a erros, difícil de testar e manter
@Autowired
private RestTemplate restTemplate;

public ClienteResponse buscarCliente(String usuarioId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + pegarTokenDoContexto());

    HttpEntity<String> entity = new HttpEntity<>(headers);

    ResponseEntity<ClienteResponse> response = restTemplate.exchange(
        "http://cliente-service/clientes/usuario/" + usuarioId,
        HttpMethod.GET,
        entity,
        ClienteResponse.class
    );

    if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new ClienteNotFoundException(usuarioId);
    }

    if (!response.getStatusCode().is2xxSuccessful()) {
        throw new ClienteServiceException("Erro ao chamar cliente-service");
    }

    return response.getBody();
}
```

**Com Feign — declarativo:**

```java
// Limpo, testável, integrado com Eureka automaticamente
@FeignClient(name = "cliente-service", configuration = FeignConfig.class)
public interface ClienteClient {

    @GetMapping("/clientes/usuario/{usuarioId}")
    ClienteResponse buscarPorUsuarioId(@PathVariable String usuarioId);
}

// No service — uso simples como qualquer outro bean:
ClienteResponse cliente = clienteClient.buscarPorUsuarioId(usuarioId);
```

---

### Como o Feign funciona por baixo

```
1. @EnableFeignClients na ContaApplication
   → Spring escaneia todas as interfaces @FeignClient

2. O Spring cria um proxy dinâmico que implementa ClienteClient
   → Quando você chama clienteClient.buscarPorUsuarioId("email")
   → O proxy intercepta a chamada
   → Monta a requisição HTTP baseada nas anotações (@GetMapping, @PathVariable)

3. FeignConfig.RequestInterceptor adiciona o JWT:
   → Pega o JWT do request atual via RequestContextHolder
   → Adiciona no header Authorization da chamada HTTP

4. Feign + Eureka → descoberta automática:
   → name = "cliente-service" → Feign pergunta ao Eureka
   → Eureka responde: "cliente-service está em localhost:8082"
   → Feign faz a chamada para http://localhost:8082/clientes/usuario/...
   → Se o cliente-service mudar de host/porta → Eureka atualiza
   → Feign encontra automaticamente — sem IP fixo no código

5. FeignConfig.ErrorDecoder traduz erros:
   → 404 → ClienteNotFoundException → GlobalHandler → 404
   → 400 → ClienteServiceException  → GlobalHandler → 503
   → 5xx → ClienteServiceException  → GlobalHandler → 503
```

---

### Propagação do JWT entre serviços

Este é um ponto crítico — sem isso o Cliente Service rejeita com 401:

```
PROBLEMA:
Usuário → [JWT] → Conta Service → Feign → Cliente Service
                                           ↑
                                  sem JWT → 401 ❌

SOLUÇÃO — RequestInterceptor:
```

```java
// FeignAuthConfig.java — intercepta TODA chamada Feign
@Configuration
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor jwtRequestInterceptor() {
        return template -> {
            // RequestContextHolder armazena o request HTTP atual por thread
            // Padrão ThreadLocal — cada requisição tem seu próprio contexto
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();

            if (attributes != null) {
                // Pega o JWT do request original (do usuário)
                String authHeader = attributes
                    .getRequest()
                    .getHeader("Authorization");

                // Propaga o mesmo JWT para o Cliente Service
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    template.header("Authorization", authHeader);
                }
            }
        };
    }
}
```

```
COM O INTERCEPTOR:
Usuário → [JWT] → Conta Service → RequestInterceptor pega JWT do contexto
                                → adiciona no header da chamada Feign
                                → [JWT] → Cliente Service ✅
```

---

### FeignConfig — configuração específica por cliente

```java
// SEM @Configuration — intencional!
// Referenciada apenas no @FeignClient específico:
// @FeignClient(configuration = FeignConfig.class)
// → aplica APENAS para o ClienteClient
// → outros FeignClients futuros terão suas próprias configurações
public class FeignConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        // FULL em dev → loga body completo das requisições e respostas
        // Em produção → BASIC ou NONE
        return Logger.Level.FULL;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        // Traduz status HTTP em exceções do nosso domínio
        return (methodKey, response) -> switch (response.status()) {
            case 404 -> new ClienteNotFoundException("...");
            case 400 -> new ClienteServiceException("...");
            default  -> new ClienteServiceException("...");
        };
    }
}
```

---

### Por que não usar RestTemplate ou WebClient

```
RestTemplate → legado, verboso, sem integração automática com Eureka
WebClient    → reativo (Project Reactor) — bom para I/O não-bloqueante
               mas adiciona complexidade sem necessidade em serviços síncronos

FeignClient  → declarativo, integração nativa com Eureka e Spring Cloud
               testável facilmente (mock da interface)
               ErrorDecoder para tratar erros de forma centralizada
               RequestInterceptor para propagação de headers
               Logger integrado para debugging
               padrão de mercado em microsserviços Spring Boot ✅
```

---

### O que acontece quando o Cliente Service está fora do ar

```java
// ErrorDecoder captura qualquer erro inesperado (5xx, timeout):
default -> new ClienteServiceException(
    "Erro ao comunicar com Cliente Service. Status: " + response.status()
);

// GlobalExceptionHandler mapeia para 503:
@ExceptionHandler(ClienteServiceException.class)
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public ErroResponse handleClienteServiceIndisponivel(ClienteServiceException ex) {
    log.error("Cliente Service indisponível: {}", ex.getMessage(), ex);
    return new ErroResponse(
        "Serviço temporariamente indisponível. Tente novamente mais tarde.",
        503
    );
}

// Resposta ao usuário:
// HTTP 503 Service Unavailable
// { "mensagem": "Serviço temporariamente indisponível...", "status": 503 }
```

---

### Log do Feign em ação

Com `Logger.Level.FULL` você vê cada chamada no console:

```
DEBUG [ClienteClient] ---> GET http://cliente-service/clientes/usuario/joao@email.com HTTP/1.1
DEBUG [ClienteClient] Authorization: Bearer eyJhbGci...
DEBUG [ClienteClient] ---> END HTTP (0-byte body)
DEBUG [ClienteClient] <--- HTTP/1.1 200 (31ms)
DEBUG [ClienteClient] content-type: application/json
DEBUG [ClienteClient] {"id":"195c7e74...","nome":"João Silva","ativo":true}
DEBUG [ClienteClient] <--- END HTTP (271-byte body)
```

---

## 4. Logs com @Slf4j — como funciona de verdade

### O que é SLF4J

**SLF4J** (Simple Logging Facade for Java) é uma **fachada de logging** — uma interface que se conecta a qualquer implementação de log (Logback, Log4j, JUL etc).

O Spring Boot usa **Logback** como implementação padrão. O SLF4J é a interface que você usa no código — sem depender de uma implementação específica.

```
Seu código → SLF4J (interface) → Logback (implementação) → Console/Arquivo
                                                          → Grafana/Datadog
                                                          → CloudWatch
```

---

### @Slf4j — o que essa anotação faz

```java
// SEM @Slf4j — verboso e repetitivo em cada classe:
public class ContaService {
    private static final Logger log =
        LoggerFactory.getLogger(ContaService.class);
    // ... você escreve isso em cada classe
}

// COM @Slf4j — Lombok gera automaticamente:
@Slf4j
public class ContaService {
    // log já está disponível — Lombok gerou o campo acima
    // você só usa:
    log.info("Conta aberta: {}", numeroConta);
}
```

**O que o Lombok gera quando você coloca @Slf4j:**

```java
// Equivalente gerado pelo Lombok em tempo de compilação:
private static final org.slf4j.Logger log =
    org.slf4j.LoggerFactory.getLogger(ContaService.class);
```

---

### Os níveis de log — do menos ao mais grave

```
TRACE → informação extremamente detalhada (raramente usado)
        "Entrando no método calcularDigito com valor 5"

DEBUG → informação de debugging — detalhes de execução
        "SELECT nextval('seq_numero_conta')"
        "Número gerado: 0001-00000001-5"

INFO  → informação relevante sobre o fluxo normal
        "Conta aberta com sucesso: 0001-00000001-5 | Cliente: joao@email.com"
        "Serviço iniciado na porta 8083"

WARN  → algo inesperado mas recuperável — vale investigar
        "Cliente tentou abrir conta CORRENTE duplicada"
        "Tentativa de encerrar conta com saldo negativo"

ERROR → erro grave que afetou uma operação — precisa de atenção
        "Cliente Service indisponível — timeout após 5000ms"
        "Erro inesperado ao salvar conta no banco"
```

---

### Como usamos os níveis no projeto

```java
@Slf4j
@Service
public class ContaService {

    public ContaResponse abrir(ContaRequest request, String usuarioId) {

        // DEBUG → detalhes de execução (visível só em dev)
        log.debug("Iniciando abertura de conta. Tipo: {} | Usuário: {}",
                request.tipo(), usuarioId);

        // INFO → fluxo normal de negócio
        log.info("Abrindo conta {} para cliente: {}",
                request.tipo(), usuarioId);

        ClienteResponse cliente = clienteClient.buscarPorUsuarioId(usuarioId);

        if (!cliente.ativo()) {
            // WARN → regra de negócio violada — esperado mas vale monitorar
            log.warn("Tentativa de abrir conta para cliente inativo: {}",
                    usuarioId);
            throw new ClienteNotFoundException(usuarioId);
        }

        // INFO → operação concluída com sucesso
        log.info("Conta aberta com sucesso: {}-{}-{} | Cliente: {}",
                numeroConta[0], numeroConta[1], numeroConta[2], usuarioId);
    }
}

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // WARN → erros de negócio esperados (não críticos)
    @ExceptionHandler(ContaNotFoundException.class)
    public ErroResponse handleNotFound(BusinessException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        return new ErroResponse(ex.getMessage(), 404);
    }

    // ERROR → erro de infraestrutura (crítico — precisa atenção)
    @ExceptionHandler(ClienteServiceException.class)
    public ErroResponse handleClienteService(ClienteServiceException ex) {
        log.error("Cliente Service indisponível: {}", ex.getMessage(), ex);
        //                                                            ↑
        //              terceiro parâmetro = a exceção inteira
        //              inclui o stack trace completo no log
        return new ErroResponse("Serviço indisponível", 503);
    }

    // ERROR + stack trace completo
    @ExceptionHandler(Exception.class)
    public ErroResponse handleGenerico(Exception ex) {
        log.error("Erro inesperado: {}", ex.getMessage(), ex);
        return new ErroResponse("Erro interno do servidor", 500);
    }
}
```

---

### Placeholder {} — por que usar e não concatenar String

```java
// ERRADO — concatenação de String:
log.info("Conta aberta: " + numeroConta + " para " + clienteId);
// Problema: a String é montada SEMPRE, mesmo se o nível INFO
// estiver desabilitado em produção → desperdício de CPU e memória

// CORRETO — placeholder {}:
log.info("Conta aberta: {} para {}", numeroConta, clienteId);
// A String SÓ é montada se o nível INFO estiver habilitado
// Em produção com nível ERROR → zero custo de processamento

// CORRETO — múltiplos placeholders:
log.debug("Conta: {}-{}-{} | Cliente: {} | Tipo: {}",
        agencia, conta, digito, clienteId, tipo);
// Cada {} é substituído pelo argumento na posição correspondente
```

---

### Configuração de níveis no application-dev.properties

```properties
# Pacote da aplicação → DEBUG (vemos tudo em desenvolvimento)
logging.level.com.tribunalbank=DEBUG

# SQL gerado pelo Hibernate → DEBUG (vemos as queries)
logging.level.org.hibernate.SQL=DEBUG

# Chamadas Feign → DEBUG (vemos request/response completo)
logging.level.com.tribunalbank.conta.client=DEBUG

# Em produção (application-prod.properties):
logging.level.com.tribunalbank=INFO   # só fluxo normal
logging.level.org.hibernate.SQL=WARN  # só erros SQL
logging.level.com.tribunalbank.conta.client=BASIC # só status e tempo
```

---

### O que sai no console com cada nível

**Em desenvolvimento (DEBUG ativo):**

```
DEBUG [ContaController]  : Abrindo conta. Tipo: CORRENTE | Usuário: joao@email.com
INFO  [ContaService]     : Abrindo conta CORRENTE para cliente: joao@email.com
DEBUG [ClienteClient]    : ---> GET http://cliente-service/clientes/usuario/joao@email.com
DEBUG [ClienteClient]    : Authorization: Bearer eyJhbGci...
DEBUG [ClienteClient]    : <--- HTTP/1.1 200 (31ms)
DEBUG [ClienteClient]    : {"id":"195c7e74...","ativo":true}
DEBUG [hibernate.SQL]    : select c1_0.id from contas where cliente_id=? and tipo=?
DEBUG [hibernate.SQL]    : SELECT nextval('seq_numero_conta')
DEBUG [NumeroContaService]: Número de conta gerado: 0001-00000001-5
INFO  [ContaService]     : Conta aberta com sucesso: 0001-00000001-5 | Cliente: joao@email.com
DEBUG [hibernate.SQL]    : insert into contas (ativa,...) values (?,?,?,...)
```

**Em produção (INFO ativo):**

```
INFO  [ContaService]     : Abrindo conta CORRENTE para cliente: joao@email.com
INFO  [ContaService]     : Conta aberta com sucesso: 0001-00000001-5 | Cliente: joao@email.com
```

---

### Logs e observabilidade — a conexão com Grafana e Datadog

Em produção, os logs não ficam só no console. Eles são enviados para sistemas de observabilidade:

```
Aplicação → Logback → Arquivo de log / stdout
                    ↓
             Agente de coleta (Filebeat, Fluentd, Vector)
                    ↓
             Sistema de observabilidade:
             ┌─────────────────────────────────────────┐
             │  Grafana Loki  → visualização de logs   │
             │  Datadog       → alertas e dashboards   │
             │  AWS CloudWatch → logs na nuvem AWS     │
             │  ELK Stack     → Elasticsearch + Kibana │
             └─────────────────────────────────────────┘
```

**Por que isso importa:**

```
SEM LOGS ESTRUTURADOS:
Erro em produção às 3h da manhã.
Ninguém sabe o que aconteceu.
Sem rastreabilidade.

COM LOGS + GRAFANA:
→ Alerta dispara quando log.error() é chamado
→ Dashboard mostra taxa de erros em tempo real
→ Você pesquisa: "mostrar todos os logs do cliente joao@email.com"
→ Encontra exatamente o que aconteceu, em qual método, com qual dados
```

**Exemplo de dashboard de alerta:**

```
REGRA DE ALERTA NO GRAFANA:
Se count(log.level="ERROR") > 5 em 1 minuto
→ Envia alerta para Slack do time
→ Abre ticket no PagerDuty
→ Aciona o plantão

Por isso log.warn() para erros esperados (negócio)
e log.error() para erros críticos (infraestrutura):
→ Não queremos alerta para "cliente não encontrado" (404)
→ Queremos alerta para "banco de dados fora do ar" (500)
```

---

### Nunca logue dados sensíveis

```java
// ERRADO — expõe dados sensíveis:
log.info("Autenticando usuário: {} com senha: {}", email, senha);
log.info("CPF do cliente: {}", cpf);  // CPF é dado pessoal (LGPD)
log.debug("Token JWT: {}", token);    // token é credencial

// CORRETO — mascarar ou omitir dados sensíveis:
log.info("Autenticando usuário: {}", email);  // sem a senha
log.info("CPF: {}***", cpf.substring(0, 3)); // só primeiros dígitos
log.debug("Token gerado para: {}", email);   // sem o token em si

// No nosso projeto:
log.info("Abrindo conta CORRENTE para cliente: joao@email.com");
// ✅ email é identificador mas não dado financeiro
// ✅ nunca logamos saldo, limite ou número de conta completo em INFO
```

---

## 5. Conhecimentos aplicados neste projeto

### Java — recursos da linguagem

| Conceito | Onde aparece | Por que foi usado |
|---|---|---|
| **Record** | `ContaRequest`, `ContaResponse`, `SaldoResponse`, `LimiteRequest`, `ClienteResponse` | Imutável por padrão — ideal para DTOs |
| **BigDecimal** | `Conta.saldo`, `Conta.limite`, `ContaResponse.saldoDisponivel` | Precisão exata para valores monetários |
| **Optional** | `ContaRepository.findByClienteIdAndTipo()`, `findByNumeroAgenciaAndNumeroConta()` | Evita NullPointerException |
| **Switch expression** | `FeignConfig.ErrorDecoder` | Sintaxe moderna Java 14+ mais expressiva |
| **@Builder.Default** | `Conta.saldo`, `Conta.limite`, `Conta.ativa` | Inicializa campos com valores padrão no Builder |
| **String.format** | `NumeroContaService.gerar()` — `%08d` | Formata número com zeros à esquerda |

---

### OpenFeign (conceito novo)

| Conceito | Onde aparece |
|---|---|
| **@FeignClient** | `ClienteClient` — define interface de comunicação com cliente-service |
| **@EnableFeignClients** | `ContaApplication` — habilita escaneamento de interfaces Feign |
| **FeignConfig** | Logger, ErrorDecoder — sem @Configuration (escopo específico) |
| **FeignAuthConfig** | RequestInterceptor — propaga JWT para o cliente-service |
| **ErrorDecoder** | Traduz status HTTP em exceções do domínio |
| **RequestInterceptor** | Adiciona header Authorization automaticamente |
| **RequestContextHolder** | Acessa o request HTTP atual via ThreadLocal |
| **Logger.Level.FULL** | Log completo de requisições/respostas em dev |

---

### Logs com @Slf4j (conceito aprofundado)

| Conceito | Onde aparece |
|---|---|
| **@Slf4j** | Todos os Services e GlobalExceptionHandler |
| **log.debug()** | Controller (entrada de requests), queries SQL, Feign calls |
| **log.info()** | Operações de negócio concluídas com sucesso |
| **log.warn()** | Regras de negócio violadas, erros esperados |
| **log.error()** | Erros de infraestrutura, exceções inesperadas com stack trace |
| **Placeholder {}** | Todos os logs — evita concatenação desnecessária de String |
| **Terceiro parâmetro ex** | `log.error("msg: {}", ex.getMessage(), ex)` — inclui stack trace |

---

### Spring Security

| Conceito | Onde aparece |
|---|---|
| **Duas Filter Chains** | `SecurityConfig` — igual ao cliente-service |
| **JwtDecoder apenas** | `JwtConfig` — só chave pública RSA |
| **@PreAuthorize** | `ContaController` — dono da conta ou ADMIN |
| **pertenceAoCliente()** | `ContaService` — verifica usuarioId vs JWT subject |

---

### JPA e Banco

| Conceito | Onde aparece |
|---|---|
| **@Getter + @Setter** | `Conta` — em vez de @Data (evita toString problemático) |
| **DECIMAL(19,2)** | `saldo` e `limite` — precisão financeira |
| **BigDecimal.ZERO** | `@Builder.Default` — saldo inicial zerado |
| **@Transactional** | Métodos de escrita — rollback automático |
| **@Transactional(readOnly)** | Métodos de leitura — otimização Dirty Checking |
| **SEQUENCE PostgreSQL** | `seq_numero_conta` — geração thread-safe de número de conta |
| **Soft delete** | `ativa = false` — nunca DELETE físico |
| **nativeQuery = true** | `proximoNumeroConta()` — SEQUENCE é específico do PostgreSQL |

---

### Algoritmo do número de conta

**Módulo 10** — dígito verificador:

```
Número: 00000001

Dígitos:  0  0  0  0  0  0  0  1
Pesos:    2  1  2  1  2  1  2  1  (alternando da direita para esquerda)
Produto:  0  0  0  0  0  0  0  1

Se produto >= 10 → soma os dois dígitos: 14 → 1+4 = 5
Se produto < 10  → usa o produto: 7 → 7

Soma total = 1
Resto = 1 % 10 = 1
Dígito = (10 - 1) % 10 = 9

Número completo: 0001-00000001-9
```

---

## 6. Stack e dependências

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

<!-- FeignClient — comunicação entre microsserviços ← NOVO -->
spring-cloud-starter-openfeign

<!-- Documentação -->
springdoc-openapi-starter-webmvc-ui:2.8.9

<!-- Utilitários -->
lombok
```

---

## 7. Como rodar localmente

### Serviços necessários

```
✅ conta-db (Docker porta 5435)       → banco do conta-service
✅ auth-service (porta 8081)          → para gerar tokens JWT
✅ cliente-service (porta 8082)       → FeignClient chama ele
✅ discovery-server (porta 8761)      → para o Feign encontrar o cliente-service
✅ conta-service (porta 8083)         → o serviço em si

❌ api-gateway → não obrigatório para testes diretos
```

### Ordem de subida

```
1. docker-compose up -d  (todos os bancos)
2. discovery-server
3. auth-service
4. cliente-service
5. conta-service         ← aguardar ~30s para Eureka registrar os serviços
```

### Verificar se está rodando

```
Swagger UI:   http://localhost:8083/swagger-ui.html
Health check: http://localhost:8083/actuator/health
Eureka:       http://localhost:8761  (ver conta-service e cliente-service registrados)
```

### Portas do projeto completo

```
5432 → PostgreSQL local do Windows (não usar)
5433 → auth-db
5434 → cliente-db
5435 → conta-db      ← novo

8080 → api-gateway
8081 → auth-service
8082 → cliente-service
8083 → conta-service  ← novo
8761 → discovery-server
```

---

## 8. Fluxo de desenvolvimento — fase a fase

### Por que essa ordem

Cada fase depende da anterior. A fundação (banco) sempre vem antes da aplicação.

---

### FASE 1 — Planejamento

```
Definir antes de escrever qualquer código:
→ Tipos de conta: CORRENTE, POUPANCA, SALARIO, INVESTIMENTO
→ Regra: um cliente = no máximo uma conta de cada tipo
→ Saldo inicial: R$ 0,00 com limite configurável
→ Número: agência (0001) + 8 dígitos sequenciais + dígito verificador
→ Comunicação com Cliente Service: FeignClient via Eureka
→ BigDecimal obrigatório para valores monetários
→ SEQUENCE para número de conta (thread-safe)
→ Soft delete: ativa = false
```

---

### FASE 2 — Migrations (Flyway)

```
src/main/resources/db/migration/
├── V1__create_contas.sql
└── V2__create_historico_saldo.sql
```

**V1__create_contas.sql:**

```sql
CREATE SEQUENCE seq_numero_conta
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;

CREATE TABLE contas (
    id                  VARCHAR(36)   NOT NULL PRIMARY KEY,
    cliente_id          VARCHAR(36)   NOT NULL,
    usuario_id          VARCHAR(36)   NOT NULL,
    numero_agencia      VARCHAR(4)    NOT NULL,
    numero_conta        VARCHAR(8)    NOT NULL,
    digito_verificador  VARCHAR(1)    NOT NULL,
    tipo                VARCHAR(20)   NOT NULL,
    saldo               DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    limite              DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ativa               BOOLEAN       NOT NULL DEFAULT true,
    criado_em           TIMESTAMP     NOT NULL,
    atualizado_em       TIMESTAMP     NOT NULL,

    CONSTRAINT uk_numero_conta UNIQUE (numero_agencia, numero_conta),
    CONSTRAINT uk_cliente_tipo UNIQUE (cliente_id, tipo)
);

CREATE INDEX idx_contas_cliente_id ON contas(cliente_id);
CREATE INDEX idx_contas_usuario_id ON contas(usuario_id);
CREATE INDEX idx_contas_tipo       ON contas(tipo);
```

**V2__create_historico_saldo.sql:**

```sql
CREATE TABLE historico_saldo (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    conta_id        VARCHAR(36)   NOT NULL
        REFERENCES contas(id) ON DELETE CASCADE,
    saldo_anterior  DECIMAL(19,2) NOT NULL,
    saldo_novo      DECIMAL(19,2) NOT NULL,
    motivo          VARCHAR(100)  NOT NULL,
    criado_em       TIMESTAMP     NOT NULL
);

CREATE INDEX idx_historico_conta_id  ON historico_saldo(conta_id);
CREATE INDEX idx_historico_criado_em ON historico_saldo(criado_em);
```

---

### FASE 3 — Entidade

```
entity/
├── TipoConta.java   ← enum: CORRENTE, POUPANCA, SALARIO, INVESTIMENTO
└── Conta.java       ← @Getter + @Setter (não @Data)
```

**Por que @Getter + @Setter em vez de @Data:**

```
@Data gera toString() que inclui TODOS os campos.
Em entidades JPA isso causa:
→ LazyInitializationException ao logar entidades com campos LAZY
→ StackOverflow em referências circulares

@Getter + @Setter separados:
→ Sem toString problemático
→ Controle granular: campo imutável = @Setter(AccessLevel.NONE)
→ Padrão recomendado em entidades JPA
```

---

### FASE 4 — Repository

```
repository/
└── ContaRepository.java
```

Métodos criados:

```java
List<Conta>     findByClienteId(String clienteId);
Optional<Conta> findByClienteIdAndTipo(String clienteId, TipoConta tipo);
boolean         existsByClienteIdAndTipo(String clienteId, TipoConta tipo);
Optional<Conta> findByNumeroAgenciaAndNumeroConta(String ag, String nr);
List<Conta>     findByClienteIdAndAtivaTrue(String clienteId);

// SEQUENCE — nativeQuery obrigatório (SQL específico do PostgreSQL)
@Query(value = "SELECT nextval('seq_numero_conta')", nativeQuery = true)
Long proximoNumeroConta();
```

---

### FASE 5 — DTOs

```
dto/
├── ContaRequest.java    ← {TipoConta tipo, BigDecimal limite}
├── ContaResponse.java   ← com saldoDisponivel calculado no from()
├── SaldoResponse.java   ← endpoint dedicado de saldo
└── LimiteRequest.java   ← {BigDecimal valor} — PATCH body
```

**Por que LimiteRequest separado:**

```
Valor monetário NUNCA em query param:
PATCH /contas/{id}/limite?valor=500.00  ← ERRADO
→ Aparece em logs de servidor, proxies, CDN
→ Dado financeiro exposto em URLs

PATCH /contas/{id}/limite
{ "valor": 500.00 }                      ← CORRETO
→ Body da requisição — não aparece em logs de URL
```

---

### FASE 6 — Exceptions

```
exception/
├── BusinessException.java           ← abstract — base de todas
├── ContaNotFoundException.java      ← 404
├── ClienteNotFoundException.java    ← 404 (do FeignClient)
├── TipoContaJaExisteException.java  ← 409
├── SaldoInsuficienteException.java  ← 422
├── ContaInativaException.java       ← 422
├── ClienteServiceException.java     ← 503 (extends RuntimeException, não Business)
└── GlobalExceptionHandler.java      ← @Slf4j + @RestControllerAdvice
```

**Por que ClienteServiceException não herda de BusinessException:**

```
BusinessException → erros de NEGÓCIO (esperados, controlados)
                   Ex: conta não encontrada, saldo insuficiente

ClienteServiceException → erro de INFRAESTRUTURA (inesperado)
                          Ex: Cliente Service fora do ar

A distinção permite o GlobalExceptionHandler:
→ BusinessException → log.warn() → não é crítico
→ ClienteServiceException → log.error() → é crítico, aciona alertas
```

---

### FASE 7 — Configurações

```
config/
├── FeignConfig.java      ← Logger + ErrorDecoder (sem @Configuration)
├── FeignAuthConfig.java  ← RequestInterceptor JWT (@Configuration global)
├── JpaAuditingConfig.java
├── JwtConfig.java        ← apenas JwtDecoder
├── SecurityConfig.java   ← duas filter chains
└── SwaggerConfig.java
```

---

### FASE 8 — FeignClient

```
client/
├── ClienteClient.java    ← interface @FeignClient
└── ClienteResponse.java  ← DTO próprio (não importa do cliente-service)
```

**Por que ClienteResponse próprio:**

```
Se importasse do cliente-service:
→ Acoplamento de compilação — mudou lá? precisa recompilar aqui
→ Quebra a independência entre microsserviços

Com DTO próprio:
→ Só tem os campos que o conta-service precisa: id, nome, cpf, ativo
→ cliente-service pode adicionar campos novos sem afetar o conta-service
→ Total independência
```

---

### FASE 9 — Services

```
service/
├── NumeroContaService.java  ← algoritmo Módulo 10 (SRP)
└── ContaService.java        ← orquestra o fluxo de negócio
```

**NumeroContaService — Single Responsibility:**

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class NumeroContaService {

    private static final String AGENCIA_PADRAO = "0001";

    public String[] gerar() {
        Long sequencial = contaRepository.proximoNumeroConta();
        String numeroConta = String.format("%08d", sequencial);
        String digito = calcularDigito(numeroConta);

        log.debug("Número de conta gerado: {}-{}-{}",
                AGENCIA_PADRAO, numeroConta, digito);

        return new String[]{AGENCIA_PADRAO, numeroConta, digito};
    }

    private String calcularDigito(String numeroConta) {
        // Algoritmo Módulo 10
        int soma = 0;
        int peso = 2;
        for (int i = numeroConta.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(numeroConta.charAt(i));
            int produto = digito * peso;
            soma += produto >= 10
                    ? (produto / 10) + (produto % 10)
                    : produto;
            peso = (peso == 2) ? 1 : 2;
        }
        return String.valueOf((10 - soma % 10) % 10);
    }
}
```

---

### FASE 10 — Controller

```
controller/
└── ContaController.java
```

**Endpoints com decisão de design:**

```
POST   /contas                        → abrir conta
GET    /contas                        → listar minhas contas (+ ?clienteId para ADMIN)
GET    /contas/{id}                   → buscar por ID
GET    /contas/{id}/saldo             → consultar saldo (endpoint dedicado)
GET    /contas/numero/{agencia}/{nr}  → buscar por número (para Transação Service)
PATCH  /contas/{id}/limite            → atualizar limite (ADMIN) — valor no BODY
DELETE /contas/{id}                   → encerrar conta (ADMIN)
```

---

## 9. Endpoints da API

Base URL: `http://localhost:8083`
Swagger: `http://localhost:8083/swagger-ui.html`

| Método | Endpoint | Descrição | Acesso |
|---|---|---|---|
| `POST` | `/contas` | Abrir conta | Usuário autenticado |
| `GET` | `/contas` | Listar minhas contas | Usuário autenticado |
| `GET` | `/contas?clienteId=x` | Listar contas de outro cliente | Apenas ADMIN |
| `GET` | `/contas/{id}` | Buscar por ID | Dono ou ADMIN |
| `GET` | `/contas/{id}/saldo` | Consultar saldo | Dono ou ADMIN |
| `GET` | `/contas/numero/{ag}/{nr}` | Buscar por número | Apenas ADMIN |
| `PATCH` | `/contas/{id}/limite` | Atualizar limite | Apenas ADMIN |
| `DELETE` | `/contas/{id}` | Encerrar conta | Apenas ADMIN |

### Exemplo — Abrir conta corrente

```json
POST /contas
Authorization: Bearer eyJhbGci...

{
  "tipo": "CORRENTE",
  "limite": 500.00
}
```

### Exemplo — Resposta 201 Created

```json
{
  "id": "af91d6ca-db42-45fb-96f4-006ee0de8b26",
  "clienteId": "195c7e74-61e6-4f96-a921-4eb3f02c9a37",
  "numeroCompleto": "0001-00000001-5",
  "tipo": "CORRENTE",
  "saldo": 0.00,
  "limite": 500.00,
  "saldoDisponivel": 500.00,
  "ativa": true,
  "criadoEm": "2026-06-22T16:28:11",
  "atualizadoEm": "2026-06-22T16:28:11"
}
```

### Exemplo — Consultar saldo

```json
GET /contas/{id}/saldo
Authorization: Bearer eyJhbGci...

{
  "numeroConta": "0001-00000001-5",
  "saldo": -200.00,
  "limite": 500.00,
  "saldoDisponivel": 300.00,
  "consultadoEm": "2026-06-22T16:30:00"
}
```

### Exemplo — Atualizar limite (ADMIN)

```json
PATCH /contas/{id}/limite
Authorization: Bearer eyJhbGci... (ADMIN)

{
  "valor": 1000.00
}
```

---

## 10. Fluxos completos detalhados

### Fluxo 1 — Abrir conta (fluxo feliz completo)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PASSO 1: Usuário faz login no Auth Service

POST http://localhost:8081/auth/login
{ "email": "joao@email.com", "senha": "senha123" }

Auth Service:
→ Valida credenciais
→ Gera accessToken (JWT RS256, 15min)
   Claims: { sub: "joao@email.com", roles: "ROLE_USER", exp: ... }

Resposta: { "accessToken": "eyJhbGci...", ... }
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PASSO 2: Usuário abre conta corrente

POST http://localhost:8083/contas
Authorization: Bearer eyJhbGci...
{ "tipo": "CORRENTE", "limite": 500.00 }
     ↓
publicFilterChain → não bate (não é Swagger/Actuator)
     ↓
protectedFilterChain (@Order 2):
→ BearerTokenAuthenticationFilter extrai o token
→ NimbusJwtDecoder.decode(token):
   ✓ Verifica assinatura com chave PÚBLICA RSA
   ✓ Verifica expiração
→ Token válido → SecurityContext populado
     ↓
ContaController.abrir(request, jwt):
→ String usuarioId = jwt.getSubject()
  → "joao@email.com" (claim "sub" do JWT)
→ @Valid valida o DTO:
   ✓ tipo não null
   ✓ limite >= 0.00 (se informado)
     ↓
ContaService.abrir(request, "joao@email.com"):
     ↓
1. log.info("Abrindo conta CORRENTE para cliente: joao@email.com")

2. FeignClient → Cliente Service:
   GET http://cliente-service/clientes/usuario/joao@email.com
   Authorization: Bearer eyJhbGci... (propagado pelo RequestInterceptor)
        ↓
   Cliente Service:
   → Valida JWT ✓
   → findByUsuarioId("joao@email.com") → encontrou
   → Retorna: { "id": "195c7e74...", "ativo": true }

3. cliente.ativo() = true → pode continuar ✓

4. existsByClienteIdAndTipo("195c7e74...", CORRENTE) → false
   → Não existe conta CORRENTE → pode criar ✓

5. NumeroContaService.gerar():
   → SELECT nextval('seq_numero_conta') → 1
   → String.format("%08d", 1) → "00000001"
   → calcularDigito("00000001") → "5"
   → retorna ["0001", "00000001", "5"]
   log.debug("Número gerado: 0001-00000001-5")

6. limite = request.limite() != null ? 500.00 : BigDecimal.ZERO

7. Conta.builder()
   .clienteId("195c7e74-...")   ← UUID do cliente
   .usuarioId("joao@email.com") ← email do JWT
   .numeroAgencia("0001")
   .numeroConta("00000001")
   .digitoVerificador("5")
   .tipo(CORRENTE)
   .saldo(BigDecimal.ZERO)
   .limite(500.00)
   .ativa(true)
   .build()

8. contaRepository.save(conta)
   → INSERT INTO contas VALUES (...)
   → @CreatedDate e @LastModifiedDate preenchidos automaticamente

9. log.info("Conta aberta com sucesso: 0001-00000001-5 | Cliente: joao@email.com")

10. return ContaResponse.from(conta)
    → saldoDisponivel = 0.00 + 500.00 = 500.00
     ↓
HTTP 201 Created
{
  "id": "af91d6ca...",
  "numeroCompleto": "0001-00000001-5",
  "saldo": 0.00,
  "limite": 500.00,
  "saldoDisponivel": 500.00,
  ...
}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Fluxo 2 — Tentativa de conta duplicada

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
POST /contas
{ "tipo": "CORRENTE" }  ← já tem uma CORRENTE
     ↓
ContaService:
1. FeignClient → cliente existe e ativo ✓
2. existsByClienteIdAndTipo("195c7e74...", CORRENTE) → TRUE ❌

log.warn("Cliente 195c7e74... já possui conta do tipo: CORRENTE")
throw new TipoContaJaExisteException("CORRENTE")
     ↓
GlobalExceptionHandler.handleConflict():
log.warn("Conflito de dados: Cliente já possui uma conta do tipo: CORRENTE")
     ↓
HTTP 409 Conflict
{
  "mensagem": "Cliente já possui uma conta do tipo: CORRENTE",
  "timestamp": "2026-06-22T16:28:11",
  "status": 409
}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Fluxo 3 — Cliente Service fora do ar

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
POST /contas
{ "tipo": "SALARIO" }
     ↓
ContaService:
clienteClient.buscarPorUsuarioId("joao@email.com")
     ↓
Feign tenta chamar cliente-service
→ Conexão recusada / timeout

FeignConfig.ErrorDecoder:
→ status 5xx ou exception de conexão
→ throw new ClienteServiceException("Erro ao comunicar...")
     ↓
GlobalExceptionHandler.handleClienteServiceIndisponivel():
log.error("Cliente Service indisponível: Erro ao comunicar...", ex)
↑
Stack trace completo gravado no log — essencial para debugging
     ↓
HTTP 503 Service Unavailable
{
  "mensagem": "Serviço temporariamente indisponível. Tente novamente mais tarde.",
  "status": 503
}

ALERTA DISPARA NO GRAFANA:
log.error() → contador de erros sobe
→ Dashboard detecta > 5 erros/minuto
→ Notificação para o time no Slack
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Fluxo 4 — Encerrar conta com saldo negativo

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DELETE /contas/{id}
Authorization: Bearer eyJhbGci... (ADMIN)
     ↓
@PreAuthorize("hasRole('ROLE_ADMIN')") → ADMIN ✓
     ↓
ContaService.encerrar("af91d6ca..."):
1. findById → conta encontrada ✓
2. conta.getSaldo() = -200.00
3. -200.00.compareTo(BigDecimal.ZERO) < 0 → VERDADEIRO

log.warn("Tentativa de encerrar conta com saldo negativo: af91d6ca...")
throw new SaldoInsuficienteException("Conta com saldo negativo não pode ser encerrada...")
     ↓
HTTP 422 Unprocessable Entity
{
  "mensagem": "Conta com saldo negativo não pode ser encerrada. Saldo: R$ -200.00",
  "status": 422
}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 11. Fluxo de erros

### Mapeamento de exceções

| Exceção | HTTP | Log | Quando |
|---|---|---|---|
| `ContaNotFoundException` | 404 | warn | Conta não encontrada por ID ou número |
| `ClienteNotFoundException` | 404 | warn | Cliente não existe no Cliente Service |
| `TipoContaJaExisteException` | 409 | warn | Cliente já tem conta desse tipo |
| `SaldoInsuficienteException` | 422 | warn | Saldo negativo ao encerrar |
| `ContaInativaException` | 422 | warn | Operação em conta encerrada |
| `ClienteServiceException` | 503 | error | Cliente Service fora do ar |
| `MethodArgumentNotValidException` | 400 | warn | Campos inválidos no DTO |
| `Exception` | 500 | error | Erro inesperado |

### Formato padronizado

```json
{
  "mensagem": "Cliente já possui uma conta do tipo: CORRENTE",
  "timestamp": "2026-06-22T16:28:11.074",
  "status": 409
}
```

---

## 12. Checklist de conceitos implementados

### OpenFeign (conceito novo)

- [x] **@FeignClient** — interface declarativa para chamar cliente-service
- [x] **@EnableFeignClients** — habilita escaneamento na ContaApplication
- [x] **FeignConfig sem @Configuration** — escopo específico por cliente
- [x] **FeignAuthConfig com @Configuration** — interceptor JWT global
- [x] **RequestInterceptor** — propaga JWT via RequestContextHolder
- [x] **ErrorDecoder** — traduz status HTTP em exceções do domínio
- [x] **Logger.Level.FULL** — log completo das chamadas em dev
- [x] **Timeouts configurados** — connect 3s, read 5s no properties
- [x] **ClienteResponse próprio** — sem acoplamento ao cliente-service
- [x] **Integração com Eureka** — Feign descobre cliente-service pelo nome

### Logs com @Slf4j

- [x] **@Slf4j** — em todos os Services e GlobalExceptionHandler
- [x] **log.debug()** — entrada de controllers, queries, chamadas Feign
- [x] **log.info()** — operações de negócio concluídas
- [x] **log.warn()** — regras de negócio violadas (erros esperados)
- [x] **log.error()** — erros de infraestrutura com stack trace
- [x] **Placeholder {}** — sem concatenação de String desnecessária
- [x] **Terceiro parâmetro ex** — stack trace completo nos logs de erro
- [x] **Níveis por ambiente** — DEBUG em dev, INFO em produção

### Spring Security

- [x] **Duas SecurityFilterChain** com @Order
- [x] **JwtDecoder apenas** — conta-service só verifica, nunca gera
- [x] **@PreAuthorize** — por dono da conta ou ADMIN
- [x] **pertenceAoCliente()** — verifica usuarioId da conta vs JWT

### JPA e Banco

- [x] **@Getter + @Setter** — em vez de @Data na entidade Conta
- [x] **BigDecimal** — saldo e limite (nunca Double)
- [x] **DECIMAL(19,2)** — precision=19, scale=2 no banco
- [x] **@Builder.Default** — saldo=ZERO, limite=ZERO, ativa=true
- [x] **SEQUENCE PostgreSQL** — número de conta thread-safe
- [x] **nativeQuery = true** — para chamada do nextval()
- [x] **@Transactional** — escrita com rollback automático
- [x] **@Transactional(readOnly)** — leitura com Dirty Checking desabilitado
- [x] **Soft delete** — ativa = false
- [x] **Constraint UNIQUE composta** — (cliente_id, tipo) no banco

### BigDecimal e finanças

- [x] **BigDecimal.ZERO** — constante imutável para valor inicial
- [x] **saldo.add(limite)** — soma segura (nunca operador +)
- [x] **compareTo()** — comparação correta (nunca ==, >, <)
- [x] **precision = 19, scale = 2** — mapeamento correto
- [x] **Valor no body** — PATCH /limite → body, nunca query param

### Algoritmo de número de conta

- [x] **SEQUENCE** — atômico, thread-safe, gerenciado pelo banco
- [x] **String.format("%08d")** — zeros à esquerda sempre 8 dígitos
- [x] **Módulo 10** — algoritmo do dígito verificador
- [x] **NumeroContaService separado** — SRP, responsabilidade única

### Arquitetura REST

- [x] **POST** → 201 Created
- [x] **GET** → 200 OK
- [x] **PATCH** → 200 OK (atualização parcial)
- [x] **DELETE** → 204 No Content (soft delete)
- [x] **422** para regras de negócio vs **400** para validação
- [x] **503** para serviço dependente indisponível
- [x] **Endpoint dedicado de saldo** — /contas/{id}/saldo
- [x] **Busca por número** — /contas/numero/{ag}/{nr}

---

## 13. Estrutura de pacotes

```
conta-service/
│
├── src/main/java/com/tribunalbank/conta/
│   │
│   ├── ContaApplication.java          # @SpringBootApplication @EnableFeignClients
│   │
│   ├── client/
│   │   ├── ClienteClient.java         # @FeignClient interface
│   │   └── ClienteResponse.java       # DTO próprio do cliente-service
│   │
│   ├── config/
│   │   ├── FeignAuthConfig.java       # @Configuration → RequestInterceptor JWT
│   │   ├── FeignConfig.java           # sem @Configuration → Logger + ErrorDecoder
│   │   ├── JpaAuditingConfig.java     # @EnableJpaAuditing
│   │   ├── JwtConfig.java             # apenas JwtDecoder (chave pública)
│   │   ├── SecurityConfig.java        # duas filter chains
│   │   └── SwaggerConfig.java         # Bearer token no Swagger
│   │
│   ├── controller/
│   │   └── ContaController.java       # /contas/* endpoints
│   │
│   ├── dto/
│   │   ├── ContaRequest.java          # record {TipoConta, BigDecimal limite}
│   │   ├── ContaResponse.java         # record + from(Conta) + saldoDisponivel
│   │   ├── SaldoResponse.java         # record + from(Conta) + consultadoEm
│   │   └── LimiteRequest.java         # record {BigDecimal valor}
│   │
│   ├── entity/
│   │   ├── TipoConta.java             # enum CORRENTE, POUPANCA, SALARIO, INVESTIMENTO
│   │   └── Conta.java                 # @Getter @Setter (não @Data)
│   │
│   ├── exception/
│   │   ├── BusinessException.java     # abstract — base de negócio
│   │   ├── ContaNotFoundException.java    # 404
│   │   ├── ClienteNotFoundException.java  # 404 (do FeignClient)
│   │   ├── TipoContaJaExisteException.java # 409
│   │   ├── SaldoInsuficienteException.java # 422
│   │   ├── ContaInativaException.java     # 422
│   │   ├── ClienteServiceException.java   # 503 (RuntimeException, não Business)
│   │   └── GlobalExceptionHandler.java   # @Slf4j @RestControllerAdvice
│   │
│   ├── repository/
│   │   └── ContaRepository.java       # JpaRepository<Conta, String>
│   │
│   └── service/
│       ├── NumeroContaService.java    # @Slf4j — Módulo 10
│       └── ContaService.java          # @Slf4j — orquestra o negócio
│
├── src/main/resources/
│   ├── application.properties         # spring.application.name=conta-service
│   ├── application-dev.properties     # porta 8083, banco 5435, JWT, Feign
│   └── db/migration/
│       ├── V1__create_contas.sql      # tabela + SEQUENCE
│       └── V2__create_historico_saldo.sql
│
└── docker-compose.yml                 # PostgreSQL porta 5435
```

---

## 14. Tabela de decisões arquiteturais

| Decisão | Alternativa | Motivo |
|---|---|---|
| **FeignClient** para validar cliente | Confiar no JWT | Consistência em tempo real — cliente pode ter sido desativado |
| **SEQUENCE PostgreSQL** para número de conta | MAX + 1 no código | Thread-safe — impossível duplicar mesmo com concorrência |
| **BigDecimal** para valores monetários | Double ou Float | Precisão exata — Double tem erro de ponto flutuante |
| **@Getter + @Setter** em vez de @Data | @Data | Evita toString problemático em entidades JPA |
| **FeignConfig sem @Configuration** | @Configuration global | Escopo específico — não afeta outros FeignClients futuros |
| **FeignAuthConfig com @Configuration** | Dentro do FeignConfig | Interceptor JWT deve ser global — toda chamada precisa de auth |
| **ClienteResponse próprio** | Importar do cliente-service | Independência entre microsserviços — sem acoplamento de compilação |
| **log.warn() para negócio** | log.error() para tudo | Separação de severidade — alertas só para erros críticos |
| **log.error() para infraestrutura** | log.warn() | Erros de infraestrutura precisam de alerta imediato |
| **503 para ClienteServiceException** | 500 | Semântica correta — o erro é no serviço dependente, não aqui |
| **422 para regras de negócio** | 400 | 400 = requisição malformada, 422 = válida mas não processável |
| **Endpoint dedicado /saldo** | Retornar no GET /contas/{id} | Auditoria — consulta de saldo é operação específica rastreável |
| **Valor monetário no body (PATCH)** | Query param `?valor=` | Dados financeiros não devem aparecer em URLs (logs, proxies) |
| **Soft delete** (ativa = false) | DELETE físico | Regulação bancária — histórico obrigatório por lei |
| **usuarioId na Conta** | Só clienteId | Permite @PreAuthorize sem chamar o Cliente Service |

---

*Tribunal Bank API — Conta Service v1.0*
*Documentação de estudo — Junho 2026*