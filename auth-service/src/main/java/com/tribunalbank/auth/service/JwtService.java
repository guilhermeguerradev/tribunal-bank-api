package com.tribunalbank.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

// ═══════════════════════════════════════════════════════════
// JWT SERVICE — Responsável por gerar os access tokens JWT
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA:
// Essa classe faz apenas uma coisa — gera tokens JWT assinados
// com a chave privada RSA configurada no JwtConfig
//
// QUEM USA ESSE SERVICE:
// AuthService → chama gerarToken() após validar as credenciais
//
// O QUE É UM JWT?
// É um token composto por 3 partes separadas por ponto:
//
// eyJhbGciOiJSUzI1NiJ9          ← Header  (algoritmo)
// .
// eyJzdWIiOiJqb2FvQC4uLiJ9      ← Payload (dados = claims)
// .
// SflKxwRJSMeKKF2QT4fwpMeJf36P  ← Signature (assinatura RSA)
//
// CLAIMS são os dados dentro do payload:
// {
//   "sub": "joao@tribunalbank.com",  ← subject (quem é)
//   "roles": ["ROLE_USER"],          ← permissões
//   "iat": 1718640000,               ← issued at (quando foi gerado)
//   "exp": 1718640900                ← expiration (quando expira)
// }
//
// IMPORTANTE:
// O payload do JWT NÃO é criptografado — qualquer um pode ler
// Mas NÃO pode ser adulterado — a assinatura RSA garante isso
// NUNCA coloque senha ou dados sensíveis no payload do JWT
// ═══════════════════════════════════════════════════════════
@Service
public class JwtService {

    // JwtEncoder configurado no JwtConfig
    // Injeta automaticamente via @Bean
    private final JwtEncoder jwtEncoder;

    // Tempo de expiração lido do .env via application.properties
    // jwt.access-token-expiration=${JWT_ACCESS_TOKEN_EXPIRATION:900000}
    // Valor em milissegundos — 900000 = 15 minutos
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    public JwtService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    // ═══════════════════════════════════════════════════════
    // GERAR TOKEN — Cria e assina um JWT para o usuário
    // ═══════════════════════════════════════════════════════
    //
    // PARÂMETROS:
    // email  → identificador único do usuário (subject do token)
    // roles  → permissões do usuário para autorização nos endpoints
    //
    // RETORNO:
    // String com o JWT completo pronto para enviar ao cliente
    // Ex: "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOi..."
    //
    // FLUXO:
    // 1. Define o momento atual e o momento de expiração
    // 2. Monta os claims (dados que ficam no payload)
    // 3. JwtEncoder assina com a chave privada RSA
    // 4. Retorna o token como String
    public String gerarToken(String email, String roles) {

        // Momento exato em que o token está sendo gerado
        Instant agora = Instant.now();

        // Momento de expiração = agora + 15 minutos (em segundos)
        // accessTokenExpiration está em milissegundos no .env
        // Instant usa segundos → dividimos por 1000
        Instant expiracao = agora.plusSeconds(accessTokenExpiration / 1000);

        // JwtClaimsSet → conjunto de claims que vão no payload do JWT
        //
        // issuer    → quem emitiu o token (nosso serviço)
        // issuedAt  → quando foi emitido
        // expiresAt → quando expira
        // subject   → quem é o dono do token (email do usuário)
        // claim     → dados extras — aqui adicionamos as roles
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("tribunal-bank-auth")     // identifica nosso serviço
                .issuedAt(agora)                   // timestamp de geração
                .expiresAt(expiracao)              // timestamp de expiração
                .subject(email)                    // email do usuário
                .claim("roles", roles)             // roles para autorização
                .build();

        // JwtEncoderParameters → empacota os claims para o encoder
        // jwtEncoder.encode() → assina os claims com a chave privada RSA
        // .getTokenValue()    → extrai o token como String
        return jwtEncoder
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }
}
