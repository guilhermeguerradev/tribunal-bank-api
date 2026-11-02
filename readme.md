# tribunal-bank-api

Sistema bancário em arquitetura de microsserviços com Java 21 e Spring Boot, desenvolvido para praticar back-end corporativo e para a preparação para concursos públicos (TRT / TJMG).

## Destaques

- **Service discovery** com Eureka: os serviços se registram e são encontrados pelo nome
- **API Gateway** (Spring Cloud Gateway) como ponto único de entrada
- **Autenticação com JWT assinado por RSA**, perfis de acesso (roles) e refresh token
- **Migrações de banco versionadas** com Flyway em cada serviço
- **Tratamento global de erros** com exceções de negócio próprias
- **README de estudo por serviço**, explicando cada decisão técnica

## Tecnologias

- Java 21
- Spring Boot 4 e Spring Cloud 2025.1 (Eureka, Gateway)
- Spring Security, JWT (RSA)
- Spring Data JPA, PostgreSQL, Flyway
- Maven

## Microsserviços

| Serviço | Porta | Descrição | Status |
| --- | --- | --- | --- |
| [discovery-server](discovery-server) | 8761 | Registro e descoberta de serviços (Eureka) | Pronto |
| [api-gateway](api-gateway) | 8080 | Ponto único de entrada e roteamento | Pronto (validação de JWT no gateway em andamento) |
| [auth-service](auth-service/readme.md) | 8081 | Autenticação, JWT RSA, roles e refresh token | Pronto |
| [cliente-service](cliente-service/readme.md) | 8082 | Cadastro de clientes e endereços | Pronto |
| [conta-service](conta-service/readme.md) | 8083 | Contas bancárias, saldo e limites | Pronto |
| transacao-service | 8084 | PIX, TED, saque e depósito | Planejado |

## Como executar

Pré-requisitos: Java 21, Maven e PostgreSQL rodando localmente (um banco por serviço, conforme o `application-dev.properties` de cada um).

```bash
git clone https://github.com/guilhermeguerradev/tribunal-bank-api.git
cd tribunal-bank-api

# Suba os serviços nesta ordem, cada um em um terminal:
cd discovery-server && mvn spring-boot:run
cd auth-service     && mvn spring-boot:run
cd cliente-service  && mvn spring-boot:run
cd conta-service    && mvn spring-boot:run
cd api-gateway      && mvn spring-boot:run
```

Depois de subir, o painel do Eureka fica em `http://localhost:8761` e todas as chamadas passam pelo gateway em `http://localhost:8080`.

## Próximos passos

- [ ] `transacao-service` (PIX, TED, saque e depósito)
- [ ] Validação do JWT no API Gateway
- [ ] `docker-compose.yml` para subir todos os serviços e bancos com um comando
- [ ] Testes de integração com Testcontainers

## Autor

**Guilherme Guerra de Paulo**, desenvolvedor back-end Java.
[LinkedIn](https://www.linkedin.com/in/guilhermeguerradepaulo) · [GitHub](https://github.com/guilhermeguerradev)
