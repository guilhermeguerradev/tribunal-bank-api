package com.tribunalbank.conta.client;

// ═══════════════════════════════════════════════════════════
// CLIENTE RESPONSE — DTO que espelha a resposta do Cliente Service
// ═══════════════════════════════════════════════════════════
//
// Por que um DTO próprio e não importar do Cliente Service?
// Em microsserviços cada serviço é INDEPENDENTE.
// Se importássemos do Cliente Service:
// → Acoplamento de compilação entre serviços
// → Mudança no Cliente Service quebra o Conta Service
// → Quebra o princípio de independência dos microsserviços
//
// Com DTO próprio:
// → Conta Service só tem os campos que REALMENTE usa
// → Cliente Service pode evoluir sem impactar o Conta Service
// → Total independência entre os serviços
//
// Campos incluídos — apenas o necessário para o Conta Service:
// → id     → armazenado como clienteId na conta
// → nome   → exibição e relatórios futuros
// → cpf    → validações e auditoria futura
// → ativo  → verifica se pode abrir nova conta
//
// Campos NÃO incluídos (Conta Service não precisa):
// → email, telefone, dataNascimento, enderecos etc
// ═══════════════════════════════════════════════════════════
public record ClienteResponse(
        String id,
        String nome,
        String cpf,    // adicionado — necessário para auditoria e relatórios
        boolean ativo  // verificado antes de criar a conta
) {}