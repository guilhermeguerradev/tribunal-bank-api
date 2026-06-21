package com.tribunalbank.cliente.dto;

import com.tribunalbank.cliente.entity.Endereco;
import com.tribunalbank.cliente.entity.TipoEndereco;

// ═══════════════════════════════════════════════════════════
// ENDERECO RESPONSE — DTO de saída para retorno ao cliente
// ═══════════════════════════════════════════════════════════
//
// Por que separar Request e Response?
// → Request: o que o cliente MANDA (sem id, sem timestamps)
// → Response: o que o cliente RECEBE (com id, com timestamps)
// → Mudanças internas na entidade não afetam o contrato da API
//
// Método estático from() — padrão Factory:
// Converte entidade → DTO em um lugar só
// Evita lógica de conversão espalhada pelo código
// ═══════════════════════════════════════════════════════════
public record EnderecoResponse(
        String id,
        TipoEndereco tipo,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado,
        String cep,
        boolean principal
) {
    // Método estático de conversão entidade → DTO
    // Chamado no service: EnderecoResponse.from(endereco)
    // Centraliza a conversão — se a entidade mudar, muda só aqui
    public static EnderecoResponse from(Endereco endereco) {
        return new EnderecoResponse(
                endereco.getId(),
                endereco.getTipo(),
                endereco.getLogradouro(),
                endereco.getNumero(),
                endereco.getComplemento(),
                endereco.getBairro(),
                endereco.getCidade(),
                endereco.getEstado(),
                endereco.getCep(),
                endereco.isPrincipal()
        );
    }
}