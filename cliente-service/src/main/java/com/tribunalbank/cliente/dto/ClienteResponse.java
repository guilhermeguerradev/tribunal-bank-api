package com.tribunalbank.cliente.dto;

import com.tribunalbank.cliente.entity.Cliente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// ═══════════════════════════════════════════════════════════
// CLIENTE RESPONSE — DTO de saída para retorno ao cliente
// ═══════════════════════════════════════════════════════════
public record ClienteResponse(
        String id,
        String nome,
        String cpf,
        String email,
        String telefone,
        LocalDate dataNascimento,
        boolean ativo,
        List<EnderecoResponse> enderecos,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    // Converte entidade → DTO incluindo a lista de endereços
    // .stream().map(EnderecoResponse::from) → converte cada Endereco
    // em um EnderecoResponse usando o método from() que criamos
    public static ClienteResponse from(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNome(),
                cliente.getCpf(),
                cliente.getEmail(),
                cliente.getTelefone(),
                cliente.getDataNascimento(),
                cliente.isAtivo(),
                cliente.getEnderecos()
                        .stream()
                        .map(EnderecoResponse::from)
                        .toList(),
                cliente.getCriadoEm(),
                cliente.getAtualizadoEm()
        );
    }
}