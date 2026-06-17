-- ═══════════════════════════════════════════════════════════
-- MIGRATION V3 — Criação da tabela de refresh tokens
-- ═══════════════════════════════════════════════════════════
--
-- Armazena os tokens de longa duração (7 dias)
-- Permite revogar sessões sem esperar o token expirar
--
-- Exemplo de dados:
-- id       | token        | usuario_id | expiracao           | revogado
-- uuid-1   | 550e8400...  | abc-123    | 2026-06-24 14:30:00 | false
-- uuid-2   | 661f9511...  | abc-123    | 2026-06-24 14:30:00 | true  ← revogado
-- uuid-3   | 772g0622...  | def-456    | 2026-06-24 14:30:00 | false
-- ═══════════════════════════════════════════════════════════

CREATE TABLE refresh_tokens (

    -- UUID gerado pelo Java
                                id VARCHAR(36) PRIMARY KEY,

    -- O token em si — UUID aleatório gerado no login
    -- UNIQUE → cada token é único no banco
    -- Ex: "550e8400-e29b-41d4-a716-446655440000"
                                token VARCHAR(255) NOT NULL UNIQUE,

    -- Chave estrangeira para o usuário dono do token
    -- ON DELETE CASCADE → se o usuário for deletado,
    -- todos os seus tokens são deletados automaticamente
                                usuario_id VARCHAR(36) NOT NULL
                                    REFERENCES usuarios(id) ON DELETE CASCADE,

    -- Data e hora de expiração do token
    -- Gerado no login: agora + 7 dias
    -- Verificado antes de aceitar o token para renovação
                                expiracao TIMESTAMP NOT NULL,

    -- Controla se o token foi invalidado manualmente
    -- false → válido, pode ser usado
    -- true  → revogado, rejeitado mesmo que não tenha expirado
    -- DEFAULT false → todo token nasce válido
                                revogado BOOLEAN NOT NULL DEFAULT FALSE
);

-- Índice no token para acelerar a busca por token
-- O refresh usa SELECT * FROM refresh_tokens WHERE token = ?
-- Com índice essa busca é instantânea
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

-- Índice no usuario_id para acelerar a revogação em massa
-- revogarTodosPorUsuario faz UPDATE WHERE usuario_id = ?
CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens(usuario_id);

-- Índice no campo revogado para filtrar tokens válidos rapidamente
CREATE INDEX idx_refresh_tokens_revogado ON refresh_tokens(revogado);