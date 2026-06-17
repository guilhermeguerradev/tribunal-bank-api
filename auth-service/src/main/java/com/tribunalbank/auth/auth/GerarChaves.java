package com.tribunalbank.auth.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Utilitário para geração do par de chaves RSA usadas na assinatura JWT.
 *
 * COMO USAR:
 * 1. Rode o método main dessa classe no IntelliJ (botão direito → Run)
 * 2. Copie as chaves impressas no console
 * 3. Cole no arquivo .env na raiz do auth-service
 * 4. Nunca commite o .env no Git
 *
 * POR QUE RSA E NÃO HMAC?
 * HMAC usa a mesma chave para assinar e verificar (chave simétrica)
 * RSA usa chave privada para assinar e chave pública para verificar
 * Em microsserviços isso é importante:
 * → Auth Service assina com a chave PRIVADA (só ele tem)
 * → Outros serviços verificam com a chave PÚBLICA (qualquer um pode ter)
 * → Nunca precisamos compartilhar a chave privada com outros serviços
 *
 * NO MUNDO REAL:
 * Essa geração seria feita uma única vez pelo time de segurança
 * e as chaves seriam armazenadas no AWS Secrets Manager ou HashiCorp Vault
 * Nunca ficariam em arquivo no repositório
 */
public class GerarChaves {

    public static void main(String[] args) throws Exception {

        // Inicializa o gerador de chaves RSA com 2048 bits
        // 2048 bits é o mínimo recomendado para produção em 2026
        // 4096 bits é mais seguro mas mais lento — 2048 é o padrão do mercado
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        // Gera o par: uma chave privada + uma chave pública matematicamente relacionadas
        // O que é assinado com a privada só pode ser verificado com a pública
        KeyPair pair = generator.generateKeyPair();

        // Converte a chave privada para o formato PEM (Base64 com cabeçalho)
        // PEM é o formato padrão que o Spring Security OAuth2 Resource Server espera
        String privateKey = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        // Converte a chave pública para o formato PEM
        String publicKey = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        // Imprime no console para copiar para o .env
        System.out.println("=== CHAVE PRIVADA — cole no .env como JWT_PRIVATE_KEY ===");
        System.out.println(privateKey);
        System.out.println();
        System.out.println("=== CHAVE PÚBLICA — cole no .env como JWT_PUBLIC_KEY ===");
        System.out.println(publicKey);
        System.out.println();
        System.out.println("LEMBRE: substitua as quebras de linha por \\n no .env");
    }
}
