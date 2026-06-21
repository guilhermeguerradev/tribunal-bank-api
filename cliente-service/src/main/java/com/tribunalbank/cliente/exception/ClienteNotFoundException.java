package com.tribunalbank.cliente.exception;

// Lançada quando um cliente não é encontrado no banco
// O GlobalExceptionHandler mapeia para HTTP 404 Not Found
public class ClienteNotFoundException extends BusinessException {

    public ClienteNotFoundException(String id) {
        super("Cliente não encontrado com id: " + id);
    }

    // Sobrecarga — permite buscar por CPF também
    // ClienteNotFoundException.porCpf("12345678901")
    public static ClienteNotFoundException porCpf(String cpf) {
        return new ClienteNotFoundException("cpf: " + cpf);
    }
}