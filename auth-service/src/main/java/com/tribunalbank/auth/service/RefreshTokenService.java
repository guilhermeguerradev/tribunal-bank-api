package com.tribunalbank.auth.service;

import com.tribunalbank.auth.entity.RefreshToken;
import com.tribunalbank.auth.entity.Usuario;
import com.tribunalbank.auth.exception.TokenInvalidoException;
import com.tribunalbank.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

// ═══════════════════════════════════════════════════════════
// REFRESH TOKEN SERVICE
// ═══════════════════════════════════════════════════════════
//
// Gerencia todo o ciclo de vida dos refresh tokens:
// → Criar novo token após login
// → Validar token recebido para renovação
// → Revogar token no logout
// → Revogar todos os tokens do usuário
//
// REFRESH TOKEN ROTATION — conceito de segurança:
// Toda vez que o cliente usa o refresh token para renovar
// o access token, o refresh token antigo é revogado e
// um novo é gerado. Isso significa que cada refresh token
// só pode ser usado UMA vez. Se um atacante roubar o token
// e tentar usá-lo depois que o cliente já usou, ele é rejeitado.
// ═══════════════════════════════════════════════════════════
@Service

// @RequiredArgsConstructor → Lombok gera o construtor com todos
// os campos final automaticamente — substitui o @Autowired
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    // Tempo de expiração lido do .env via application.properties
    // jwt.refresh-token-expiration=${JWT_REFRESH_TOKEN_EXPIRATION:604800000}
    // Valor em milissegundos — 604800000 = 7 dias
    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // ═══════════════════════════════════════════════════════
    // CRIAR — Gera e salva um novo refresh token
    // ═══════════════════════════════════════════════════════
    //
    // Chamado em dois momentos:
    // → No login (primeiro token)
    // → Na renovação (token de rotação — substitui o antigo)
    //
    // @Transactional → garante que o save é atômico
    // Se der erro o token não é salvo pela metade
    @Transactional
    public RefreshToken criar(Usuario usuario) {
        RefreshToken refreshToken = RefreshToken.builder()
                // UUID aleatório — impossível de adivinhar ou forçar
                .token(UUID.randomUUID().toString())
                .usuario(usuario)
                // agora + 7 dias em milissegundos convertidos para segundos
                .expiracao(LocalDateTime.now()
                        .plusSeconds(refreshTokenExpiration / 1000))
                .revogado(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    // ═══════════════════════════════════════════════════════
    // VALIDAR — Verifica se o token pode ser usado
    // ═══════════════════════════════════════════════════════
    //
    // Lança exceção específica para cada tipo de problema
    // O GlobalExceptionHandler converte para 401 automaticamente
    //
    // ORDEM DAS VERIFICAÇÕES (importante):
    // 1. Existe no banco?    → se não, token falso ou expirado e deletado
    // 2. Foi revogado?       → logout ou rotação já aconteceu
    // 3. Não expirou?        → 7 dias passaram, precisa fazer login de novo
    public RefreshToken validar(String token) {
        // 1. Busca no banco — retorna vazio se não existir
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(token)
                .orElseThrow(() ->
                        new TokenInvalidoException("Refresh token não encontrado")
                );

        // 2. Verifica se foi revogado manualmente
        // (logout, troca de senha, sair de todos os dispositivos)
        if (refreshToken.isRevogado()) {
            throw new TokenInvalidoException("Refresh token já foi revogado");
        }

        // 3. Verifica se ainda está dentro do prazo de 7 dias
        if (refreshToken.getExpiracao().isBefore(LocalDateTime.now())) {
            throw new TokenInvalidoException(
                    "Refresh token expirado — faça login novamente"
            );
        }

        return refreshToken;
    }

    // ═══════════════════════════════════════════════════════
    // REVOGAR — Invalida um token específico
    // ═══════════════════════════════════════════════════════
    //
    // Usado no logout simples — invalida só o token daquele dispositivo
    // O usuário continua logado em outros dispositivos
    //
    // ifPresent → não lança exceção se o token não existir
    //             logout é uma operação idempotente —
    //             chamar duas vezes tem o mesmo resultado
    @Transactional
    public void revogar(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevogado(true);
            refreshTokenRepository.save(rt);
        });
    }

    // ═══════════════════════════════════════════════════════
    // REVOGAR TODOS — Invalida todos os tokens do usuário
    // ═══════════════════════════════════════════════════════
    //
    // Usado em:
    // → "Sair de todos os dispositivos"
    // → Troca de senha (boa prática de segurança)
    // → Administrador bloqueia o usuário
    //
    // Um único UPDATE no banco — muito mais eficiente
    // do que buscar todos os tokens e atualizar um por um
    @Transactional
    public void revogarTodos(Usuario usuario) {
        refreshTokenRepository.revogarTodosPorUsuario(usuario);
    }
}