# tribunal-bank-api

Sistema bancário com microsserviços em Java e Spring Boot, desenvolvido para validação de conhecimentos técnicos e preparação para concursos públicos (TRT / TJMG).

---

## Tecnologias

- Java 21
- Spring Boot 4.0.7
- Spring Cloud 2025.1 (Oakwood)
- PostgreSQL
- Docker / Docker Compose
- Maven

---

## Microsserviços

| Serviço           | Porta | Descrição                                    |
| ----------------- | ----- | -------------------------------------------- |
| discovery-server  | 8761  | Registro e descoberta de serviços (Eureka)   |
| api-gateway       | 8080  | Ponto único de entrada, roteamento e JWT     |
| auth-service      | 8081  | Autenticação, JWT RSA, roles e refresh token |
| cliente-service   | 8082  | Cadastro de clientes e endereços             |
| conta-service     | 8083  | Contas bancárias, saldo e limites            |
| transacao-service | 8084  | PIX, TED, saque e depósito                   |

---

## Como executar

```bash
# Clonar o repositório
git clone https://github.com/seu-usuario/tribunal-bank-api.git
cd tribunal-bank-api

# Subir todos os serviços
docker-compose up -d
```

---

## Status do projeto

🚧 Em desenvolvimento
