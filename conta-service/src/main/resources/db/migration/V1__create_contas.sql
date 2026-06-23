-- ═══════════════════════════════════════════════════════════
-- MIGRATION V1 — Criação da tabela de contas bancárias
-- ═══════════════════════════════════════════════════════════

CREATE TABLE contas (
                        id                VARCHAR(36)    NOT NULL PRIMARY KEY,
                        cliente_id        VARCHAR(36)    NOT NULL,
                        numero_agencia    VARCHAR(4)     NOT NULL,
                        numero_conta      VARCHAR(8)     NOT NULL,
                        digito_verificador VARCHAR(1)   NOT NULL,
                        tipo              VARCHAR(20)    NOT NULL,
                        saldo             DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
                        limite            DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
                        ativa             BOOLEAN        NOT NULL DEFAULT true,
                        criado_em         TIMESTAMP      NOT NULL,
                        atualizado_em     TIMESTAMP      NOT NULL,
                        usuario_id        VARCHAR(36)    NOT NULL,


    -- Número completo único no sistema
                        CONSTRAINT uk_numero_conta UNIQUE (numero_agencia, numero_conta),

    -- Um cliente só pode ter uma conta de cada tipo
                        CONSTRAINT uk_cliente_tipo UNIQUE (cliente_id, tipo)
);

CREATE SEQUENCE seq_numero_conta
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;

CREATE INDEX idx_contas_cliente_id ON contas(cliente_id);
CREATE INDEX idx_contas_tipo       ON contas(tipo);