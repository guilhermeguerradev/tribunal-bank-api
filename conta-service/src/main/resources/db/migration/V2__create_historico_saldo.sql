-- ═══════════════════════════════════════════════════════════
-- MIGRATION V2 — Histórico de alterações de saldo
-- ═══════════════════════════════════════════════════════════
--
-- Registra toda alteração de saldo para auditoria.
-- Banco é obrigado por lei a manter esse histórico.
-- Esse histórico é lido pelo Transação Service no futuro.

CREATE TABLE historico_saldo (
                                 id              VARCHAR(36)   NOT NULL PRIMARY KEY,
                                 conta_id        VARCHAR(36)   NOT NULL
                                     REFERENCES contas(id) ON DELETE CASCADE,
                                 saldo_anterior  DECIMAL(19,2) NOT NULL,
                                 saldo_novo      DECIMAL(19,2) NOT NULL,
                                 motivo          VARCHAR(100)  NOT NULL,
                                 criado_em       TIMESTAMP     NOT NULL
);

CREATE INDEX idx_historico_conta_id ON historico_saldo(conta_id);
CREATE INDEX idx_historico_criado_em ON historico_saldo(criado_em);