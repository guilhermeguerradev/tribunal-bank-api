package com.tribunalbank.conta.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Habilita @CreatedDate e @LastModifiedDate nas entidades
// Sem essa anotação os campos de auditoria ficam NULL
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}