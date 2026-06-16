# API Gateway
 
Ponto único de entrada do **tribunal-bank-api**.  
Todas as requisições externas passam por aqui antes de serem roteadas para o microsserviço correto.
 
---
 
## Tecnologias
 
- Java 21
- Spring Boot 4.0.7
- Spring Cloud 2025.1.2
- Spring Cloud Gateway (WebMVC)
- Spring Cloud Netflix Eureka Client
- Spring Boot Actuator
---
 
## Como funciona
 
O Gateway recebe todas as requisições externas e as roteia automaticamente para o microsserviço correto consultando o Eureka — sem precisar de IPs ou portas fixas nas rotas.
 
```
Cliente / Internet
       ↓
  API Gateway (8080)
       ↓
  Consulta Eureka (8761)
       ↓
  ┌────────────────────────┐
  │  auth-service    (8081)│
  │  cliente-service (8082)│
  │  conta-service   (8083)│
  │  transacao-service(8084│
  └────────────────────────┘
```
 
---
 
## Estrutura do projeto
 
```
api-gateway/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/tribunalbank/gateway/
│       │       └── ApiGatewayApplication.java
│       └── resources/
│           ├── application.properties
│           ├── application-dev.properties
│           └── application-prod.properties
└── pom.xml
```
 
---
 
## Passo a passo — como foi configurado
 
### 1. Geração do projeto
 
Projeto gerado no [Spring Initializr](https://start.spring.io) com as seguintes configurações:
 
| Campo | Valor |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | 4.0.7 |
| Group | com.tribunalbank |
| Artifact | api-gateway |
| Package name | com.tribunalbank.gateway |
| Packaging | Jar |
| Java | 21 |
 
**Dependências adicionadas:**
 
| Dependência | Finalidade |
|---|---|
| Gateway | Roteamento das requisições |
| Eureka Discovery Client | Registro e descoberta de serviços |
| Spring Boot Actuator | Monitoramento da saúde do gateway |
 
---
 
### 2. Correção do pom.xml
 
O Initializr gerou a dependência de teste incorreta. Foi corrigida de:
 
```xml
<!-- ERRADO — dependência não existe -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator-test</artifactId>
    <scope>test</scope>
</dependency>
```
 
Para:
 
```xml
<!-- CORRETO -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```
 
---
 
### 3. Configuração dos profiles
 
**`application.properties`** — configurações comuns a todos os ambientes:
 
```properties
# Nome do serviço — usado pelo Eureka para identificar o gateway
spring.application.name=api-gateway
 
# Profile ativo — trocar para prod no Docker e na AWS
spring.profiles.active=dev
 
# Endereço do Eureka — gateway se registra aqui ao subir
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
 
# Habilita o roteamento automático via nome dos serviços registrados no Eureka
spring.cloud.gateway.server.webmvc.discovery.locator.enabled=true
 
# Converte o nome dos serviços para minúsculo na URL
# Exemplo: acessa /auth-service/** em vez de /AUTH-SERVICE/**
spring.cloud.gateway.server.webmvc.discovery.locator.lower-case-service-id=true
```
 
**`application-dev.properties`** — configurações de desenvolvimento:
 
```properties
# Porta do gateway — ponto único de entrada do sistema
server.port=8080
 
# Logs detalhados para ver o roteamento em desenvolvimento
logging.level.org.springframework.cloud.gateway=DEBUG
logging.level.com.tribunalbank=DEBUG
```
 
**`application-prod.properties`** — configurações de produção:
 
```properties
# Porta do gateway
server.port=8080
 
# Apenas erros em produção — evita poluição nos logs
logging.level.org.springframework.cloud.gateway=ERROR
logging.level.com.tribunalbank=ERROR
```
 
---
 
### 4. Como o roteamento automático funciona
 
Com `discovery.locator.enabled=true` o gateway roteia automaticamente pelo nome do serviço registrado no Eureka:
 
```
GET http://localhost:8080/auth-service/auth/login
                              ↓
              Consulta Eureka pelo nome "auth-service"
                              ↓
              Roteia para http://localhost:8081/auth/login
```
 
Não é necessário configurar rotas manualmente para cada serviço — o Eureka resolve tudo automaticamente.
 
---
 
## Como executar localmente
 
> ⚠️ O Discovery Server deve estar rodando antes de subir o Gateway.
 
```bash
# Na pasta do discovery-server (se ainda não estiver rodando)
cd discovery-server
./mvnw spring-boot:run
 
# Na pasta do api-gateway
cd api-gateway
./mvnw spring-boot:run
```
 
Verifique se o gateway apareceu no dashboard do Eureka:
 
```
http://localhost:8761
```
 
---
 
## Observações
 
- O Gateway deve ser o **segundo serviço a subir**, logo após o Discovery Server
- Toda requisição externa deve passar pelo Gateway — nunca acessar os microsserviços diretamente
- Em produção o profile `dev` deve ser substituído por `prod` via variável de ambiente no Docker ou AWS
- A porta `8080` é o padrão para o ponto de entrada da aplicação
 