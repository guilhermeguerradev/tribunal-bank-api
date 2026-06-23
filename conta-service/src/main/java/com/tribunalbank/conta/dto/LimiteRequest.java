package com.tribunalbank.conta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// DTO específico para atualização de limite
// Valor monetário no body — nunca em query param
public record LimiteRequest(

        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.00",
                inclusive = true,
                message = "Limite não pode ser negativo")
        BigDecimal valor

) {}