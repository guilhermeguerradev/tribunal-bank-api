package com.tribunalbank.cliente.dto;

import com.tribunalbank.cliente.entity.TipoEndereco;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// ═══════════════════════════════════════════════════════════
// ENDERECO REQUEST — DTO de entrada para criação/atualização
// ═══════════════════════════════════════════════════════════
//
// Por que Record?
// → Imutável por padrão — dados de entrada não devem ser alterados
// → Getters automáticos (tipo()) sem Lombok
// → equals/hashCode/toString automáticos
// → Sintaxe mais enxuta que class com @Data
//
// Por que DTO e não usar a entidade Endereco diretamente?
// → Entidade tem campos que o cliente não deve enviar (id, criadoEm)
// → Validações ficam no DTO — entidade fica limpa
// → Contrato da API fica independente da estrutura do banco
// ═══════════════════════════════════════════════════════════
public record EnderecoRequest(

        // @NotNull → o tipo não pode ser null (campo obrigatório)
        // @NotBlank não funciona em Enum — use @NotNull
        // O Jackson deserializa "RESIDENCIAL" ou "COMERCIAL" para o Enum
        // Se vier um valor inválido → 400 Bad Request automático
        @NotNull(message = "Tipo do endereço é obrigatório")
        TipoEndereco tipo,

        // @NotBlank → não pode ser null, vazio ("") ou só espaços ("   ")
        @NotBlank(message = "Logradouro é obrigatório")
        String logradouro,

        @NotBlank(message = "Número é obrigatório")
        @Size(max = 10, message = "Número deve ter no máximo 10 caracteres")
        String numero,

        // Complemento é opcional — sem validação obrigatória
        // Pode vir null ou ausente no JSON
        String complemento,

        @NotBlank(message = "Bairro é obrigatório")
        String bairro,

        @NotBlank(message = "Cidade é obrigatória")
        String cidade,

        // @Pattern → valida com expressão regular
        // [A-Z]{2} → exatamente 2 letras maiúsculas: SP, RJ, MG etc
        // regexp = "..." → a regex em si
        // message → mensagem de erro se não bater
        @NotBlank(message = "Estado é obrigatório")
        @Pattern(regexp = "[A-Z]{2}", message = "Estado deve ter 2 letras maiúsculas (ex: SP)")
        String estado,

        // CEP com exatamente 8 dígitos numéricos
        // \\d = dígito (0-9), {8} = exatamente 8 vezes
        // Sem hífen — armazenamos sem formatação
        @NotBlank(message = "CEP é obrigatório")
        @Pattern(regexp = "\\d{8}", message = "CEP deve conter exatamente 8 dígitos numéricos")
        String cep,

        // Indica se este é o endereço principal do cliente
        // Enviado pelo front-end — o service garante que só um é principal
        boolean principal

) {}