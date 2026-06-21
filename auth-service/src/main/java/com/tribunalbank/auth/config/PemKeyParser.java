package com.tribunalbank.auth.config;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

// ═══════════════════════════════════════════════════════════
// PEM KEY PARSER — Utilitário de conversão de chaves RSA
// ═══════════════════════════════════════════════════════════
//
// RESPONSABILIDADE ÚNICA (SRP — Single Responsibility Principle):
// Essa classe tem UMA única responsabilidade:
// converter Strings no formato PEM em objetos Java de criptografia.
//
// Por que separar do JwtConfig?
// JwtConfig é sobre CONFIGURAÇÃO de beans Spring.
// PemKeyParser é sobre CONVERSÃO de dados.
// São responsabilidades distintas — separar facilita:
// → Testar o parser isoladamente (unit test sem Spring)
// → Reutilizar em outros contextos se necessário
// → Manter cada classe pequena e focada
//
// FORMATO PEM (Privacy Enhanced Mail):
// É um formato de texto para armazenar chaves criptográficas.
// Consiste em:
// → Cabeçalho: -----BEGIN PUBLIC KEY-----
// → Corpo: dados binários codificados em Base64
// → Rodapé: -----END PUBLIC KEY-----
//
// PROBLEMA QUE RESOLVE:
// O Spring não consegue injetar RSAPublicKey/@Value diretamente
// quando a chave PEM está em uma linha com \n literal (não quebra real).
// Esse parser trata ambos os casos — \n literal e quebra de linha real.
//
// Por que classe final?
// Indica que não deve ser estendida — é um utilitário puro.
// Sinaliza para outros desenvolvedores: "não herde isso".
//
// Por que construtor privado?
// Todos os métodos são estáticos — não faz sentido instanciar.
// Padrão de classe utilitária (utility class pattern).
// ═══════════════════════════════════════════════════════════
final class PemKeyParser {

    // Construtor privado — impede instanciação acidental.
    // Lança exceção por segurança caso alguém tente via reflection.
    private PemKeyParser() {
        throw new UnsupportedOperationException("Classe utilitária — não instanciar");
    }

    // ═══════════════════════════════════════════════════════
    // PARSE PUBLIC KEY
    // ═══════════════════════════════════════════════════════
    //
    // Converte uma String PEM de chave pública em RSAPublicKey.
    //
    // ALGORITMO:
    // 1. Remove cabeçalho e rodapé PEM
    // 2. Remove \n literais e quebras de linha reais
    // 3. Remove espaços extras
    // 4. Decodifica Base64 → bytes
    // 5. Cria X509EncodedKeySpec (formato padrão para chaves públicas)
    // 6. Usa KeyFactory para gerar o objeto RSAPublicKey
    //
    // X509EncodedKeySpec:
    // Padrão X.509 para codificar chaves públicas (SubjectPublicKeyInfo).
    // É o formato que o Java espera para chaves públicas RSA.
    //
    // @param pem String no formato PEM com ou sem quebras de linha
    // @return RSAPublicKey pronto para uso no JwtDecoder
    // @throws Exception se a chave for inválida ou malformada
    static RSAPublicKey parsePublicKey(String pem) throws Exception {
        // Remove tudo que não é o conteúdo Base64 da chave
        String base64 = stripPemHeaders(pem,
                "-----BEGIN PUBLIC KEY-----",
                "-----END PUBLIC KEY-----");

        // Base64.getDecoder() usa o alfabeto padrão (RFC 4648).
        // Chaves PEM usam Base64 padrão — não use getUrlDecoder() aqui.
        byte[] decoded = Base64.getDecoder().decode(base64);

        // X509EncodedKeySpec é o wrapper Java para o formato DER de chaves públicas.
        // DER (Distinguished Encoding Rules) = representação binária do X.509.
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);

        // KeyFactory é a fábrica de objetos de chave criptográfica do Java.
        // "RSA" especifica o algoritmo — poderia ser "EC" para chaves elípticas.
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        // generatePublic converte a especificação em um objeto RSAPublicKey
        // que implementa as interfaces java.security e pode ser usado diretamente
        // pelo NimbusJwtDecoder para verificar assinaturas JWT.
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    // ═══════════════════════════════════════════════════════
    // PARSE PRIVATE KEY
    // ═══════════════════════════════════════════════════════
    //
    // Converte uma String PEM de chave privada em RSAPrivateKey.
    //
    // PKCS8EncodedKeySpec vs PKCS1:
    // Existem dois formatos de chave privada RSA em PEM:
    // → PKCS#1: -----BEGIN RSA PRIVATE KEY----- (formato legado OpenSSL)
    // → PKCS#8: -----BEGIN PRIVATE KEY-----     (formato moderno, padrão Java)
    //
    // Nosso GerarChaves.java gera PKCS#8, então usamos PKCS8EncodedKeySpec.
    // Se a chave fosse PKCS#1, precisaria de Bouncy Castle para converter.
    //
    // @param pem String no formato PEM com ou sem quebras de linha
    // @return RSAPrivateKey pronto para uso no JwtEncoder
    // @throws Exception se a chave for inválida ou malformada
    static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        // Remove cabeçalho e rodapé do formato PKCS#8
        String base64 = stripPemHeaders(pem,
                "-----BEGIN PRIVATE KEY-----",
                "-----END PRIVATE KEY-----");

        byte[] decoded = Base64.getDecoder().decode(base64);

        // PKCS8EncodedKeySpec é o wrapper Java para chaves privadas no formato PKCS#8.
        // Esse é o formato padrão gerado pelo Java KeyPairGenerator
        // e compatível com OpenSSL moderno (pkcs8 -topk8).
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        // generatePrivate converte para RSAPrivateKey que será usado
        // pelo NimbusJwtEncoder para assinar os tokens JWT.
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    // ═══════════════════════════════════════════════════════
    // STRIP PEM HEADERS — método auxiliar privado
    // ═══════════════════════════════════════════════════════
    //
    // Extrai apenas o conteúdo Base64 de uma String PEM,
    // removendo cabeçalho, rodapé e qualquer whitespace.
    //
    // Trata dois casos de quebra de linha:
    // → "\\n" (dois chars: barra + n) — vem de properties com \n literal
    // → "\n"  (um char: newline real) — vem de arquivos com quebra real
    //
    // Por que replaceAll("\\s+", "") em vez de replace(" ", "")?
    // \s+ captura qualquer whitespace: espaço, tab, \r, \n, \f
    // Garante limpeza completa independente do sistema operacional.
    //
    // @param pem    String PEM completa
    // @param header cabeçalho a remover ex: "-----BEGIN PUBLIC KEY-----"
    // @param footer rodapé a remover  ex: "-----END PUBLIC KEY-----"
    // @return String Base64 limpa, pronta para decodificação
    private static String stripPemHeaders(String pem, String header, String footer) {
        return pem
                .replace(header, "")       // remove cabeçalho PEM
                .replace(footer, "")       // remove rodapé PEM
                .replace("\\n", "")        // remove \n literal (do properties)
                .replace("\n", "")         // remove quebra de linha real
                .replace("\r", "")         // remove carriage return (Windows)
                .replaceAll("\\s+", "")    // remove qualquer whitespace restante
                .trim();                   // remove espaços das bordas
    }
}