-- ═══════════════════════════════════════════════════════════
-- MIGRATION V1 — Criação da tabela de usuários
-- ═══════════════════════════════════════════════════════════
--
-- CONVENÇÃO DE NOMENCLATURA DO FLYWAY:
-- V1__nome_descritivo.sql
-- ↑   ↑
-- │   └── dois underlines obrigatórios
-- └── número da versão — sempre crescente
--
-- O Flyway executa as migrations em ordem numérica
-- e nunca executa a mesma migration duas vezes
-- Ele controla isso na tabela flyway_schema_history
-- que ele mesmo cria no banco automaticamente
-- ═══════════════════════════════════════════════════════════

CREATE TABLE usuarios (

    -- UUID gerado pelo Java — impossível de adivinhar
    -- VARCHAR(36) porque UUID tem exatamente 36 caracteres
    -- Ex: "550e8400-e29b-41d4-a716-446655440000"
                          id VARCHAR(36) PRIMARY KEY,

    -- Email único — índice criado automaticamente pelo UNIQUE
    -- NOT NULL → campo obrigatório
                          email VARCHAR(255) NOT NULL UNIQUE,

    -- Senha SEMPRE armazenada como hash BCrypt
    -- Hash BCrypt tem sempre 60 caracteres
    -- Ex: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
                          senha VARCHAR(255) NOT NULL,

    -- Controla se o usuário pode acessar o sistema
    -- false = conta bloqueada pelo administrador
    -- DEFAULT true → todo usuário nasce ativo
                          ativo BOOLEAN NOT NULL DEFAULT TRUE,

    -- Preenchido automaticamente pelo Spring Data (@CreatedDate)
    -- updatable = false na entidade → nunca alterado depois da criação
                          criado_em TIMESTAMP NOT NULL,

    -- Atualizado automaticamente pelo Spring Data (@LastModifiedDate)
    -- sempre que o registro for salvo
                          atualizado_em TIMESTAMP NOT NULL
);

-- Índice no email para acelerar buscas por email
-- O login faz SELECT * FROM usuarios WHERE email = ?
-- Com índice essa busca é O(log n) em vez de O(n)
CREATE INDEX idx_usuarios_email ON usuarios(email);

-- Índice no campo ativo para acelerar filtros de usuários ativos
CREATE INDEX idx_usuarios_ativo ON usuarios(ativo);