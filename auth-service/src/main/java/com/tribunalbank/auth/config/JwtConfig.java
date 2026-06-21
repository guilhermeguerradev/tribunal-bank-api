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
// JWT CONFIG — Configuração dos beans de codificação JWT
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA (SRP):
// Essa classe tem UMA responsabilidade:
// configurar e expor como beans Spring os componentes
// necessários para GERAR e VERIFICAR tokens JWT.
//
// O que NÃO é responsabilidade dessa classe:
// → Converter chaves PEM → responsabilidade do PemKeyParser
// → Definir regras de acesso → responsabilidade do SecurityConfig
// → Gerar o conteúdo do token → responsabilidade do JwtService
//
// ASSIMETRIA RSA — conceito fundamental:
//
// RSA usa um PAR de chaves matematicamente relacionadas:
//
//   CHAVE PRIVADA (secreta — só o Auth Service tem)
//   → Usada para ASSINAR o token no login
//   → Matemáticamente é impossível derivar a privada a partir da pública
//   → NUNCA sai do Auth Service, nunca vai para outros microsserviços
//
//   CHAVE PÚBLICA (pode ser compartilhada)
//   → Usada para VERIFICAR a assinatura do token
//   → Qualquer microsserviço pode ter uma cópia para validar tokens
//   → Na V2 do projeto: Cliente Service, Conta Service etc
//      receberão a chave pública para validar tokens sem chamar o Auth
//
// VANTAGEM DO RSA SOBRE HMAC (HS256):
// Com HMAC, todos os serviços precisariam da MESMA chave secreta
// para verificar tokens — o que aumenta o risco de vazamento.
// Com RSA, apenas o Auth Service tem a chave privada.
// Os outros serviços têm só a pública — inútil para gerar tokens falsos.
//
// NIMBUS JOSE JWT:
// Biblioteca Java líder para JWT/JWE/JWS (RFC 7515, 7516, 7519).
// O Spring Security OAuth2 Resource Server usa ela internamente.
// NimbusJwtDecoder e NimbusJwtEncoder são wrappers do Spring sobre Nimbus.
//
// @Configuration — indica ao Spring que essa classe contém definições de beans.
// Todos os métodos @Bean são gerenciados pelo container IoC.
// ═══════════════════════════════════════════════════════════
@Configuration
public class JwtConfig {

    // ═══════════════════════════════════════════════════════
    // INJEÇÃO DAS CHAVES VIA @Value
    // ═══════════════════════════════════════════════════════
    //
    // @Value("${jwt.public-key}") lê a propriedade jwt.public-key
    // do application-dev.properties (ou da variável de ambiente).
    //
    // Por que String e não RSAPublicKey diretamente?
    // O Spring consegue injetar RSAPublicKey diretamente SE a chave
    // estiver em um arquivo .pem referenciado por classpath:chave.pem.
    // No nosso caso a chave está como texto no properties com \n literal —
    // o conversor padrão do Spring não trata isso.
    // Solução: injetar como String e fazer o parse via PemKeyParser.
    //
    // Por que as chaves ficam em properties e não hardcoded?
    // → Em dev: properties carrega do application-dev.properties (gitignored)
    // → Em prod: variável de ambiente sobrescreve o properties
    // → Nunca vai para o repositório Git com as chaves reais
    @Value("${jwt.public-key}")
    private String publicKeyValue;

    @Value("${jwt.private-key}")
    private String privateKeyValue;

    // ═══════════════════════════════════════════════════════
    // JWT DECODER — Bean de verificação de tokens recebidos
    // ═══════════════════════════════════════════════════════
    //
    // O Spring Security usa esse bean automaticamente para validar
    // o Bearer token em TODA requisição autenticada.
    // Você não chama esse bean manualmente — o framework chama.
    //
    // FLUXO AUTOMÁTICO DO SPRING SECURITY:
    // Requisição chega com header: Authorization: Bearer eyJhbG...
    //      ↓
    // BearerTokenAuthenticationFilter extrai o token
    //      ↓
    // JwtDecoder.decode(token) verifica:
    //   → Assinatura válida? (foi assinado com nossa chave privada?)
    //   → Token não expirou? (claim "exp" > agora)
    //   → Token não foi adulterado? (payload intacto)
    //      ↓
    // Autenticação bem-sucedida → request prossegue para o Controller
    // Autenticação falha        → 401 Unauthorized automático
    //
    // NimbusJwtDecoder.withPublicKey():
    // Cria um decoder que verifica assinaturas usando chave pública RSA.
    // Algoritmo padrão: RS256 (RSA + SHA-256).
    //
    // throws Exception:
    // O parse da chave pode falhar se o PEM for inválido.
    // Preferível falhar na inicialização do Spring a falhar em produção.
    // Spring vai abortar o startup com mensagem clara — comportamento correto.
    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        RSAPublicKey publicKey = PemKeyParser.parsePublicKey(publicKeyValue);

        return NimbusJwtDecoder
                .withPublicKey(publicKey)  // verifica com chave pública
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // JWT ENCODER — Bean de geração e assinatura de tokens
    // ═══════════════════════════════════════════════════════
    //
    // Usado pelo JwtService para gerar access tokens após login bem-sucedido.
    // Você INJETA esse bean no JwtService via @Autowired ou construtor.
    //
    // FLUXO DE GERAÇÃO:
    // JwtService recebe o usuário autenticado
    //      ↓
    // Monta os claims: sub (email), roles, exp (expiração), iat (emissão)
    //      ↓
    // JwtEncoder.encode(claims) assina com CHAVE PRIVADA
    //      ↓
    // Retorna token: eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ...
    //
    // JWKSet (JSON Web Key Set):
    // Estrutura padrão RFC 7517 para representar chaves criptográficas.
    // Permite múltiplas chaves — útil para rotação de chaves em produção:
    // você adiciona uma nova chave sem invalidar tokens com a chave antiga.
    //
    // RSAKey.Builder:
    // Constrói a representação JWK do par RSA.
    // Inclui a chave pública (para publicar no endpoint /.well-known/jwks.json)
    // e a privada (para assinar — nunca exposta externamente).
    //
    // ImmutableJWKSet:
    // Conjunto imutável e thread-safe de chaves JWK.
    // Thread-safe é crítico porque o encoder é um bean singleton
    // compartilhado entre todas as threads da aplicação.
    @Bean
    public JwtEncoder jwtEncoder() throws Exception {
        RSAPublicKey publicKey   = PemKeyParser.parsePublicKey(publicKeyValue);
        RSAPrivateKey privateKey = PemKeyParser.parsePrivateKey(privateKeyValue);

        // Constrói o par de chaves no formato JWK
        // A chave pública fica no JWKSet para eventual exposição via JWKS endpoint
        // A chave privada é usada internamente para assinar — nunca exposta
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .build();

        // ImmutableJWKSet encapsula o JWKSet em uma fonte imutável
        // O tipo genérico <Never> indica que não há contexto de segurança adicional
        return new NimbusJwtEncoder(
                new ImmutableJWKSet<>(new JWKSet(rsaKey))
        );
    }
}