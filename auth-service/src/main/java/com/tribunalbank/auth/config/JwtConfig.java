package com.tribunalbank.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

// ═══════════════════════════════════════════════════════════
// JWT CONFIG — Configuração exclusiva das chaves RSA e JWT
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA (Single Responsibility Principle):
// Essa classe cuida APENAS de configurar como o JWT é
// gerado e verificado usando as chaves RSA do .env
//
// Por que separar do SecurityConfig?
// SecurityConfig define REGRAS DE ACESSO (quem pode o quê)
// JwtConfig define COMO O TOKEN FUNCIONA (geração e verificação)
// São responsabilidades diferentes — manutenção mais fácil
//
// FLUXO DO JWT:
//
// LOGIN:
// AuthService gera token
//      ↓
// JwtEncoder assina com CHAVE PRIVADA
//      ↓
// Token enviado ao cliente
//
// REQUISIÇÃO AUTENTICADA:
// Cliente manda token no header
//      ↓
// Spring Security intercepta
//      ↓
// JwtDecoder verifica com CHAVE PÚBLICA
//      ↓
// Requisição autorizada ou rejeitada
// ═══════════════════════════════════════════════════════════
@Configuration
public class JwtConfig {

    // Chave pública lida do .env via application.properties
    // jwt.public-key=${JWT_PUBLIC_KEY}
    //
    // Usada para VERIFICAR tokens — qualquer serviço pode ter
    // Na V2 os outros microsserviços terão uma cópia da chave pública
    @Value("${jwt.public-key}")
    private RSAPublicKey publicKey;

    // Chave privada lida do .env via application.properties
    // jwt.private-key=${JWT_PRIVATE_KEY}
    //
    // Usada para ASSINAR tokens — só o Auth Service tem
    // NUNCA compartilhar com outros serviços
    @Value("${jwt.private-key}")
    private RSAPrivateKey privateKey;

    // ═══════════════════════════════════════════════════════
    // JWT DECODER — Verifica tokens recebidos nas requisições
    // ═══════════════════════════════════════════════════════
    //
    // O Spring Security usa esse bean automaticamente para
    // validar o Bearer token em toda requisição autenticada
    //
    // Verificações que ele faz:
    // → Assinatura válida? (foi assinado com nossa chave privada?)
    // → Token não expirou? (exp > agora)
    // → Token não foi adulterado? (payload intacto)
    //
    // Se qualquer verificação falhar → 401 Unauthorized automático
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder
                .withPublicKey(publicKey)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // JWT ENCODER — Gera e assina tokens no login
    // ═══════════════════════════════════════════════════════
    //
    // Usado pelo JwtService para gerar o access token após login
    //
    // JWKSet → conjunto de chaves no formato JSON Web Key
    //          padrão da especificação OAuth2/OIDC
    //          permite múltiplas chaves para rotação futura
    //
    // RSAKey → representa o par RSA (pública + privada) em formato JWK
    //          o encoder usa a PRIVADA para assinar
    //          o decoder usa a PÚBLICA para verificar
    //
    // ImmutableJWKSet → conjunto imutável de chaves
    //                   thread-safe por padrão
    @Bean
    public JwtEncoder jwtEncoder() {
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .build();

        return new NimbusJwtEncoder(
                new ImmutableJWKSet<>(new JWKSet(rsaKey))
        );
    }
}