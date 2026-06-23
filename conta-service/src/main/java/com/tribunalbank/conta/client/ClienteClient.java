package com.tribunalbank.conta.client;

import com.tribunalbank.conta.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// ═══════════════════════════════════════════════════════════
// CLIENTE CLIENT — Interface FeignClient para o Cliente Service
// ═══════════════════════════════════════════════════════════
//
// O que é FeignClient?
// É um cliente HTTP declarativo do Spring Cloud.
// Você define uma interface com as chamadas que quer fazer
// e o Spring implementa automaticamente em runtime.
//
// Sem Feign — jeito manual com RestTemplate:
// ResponseEntity<ClienteResponse> response = restTemplate
//     .getForEntity("http://cliente-service/clientes/" + id,
//                    ClienteResponse.class);
// → Verboso, propenso a erros, difícil de testar
//
// Com Feign — declarativo:
// clienteClient.buscarPorId(id);
// → Limpo, testável, integrado com Eureka automaticamente
//
// @FeignClient:
// name = "cliente-service" → nome do serviço no Eureka
//   O Feign pergunta ao Eureka: "onde está o cliente-service?"
//   Eureka responde: "localhost:8082"
//   O Feign faz a chamada para localhost:8082/clientes/{id}
//   Se o Cliente Service mudar de porta → Eureka atualiza
//   O Feign encontra automaticamente — sem IP fixo
//
// configuration = FeignConfig.class → usa nossa configuração:
//   → Logger.Level.FULL (log completo em dev)
//   → ErrorDecoder (traduz erros HTTP em exceções do domínio)
//
// URL DO GATEWAY (futuro):
// Em produção com API Gateway, poderia chamar via Gateway:
// url = "http://api-gateway" → Gateway roteia para cliente-service
// Por enquanto chama direto via Eureka — mais simples
// ═══════════════════════════════════════════════════════════
@FeignClient(
        name = "cliente-service",
        configuration = FeignConfig.class
)
public interface ClienteClient {

    // ── Buscar cliente por ID ────────────────────────────
    //
    // Chamado pelo ContaService antes de criar uma conta:
    // 1. Cliente existe? (não lança 404?)
    // 2. Cliente está ativo? (campo ativo = true?)
    //
    // O Feign monta automaticamente:
    // GET http://cliente-service/clientes/{clienteId}
    // Authorization: Bearer {token} → adicionado pelo RequestInterceptor
    //
    // Retorna ClienteResponse — DTO que espelha o retorno
    // do Cliente Service. Não usamos a entidade Cliente
    // porque ela pertence ao Cliente Service, não ao Conta Service.
    // Cada microsserviço tem seus próprios modelos.
    @GetMapping("/clientes/usuario/{usuarioId}")
    ClienteResponse buscarPorUsuarioId(@PathVariable String usuarioId);
}