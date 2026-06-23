package com.tribunalbank.conta.config;

import com.tribunalbank.conta.exception.ClienteNotFoundException;
import com.tribunalbank.conta.exception.ClienteServiceException;
import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class FeignConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> switch (response.status()) {
            case 404 -> new ClienteNotFoundException(
                    "Cliente não encontrado via " + methodKey);
            case 400 -> new ClienteServiceException(
                    "Requisição inválida: " + methodKey);
            default -> new ClienteServiceException(
                    "Erro ao comunicar. Status: " + response.status());
        };
    }
}