-- ═══════════════════════════════════════════════════════════
-- MIGRATION V1 — Criação da tabela de clientes
-- ═══════════════════════════════════════════════════════════

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

-- Índices para as buscas mais comuns
CREATE INDEX idx_clientes_cpf   ON clientes(cpf);
CREATE INDEX idx_clientes_email ON clientes(email);
CREATE INDEX idx_clientes_nome  ON clientes(nome);