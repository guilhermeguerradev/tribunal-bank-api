package com.tribunalbank.auth.service;

import com.tribunalbank.auth.dto.AuthResponse;
import com.tribunalbank.auth.dto.LoginRequest;
import com.tribunalbank.auth.dto.RefreshRequest;
import com.tribunalbank.auth.dto.RegisterRequest;
import com.tribunalbank.auth.entity.RefreshToken;
import com.tribunalbank.auth.entity.Role;
import com.tribunalbank.auth.entity.Usuario;
import com.tribunalbank.auth.exception.EmailJaCadastradoException;
import com.tribunalbank.auth.exception.UsuarioNotFoundException;
import com.tribunalbank.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

// ═══════════════════════════════════════════════════════════
// AUTH SERVICE — Orquestrador principal da autenticação
// ═══════════════════════════════════════════════════════════
//
// Esse é o service mais importante do Auth Service.
// Ele orquestra todos os outros componentes para realizar
// as operações de autenticação.
//
// DEPENDÊNCIAS QUE ELE USA:
// → UsuarioRepository   para buscar/salvar usuários no banco
// → PasswordEncoder     para criptografar senhas com BCrypt
// → AuthenticationManager para validar email + senha no login
// → JwtService          para gerar o access token JWT
// → RefreshTokenService para gerenciar os refresh tokens
//
// OPERAÇÕES:
// → cadastrar()  POST /auth/register
// → login()      POST /auth/login
// → refresh()    POST /auth/refresh
// → logout()     POST /auth/logout
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // ═══════════════════════════════════════════════════════
    // CADASTRAR — Registra novo usuário no sistema
    // ═══════════════════════════════════════════════════════
    //
    // FLUXO:
    // 1. Verifica se o email já existe no banco
    // 2. Criptografa a senha com BCrypt
    // 3. Cria o usuário com ROLE_USER por padrão
    // 4. Salva no banco
    // 5. Gera os tokens e retorna AuthResponse
    //
    // Por que já retorna os tokens no cadastro?
    // → Melhor experiência — usuário cadastrou e já está logado
    // → Padrão adotado por Nubank, Inter, C6 e similares
    //
    // @Transactional → se qualquer passo falhar,
    // nada é salvo no banco — atomicidade garantida
    @Transactional
    public AuthResponse cadastrar(RegisterRequest request) {

        // 1. Verifica email duplicado
        // Lança EmailJaCadastradoException → GlobalExceptionHandler → 422
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException(request.email());
        }

        // 2. Cria o usuário com senha criptografada
        // passwordEncoder.encode() → BCrypt hash irreversível
        // ROLE_USER → toda conta nova começa com permissão básica
        // Um admin pode promover depois via endpoint específico
        Usuario usuario = Usuario.builder()
                .email(request.email())
                .senha(passwordEncoder.encode(request.senha()))
                .ativo(true)
                .roles(Set.of(Role.ROLE_USER))
                .build();

        // 3. Salva no banco
        // @CreatedDate e @LastModifiedDate preenchidos automaticamente
        // pelo JpaAuditingConfig
        usuarioRepository.save(usuario);

        // 4. Gera e retorna os tokens
        return gerarAuthResponse(usuario);
    }

    // ═══════════════════════════════════════════════════════
    // LOGIN — Autentica usuário existente
    // ═══════════════════════════════════════════════════════
    //
    // FLUXO:
    // 1. AuthenticationManager valida email + senha
    // 2. Busca o usuário no banco pelo email
    // 3. Gera os tokens e retorna AuthResponse
    //
    // Por que usamos AuthenticationManager em vez de
    // buscar o usuário e comparar a senha manualmente?
    //
    // O AuthenticationManager já faz tudo isso internamente:
    // → Busca o usuário pelo email (via UserDetailsService)
    // → Compara a senha com BCrypt
    // → Verifica se a conta está ativa
    // → Lança exceção específica para cada problema:
    //   BadCredentialsException → email ou senha incorretos
    //   DisabledException       → conta desativada
    //   LockedException         → conta bloqueada
    //
    // Todas essas exceções são capturadas pelo GlobalExceptionHandler
    @Transactional
    public AuthResponse login(LoginRequest request) {

        // 1. Valida as credenciais
        // Se email ou senha estiverem errados lança BadCredentialsException
        // que o GlobalExceptionHandler converte para 401
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.senha()
                )
        );

        // 2. Busca o usuário — nesse ponto sabemos que existe
        // pois o authenticationManager já validou
        Usuario usuario = usuarioRepository
                .findByEmail(request.email())
                .orElseThrow(() ->
                        new UsuarioNotFoundException("Usuário não encontrado")
                );

        // 3. Gera e retorna os tokens
        return gerarAuthResponse(usuario);
    }

    // ═══════════════════════════════════════════════════════
    // REFRESH — Renova o access token expirado
    // ═══════════════════════════════════════════════════════
    //
    // FLUXO:
    // 1. Valida o refresh token (existe? não expirou? não revogado?)
    // 2. Revoga o refresh token antigo (Refresh Token Rotation)
    // 3. Gera novo access token + novo refresh token
    // 4. Retorna AuthResponse com os novos tokens
    //
    // REFRESH TOKEN ROTATION:
    // O token antigo é revogado e um novo é gerado
    // Se um atacante roubar o refresh token e tentar usá-lo
    // depois que o usuário legítimo já renovou, ele é rejeitado
    // porque o token já foi marcado como revogado
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {

        // 1. Valida o token — lança TokenInvalidoException se inválido
        RefreshToken refreshToken = refreshTokenService
                .validar(request.refreshToken());

        // 2. Revoga o token antigo — Refresh Token Rotation
        refreshTokenService.revogar(request.refreshToken());

        // 3. Gera novos tokens para o usuário dono do token
        return gerarAuthResponse(refreshToken.getUsuario());
    }

    // ═══════════════════════════════════════════════════════
    // LOGOUT — Revoga o refresh token do dispositivo atual
    // ═══════════════════════════════════════════════════════
    //
    // O access token expira naturalmente em 15 minutos
    // O refresh token é revogado imediatamente no banco
    //
    // Por que não invalidamos o access token?
    // JWT é stateless — não tem como invalidar antes de expirar
    // Por isso o access token tem vida curta (15 minutos)
    // O dano máximo de um token roubado é de 15 minutos
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revogar(refreshToken);
    }

    // ═══════════════════════════════════════════════════════
    // MÉTODO PRIVADO — Monta o AuthResponse
    // ═══════════════════════════════════════════════════════
    //
    // Centraliza a geração dos tokens para evitar repetição
    // Usado por: cadastrar(), login() e refresh()
    //
    // FLUXO:
    // 1. Converte as roles do Set para String separada por vírgula
    //    Ex: Set{ROLE_USER, ROLE_ADMIN} → "ROLE_USER,ROLE_ADMIN"
    // 2. Gera o access token JWT com email e roles
    // 3. Gera e salva o refresh token no banco
    // 4. Monta e retorna o AuthResponse
    private AuthResponse gerarAuthResponse(Usuario usuario) {

        // 1. Converte roles para String
        // O JWT guarda as roles como String no payload
        // Ex: "ROLE_USER,ROLE_ADMIN"
        String roles = usuario.getRoles()
                .stream()
                .map(Role::name)           // Role.ROLE_USER → "ROLE_USER"
                .collect(Collectors.joining(","));

        // 2. Gera o access token JWT — válido por 15 minutos
        String accessToken = jwtService.gerarToken(
                usuario.getEmail(),
                roles
        );

        // 3. Gera e salva novo refresh token — válido por 7 dias
        RefreshToken refreshToken = refreshTokenService.criar(usuario);

        // 4. Monta e retorna a resposta
        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                "Bearer",
                usuario.getEmail()
        );
    }
}