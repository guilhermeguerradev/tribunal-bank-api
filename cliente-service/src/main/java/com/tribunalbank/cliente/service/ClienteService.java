package com.tribunalbank.cliente.service;

import com.tribunalbank.cliente.dto.*;
import com.tribunalbank.cliente.entity.Cliente;
import com.tribunalbank.cliente.entity.Endereco;
import com.tribunalbank.cliente.entity.TipoEndereco;
import com.tribunalbank.cliente.exception.*;
import com.tribunalbank.cliente.repository.ClienteRepository;
import com.tribunalbank.cliente.repository.EnderecoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// ═══════════════════════════════════════════════════════════
// CLIENTE SERVICE — Lógica de negócio do cliente-service
// ═══════════════════════════════════════════════════════════
//
// @RequiredArgsConstructor — Lombok gera construtor com todos
// os campos final. Padrão recomendado para injeção de dependência:
// → Mais testável que @Autowired (fácil de mockar no teste)
// → Garante que o objeto é criado com todas as dependências
// → Campos final não podem ser null após construção
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final EnderecoRepository enderecoRepository;
    private final CpfValidatorService cpfValidator;

    // ── Cadastrar cliente ────────────────────────────────
    //
    // @Transactional — envolve o método em uma transação
    // Se qualquer passo falhar → rollback automático
    // Garante que cliente e endereços são salvos juntos (tudo ou nada)
    //
    // usuarioId vem do JWT — extraído no controller
    // Garante que um usuário só cria o próprio perfil
    @Transactional
    public ClienteResponse cadastrar(ClienteRequest request, String usuarioId) {

        // Valida o algoritmo do CPF — não só o formato
        if (!cpfValidator.isValido(request.cpf())) {
            throw new BusinessException("CPF inválido: " + request.cpf()) {};
        }

        // Verifica duplicidade de CPF
        if (clienteRepository.existsByCpf(request.cpf())) {
            throw new CpfJaCadastradoException(request.cpf());
        }

        // Verifica duplicidade de email
        if (clienteRepository.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException(request.email());
        }

        // Verifica se o usuário já tem um perfil de cliente
        if (clienteRepository.findByUsuarioId(usuarioId).isPresent()) {
            throw new BusinessException("Usuário já possui um perfil de cliente") {};
        }

        // Constrói a entidade Cliente via Builder
        Cliente cliente = Cliente.builder()
                .usuarioId(usuarioId)
                .nome(request.nome())
                .cpf(request.cpf())
                .email(request.email())
                .telefone(request.telefone())
                .dataNascimento(request.dataNascimento())
                .ativo(true)
                .build();

        // Adiciona endereços se vieram no request
        if (request.enderecos() != null && !request.enderecos().isEmpty()) {
            request.enderecos().forEach(endReq ->
                    cliente.getEnderecos().add(
                            construirEndereco(endReq, cliente)
                    )
            );
        }

        // CascadeType.PERSIST garante que os endereços
        // são salvos junto com o cliente automaticamente
        clienteRepository.save(cliente);

        return ClienteResponse.from(cliente);
    }

    // ── Buscar por ID ────────────────────────────────────
    @Transactional(readOnly = true)
    public ClienteResponse buscarPorId(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNotFoundException(id));

        return ClienteResponse.from(cliente);
    }

    // ── Buscar por CPF ───────────────────────────────────
    @Transactional(readOnly = true)
    public ClienteResponse buscarPorCpf(String cpf) {
        Cliente cliente = clienteRepository.findByCpf(cpf)
                .orElseThrow(() -> ClienteNotFoundException.porCpf(cpf));

        return ClienteResponse.from(cliente);
    }

    // ── Listar com paginação ─────────────────────────────
    //
    // Pageable → Spring monta automaticamente a partir dos
    // query params: ?page=0&size=10&sort=nome,asc
    // Page<ClienteResponse> → retorna metadados de paginação:
    // totalElements, totalPages, currentPage, content
    @Transactional(readOnly = true)
    public Page<ClienteResponse> listar(String nome, Pageable pageable) {
        if (nome != null && !nome.isBlank()) {
            // Busca com filtro por nome
            return clienteRepository
                    .findByNomeContendoEAtivo(nome, pageable)
                    .map(ClienteResponse::from);
        }

        // Sem filtro — lista todos os ativos
        return clienteRepository
                .findAllByAtivoTrue(pageable)
                .map(ClienteResponse::from);
    }

    // ── Atualizar cliente ────────────────────────────────
    @Transactional
    public ClienteResponse atualizar(String id, ClienteRequest request, String usuarioId) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNotFoundException(id));

        // Garante que o usuário só atualiza o próprio perfil
        // A verificação de ROLE_ADMIN fica no controller via @PreAuthorize
        if (!cliente.getUsuarioId().equals(usuarioId)) {
            throw new BusinessException("Acesso negado — você só pode atualizar seu próprio perfil") {};
        }

        // Verifica email duplicado apenas se mudou
        if (!cliente.getEmail().equals(request.email()) && clienteRepository.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException(request.email());
        }

        // Atualiza os campos permitidos
        // CPF não é atualizado — documento imutável
        cliente.setNome(request.nome());
        cliente.setEmail(request.email());
        cliente.setTelefone(request.telefone());
        cliente.setDataNascimento(request.dataNascimento());

        clienteRepository.save(cliente);

        return ClienteResponse.from(cliente);
    }

    // ── Desativar cliente (soft delete) ──────────────────
    @Transactional
    public void desativar(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNotFoundException(id));

        // Soft delete — apenas marca como inativo
        // O registro permanece no banco para auditoria
        cliente.setAtivo(false);
        clienteRepository.save(cliente);
    }

    // ── Adicionar endereço ───────────────────────────────
    @Transactional
    public EnderecoResponse adicionarEndereco(String clienteId,
                                              EnderecoRequest request) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNotFoundException(clienteId));

        // Verifica se já existe endereço do mesmo tipo
        // Um cliente só pode ter UM RESIDENCIAL e UM COMERCIAL
        if (enderecoRepository.existsByClienteAndTipo(cliente, request.tipo())) {
            throw new TipoEnderecoJaExisteException(request.tipo().name());
        }

        // Se marcado como principal, remove o principal atual
        if (request.principal()) {
            removerPrincipalAtual(cliente);
        }

        Endereco endereco = construirEndereco(request, cliente);
        cliente.getEnderecos().add(endereco);

        // CascadeType.PERSIST salva o endereço novo junto com o cliente
        clienteRepository.save(cliente);

        return EnderecoResponse.from(endereco);
    }

    // ── Atualizar endereço ───────────────────────────────
    @Transactional
    public EnderecoResponse atualizarEndereco(String clienteId,
                                              String enderecoId,
                                              EnderecoRequest request) {
        // Verifica se o cliente existe
        clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNotFoundException(clienteId));

        Endereco endereco = enderecoRepository.findById(enderecoId)
                .orElseThrow(() -> new EnderecoNotFoundException(enderecoId));

        // Atualiza os campos do endereço
        endereco.setLogradouro(request.logradouro());
        endereco.setNumero(request.numero());
        endereco.setComplemento(request.complemento());
        endereco.setBairro(request.bairro());
        endereco.setCidade(request.cidade());
        endereco.setEstado(request.estado());
        endereco.setCep(request.cep());
        endereco.setPrincipal(request.principal());

        enderecoRepository.save(endereco);

        return EnderecoResponse.from(endereco);
    }

    // ── Remover endereço ─────────────────────────────────
    @Transactional
    public void removerEndereco(String clienteId, String enderecoId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNotFoundException(clienteId));

        Endereco endereco = enderecoRepository.findById(enderecoId)
                .orElseThrow(() -> new EnderecoNotFoundException(enderecoId));

        // Remove da lista do cliente
        // orphanRemoval = true faz o DELETE no banco automaticamente
        cliente.getEnderecos().remove(endereco);
        clienteRepository.save(cliente);
    }

    // ── Métodos auxiliares privados ──────────────────────

    // Constrói a entidade Endereco a partir do DTO
    // Centraliza a conversão DTO → entidade
    private Endereco construirEndereco(EnderecoRequest request, Cliente cliente) {
        return Endereco.builder()
                .cliente(cliente)
                .tipo(request.tipo())
                .logradouro(request.logradouro())
                .numero(request.numero())
                .complemento(request.complemento())
                .bairro(request.bairro())
                .cidade(request.cidade())
                .estado(request.estado())
                .cep(request.cep())
                .principal(request.principal())
                .build();
    }

    // Remove o flag principal do endereço atual
    // Antes de definir um novo principal — só um pode ser principal
    private void removerPrincipalAtual(Cliente cliente) {
        enderecoRepository.findByClienteAndPrincipalTrue(cliente)
                .ifPresent(endAtual -> {
                    endAtual.setPrincipal(false);
                    enderecoRepository.save(endAtual);
                });
    }

    // ── Verifica se o cliente pertence ao usuário autenticado ──
    //
    // Chamado pelo @PreAuthorize do controller:
    // @clienteService.pertenceAoUsuario(#id, authentication.name)
    // authentication.name → email do usuário autenticado (claim "sub" do JWT)
    //
    // Retorna true → usuário pode acessar
    // Retorna false → Spring lança AccessDeniedException → 403 Forbidden
    @Transactional
    public boolean pertenceAoUsuario(String clienteId, String email) {
        return clienteRepository.findById(clienteId)
                .map(cliente -> {
                    // Busca o cliente pelo usuarioId que é o email (subject do JWT)
                    return clienteRepository
                            .findByUsuarioId(email)
                            .map(c -> c.getId().equals(clienteId))
                            .orElse(false);
                })
                .orElse(false);
    }
}