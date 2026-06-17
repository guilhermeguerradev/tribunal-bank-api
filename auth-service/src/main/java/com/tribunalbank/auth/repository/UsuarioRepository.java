package com.tribunalbank.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tribunalbank.auth.entity.Usuario;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// @Repository → marca a interface como componente de persistência do Spring
// O Spring cria a implementação automaticamente em tempo de execução
// Você não precisa escrever nenhum SQL para os métodos básicos
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, String> {
    // JpaRepository<Usuario, String> significa:
    // → Usuario  = entidade que essa interface gerencia
    // → String   = tipo do ID da entidade (usamos UUID como String)

    // O Spring Data JPA gera a query automaticamente pelo nome do método
    // findByEmail → SELECT * FROM usuarios WHERE email = ?
    // Optional    → pode retornar vazio sem lançar NullPointerException
    //               você trata com .orElseThrow() ou .isPresent()
    Optional<Usuario> findByEmail(String email);

    // Verifica se já existe um usuário com esse email
    // Útil no cadastro para não permitir emails duplicados
    // SELECT COUNT(*) > 0 FROM usuarios WHERE email = ?
    boolean existsByEmail(String email);
}