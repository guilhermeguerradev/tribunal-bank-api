package com.tribunalbank.cliente.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${jwt.public-key}")
    private String publicKeyValue;

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            RSAPublicKey publicKey = parsePublicKey(publicKeyValue);
            return NimbusJwtDecoder
                    .withPublicKey(publicKey)
                    .build();
        } catch (Exception e) {
            // Falha na inicialização → Spring aborta o startup com mensagem clara
            // Melhor falhar aqui do que em runtime com requisições reais
            throw new IllegalStateException("Erro ao configurar JwtDecoder — verifique jwt.public-key no properties", e);
        }
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\n", "")
                .replace("\n", "")
                .replaceAll("\\s+", "")
                .trim();

        byte[] decoded = Base64.getDecoder().decode(base64);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
    }
}