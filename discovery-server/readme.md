# Discovery Server — Eureka
 
Servidor de registro e descoberta de serviços do **tribunal-bank-api**.  
Todos os microsserviços do projeto se registram aqui e se localizam através dele.
 
---
 
## Tecnologias
 
- Java 21
- Spring Boot 4.0.7
- Spring Cloud 2025.1.2
- Spring Cloud Netflix Eureka Server
---
 
## Como funciona
 
O Eureka Server funciona como um "cartório" de serviços. Quando um microsserviço sobe, ele se registra aqui com seu nome e endereço. Quando outro serviço precisa se comunicar com ele, consulta o Eureka para saber onde encontrá-lo — sem precisar de IPs ou portas fixas no código.
 
```
Auth Service     ──┐
Cliente Service  ──┤
Conta Service    ──┼──► Eureka Server (8761)
Transação Service──┤
API Gateway      ──┘
```
 
---
 
## Estrutura do projeto
 
```
discovery-server/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/tribunalbank/discovery/
│       │       └── DiscoveryServerApplication.java
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
| Artifact | discovery-server |
| Package name | com.tribunalbank.discovery |
| Packaging | Jar |
| Java | 21 |
 
**Dependência adicionada:**
- Eureka Server `(Spring Cloud Discovery)`
---
 
### 2. Anotação na classe principal
 
Adicionado `@EnableEurekaServer` na classe principal para ativar o servidor:
 
```java
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```
 
---
 
### 3. Configuração dos profiles
 
O projeto usa três arquivos de configuração separados por ambiente.
 
**`application.properties`** — configurações comuns a todos os ambientes:
 
```properties
# Nome do serviço — usado pelo Eureka para identificar esta instância no dashboard
spring.application.name=discovery-server
 
# Profile ativo — trocar para prod no Docker e na AWS
spring.profiles.active=dev
 
# Impede que o próprio servidor Eureka tente se registrar nele mesmo como cliente
# Sem isso ele entra em loop tentando se registrar em si próprio
eureka.client.register-with-eureka=false
 
# Impede que o servidor tente buscar a lista de serviços registrados em outro Eureka
# Só faz sentido em configurações com múltiplos servidores Eureka (alta disponibilidade)
# No nosso projeto temos apenas um servidor, então desabilitamos
eureka.client.fetch-registry=false
```
 
**`application-dev.properties`** — configurações de desenvolvimento:
 
```properties
# Porta onde o servidor Eureka vai rodar
server.port=8761
 
# Remove o delay de 30s que o Eureka aguarda na inicialização antes de aceitar registros
# Em desenvolvimento isso acelera muito o startup
eureka.server.wait-time-in-ms-when-sync-empty=0
 
# Logs detalhados para facilitar depuração local
logging.level.com.netflix.eureka=DEBUG
logging.level.com.netflix.discovery=DEBUG
```
 
**`application-prod.properties`** — configurações de produção:
 
```properties
# Porta onde o servidor Eureka vai rodar
server.port=8761
 
# Aguarda 30s antes de aceitar registros — garante que o servidor
# está completamente pronto antes de receber os microsserviços
eureka.server.wait-time-in-ms-when-sync-empty=30000
 
# Apenas erros em produção — evita poluição nos logs
logging.level.com.netflix.eureka=ERROR
logging.level.com.netflix.discovery=ERROR
```
 
---
 
### 4. Como ativar o profile
 
**Na IDE (IntelliJ):**
```
Edit Configurations → Environment Variables → spring.profiles.active=dev
```
 
**Na linha de comando:**
```bash
java -jar discovery-server.jar --spring.profiles.active=prod
```
 
**Via Docker (será configurado na etapa de Docker Compose):**
```yaml
environment:
  - SPRING_PROFILES_ACTIVE=prod
```
 
---
 
## Como executar localmente
 
```bash
# Clonar o repositório
git clone https://github.com/seu-usuario/tribunal-bank-api.git
cd tribunal-bank-api/discovery-server
 
# Rodar com Maven
./mvnw spring-boot:run
```
 
Acesse o dashboard do Eureka em:
 
```
http://localhost:8761
```
 
Se aparecer a mensagem **"No instances available"**, o servidor está funcionando corretamente e aguardando os microsserviços se registrarem.
 
---
 
## Observações
 
- O discovery server deve ser o **primeiro serviço a subir** — todos os outros dependem dele para se registrar
- Em produção o profile `dev` deve ser substituído por `prod` via variável de ambiente no Docker ou AWS
- A porta `8761` é o padrão da comunidade para Eureka — não alterar sem necessidade