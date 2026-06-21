package com.tribunalbank.cliente.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// ═══════════════════════════════════════════════════════════
// JPA AUDITING CONFIG — Habilita auditoria automática
// ═══════════════════════════════════════════════════════════
//
// @EnableJpaAuditing ativa o sistema de auditoria do Spring Data.
// Sem essa anotação, mesmo com @CreatedDate e @LastModifiedDate
// nas entidades, os campos ficam NULL — nada é preenchido.
//
// O AuditingEntityListener nas entidades escuta os eventos:
// → PRE_PERSIST (antes do INSERT) → preenche criadoEm
// → PRE_UPDATE  (antes do UPDATE) → atualiza atualizadoEm
//
// Separado em classe própria porque é uma configuração de
// infraestrutura — não mistura com regras de segurança.
// ═══════════════════════════════════════════════════════════
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // Classe vazia intencionalmente
    // A anotação @EnableJpaAuditing é suficiente
    // O Spring faz todo o resto automaticamente
}