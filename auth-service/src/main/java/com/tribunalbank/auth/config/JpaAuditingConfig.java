package com.tribunalbank.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// ═══════════════════════════════════════════════════════════
// JPA AUDITING CONFIG
// ═══════════════════════════════════════════════════════════
//
// PROBLEMA QUE RESOLVE:
// A entidade Usuario tem dois campos:
//   @CreatedDate    → criadoEm
//   @LastModifiedDate → atualizadoEm
//
// Sem essa configuração esses campos NUNCA são preenchidos
// O Spring simplesmente ignora as anotações
// e você recebe erro de NOT NULL ao tentar salvar
//
// COM essa configuração o Spring preenche automaticamente:
//   criadoEm      → no momento que o registro é criado
//                   nunca mais alterado (updatable = false)
//   atualizadoEm  → toda vez que o registro é salvo
//
// FLUXO:
//   usuarioRepository.save(usuario)
//          ↓
//   Spring Auditing intercepta
//          ↓
//   criadoEm    = LocalDateTime.now()  (só na criação)
//   atualizadoEm = LocalDateTime.now() (sempre)
//          ↓
//   INSERT INTO usuarios (..., criado_em, atualizado_em)
//   VALUES (..., '2026-06-17 14:30:00', '2026-06-17 14:30:00')
//
// @EnableJpaAuditing → habilita o sistema de auditoria do Spring Data JPA
//                      lê as anotações @CreatedDate e @LastModifiedDate
//                      e preenche os campos automaticamente
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // Configuração declarativa via anotação
    // Não precisa de nenhum método aqui
}