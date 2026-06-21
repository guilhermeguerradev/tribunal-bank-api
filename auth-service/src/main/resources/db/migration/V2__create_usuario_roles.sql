-- ═══════════════════════════════════════════════════════════
-- MIGRATION V2 — Criação da tabela de roles dos usuários
-- ═══════════════════════════════════════════════════════════
--
-- Essa tabela é gerada pelo @ElementCollection da entidade Usuario
-- Cada linha representa uma role de um usuário
--
-- Exemplo de dados:
-- usuario_id                           | role
-- 550e8400-e29b-41d4-a716-446655440000 | ROLE_USER
-- 550e8400-e29b-41d4-a716-446655440000 | ROLE_ADMIN
-- 661f9511-f30c-52e5-b827-557766551111 | ROLE_USER
-- ═══════════════════════════════════════════════════════════

CREATE TABLE usuario_roles (

    -- Chave estrangeira referenciando o usuário dono dessa role
    -- ON DELETE CASCADE → se o usuário for deletado,
    -- todas as suas roles são deletadas automaticamente
                               usuario_id VARCHAR(36) NOT NULL
                                   REFERENCES usuarios(id) ON DELETE CASCADE,

    -- Nome da role armazenada como String
    -- Salvo assim por causa do @Enumerated(EnumType.STRING)
    -- Valores possíveis: ROLE_USER, ROLE_ADMIN, ROLE_GERENTE...
                               role VARCHAR(50) NOT NULL,

    -- Chave primária composta — garante que um usuário
    -- não pode ter a mesma role duas vezes no banco
    -- Complementa a garantia do HashSet na entidade Java
                               PRIMARY KEY (usuario_id, role)
);