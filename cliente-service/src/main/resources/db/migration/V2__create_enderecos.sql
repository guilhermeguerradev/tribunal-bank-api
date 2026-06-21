-- ═══════════════════════════════════════════════════════════
-- MIGRATION V2 — Criação da tabela de endereços
-- ═══════════════════════════════════════════════════════════

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

    -- Um cliente só pode ter um endereço de cada tipo (RESIDENCIAL ou COMERCIAL)
                           CONSTRAINT uk_cliente_tipo UNIQUE (cliente_id, tipo)
);