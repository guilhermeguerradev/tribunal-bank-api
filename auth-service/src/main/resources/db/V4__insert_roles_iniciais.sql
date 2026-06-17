-- ═══════════════════════════════════════════════════════════
-- MIGRATION V4 — Inserção do usuário administrador padrão
-- ═══════════════════════════════════════════════════════════
--
-- Cria o usuário admin inicial do sistema
-- IMPORTANTE: em produção a senha deve ser trocada imediatamente
--
-- Senha: admin123
-- Hash BCrypt gerado com strength 10
-- ═══════════════════════════════════════════════════════════

INSERT INTO usuarios (id, email, senha, ativo, criado_em, atualizado_em)
VALUES (
           '00000000-0000-0000-0000-000000000001',
           'admin@tribunalbank.com',
           -- Hash BCrypt de 'admin123' — nunca salvar senha em texto puro!
           '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
           TRUE,
           NOW(),
           NOW()
       );

-- Atribui a role de admin ao usuário criado acima
INSERT INTO usuario_roles (usuario_id, role)
VALUES (
           '00000000-0000-0000-0000-000000000001',
           'ROLE_ADMIN'
       );