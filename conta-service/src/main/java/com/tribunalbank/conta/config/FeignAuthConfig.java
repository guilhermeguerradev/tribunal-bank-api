package com.tribunalbank.conta.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// ═══════════════════════════════════════════════════════════
// FEIGN AUTH CONFIG — Propaga JWT globalmente para todos os
// FeignClients do conta-service
// ═══════════════════════════════════════════════════════════
//
// @Configuration global — aplica o interceptor para TODOS
// os FeignClients automaticamente.
// Separado do FeignConfig (sem @Configuration) para evitar
// conflito de beans duplicados.
// ═══════════════════════════════════════════════════════════
@Configuration
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor jwtRequestInterceptor() {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder
                            .getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");

                if (authHeader != null
                        && authHeader.startsWith("Bearer ")) {
                    template.header("Authorization", authHeader);
                }
            }
        };
    }
}