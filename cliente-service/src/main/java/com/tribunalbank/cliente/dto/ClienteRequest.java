package com.tribunalbank.cliente.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

// ═══════════════════════════════════════════════════════════
// CLIENTE REQUEST — DTO de entrada para criação de cliente
// ═══════════════════════════════════════════════════════════
//
// O usuarioId NÃO vem no body da requisição.
// Ele é extraído do JWT no controller:
// @AuthenticationPrincipal Jwt jwt → jwt.getSubject()
// Isso garante que um usuário só pode criar o próprio perfil.
// ═══════════════════════════════════════════════════════════
public record ClienteRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 255, message = "Nome deve ter entre 2 e 255 caracteres")
        String nome,

        // CPF validado com nossa anotação customizada @Cpf (criada na FASE 7)
        // Por enquanto usamos @Pattern para validar o formato básico
        // 11 dígitos numéricos — sem pontos ou hífen
        @NotBlank(message = "CPF é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "CPF deve conter exatamente 11 dígitos numéricos")
        String cpf,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        // Telefone é opcional no cadastro — sem @NotBlank
        // Mas se vier, deve ter entre 10 e 11 dígitos (com DDD)
        // 10 dígitos = fixo com DDD: (11) 3333-3333
        // 11 dígitos = celular com DDD: (11) 98765-4321
        @Pattern(regexp = "\\d{10,11}", message = "Telefone deve ter 10 ou 11 dígitos")
        String telefone,

        // @NotNull → data não pode ser null (@NotBlank não funciona em LocalDate)
        // @Past → a data deve ser no passado — ninguém nasce no futuro
        @NotNull(message = "Data de nascimento é obrigatória")
        @Past(message = "Data de nascimento deve ser no passado")
        LocalDate dataNascimento,

        // Lista de endereços opcional no cadastro inicial
        // Cliente pode se cadastrar sem endereço e adicionar depois
        // @Valid → propaga a validação para cada EnderecoRequest da lista
        // Sem @Valid as anotações dentro de EnderecoRequest seriam ignoradas
        @Valid
        List<EnderecoRequest> enderecos

) {}