package com.tribunalbank.auth.repository;

import com.tribunalbank.auth.entity.RefreshToken;
import com.tribunalbank.auth.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    // Busca um token pelo valor dele
    // Usado quando o usuário manda o refresh token para renovar o access token
    // SELECT * FROM refresh_tokens WHERE token = ?
    Optional<RefreshToken> findByToken(String token);

    // Revoga todos os tokens de um usuário de uma vez
    // Usado quando:
    // → usuário troca a senha
    // → administrador bloqueia o usuário
    // → usuário clica em "sair de todos os dispositivos"
    //
    // @Modifying  → obrigatório para queries de UPDATE e DELETE
    // @Query      → necessário aqui porque o Spring Data não consegue
    //               gerar UPDATE automaticamente pelo nome do método
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revogado = true WHERE rt.usuario = :usuario")
    void revogarTodosPorUsuario(Usuario usuario);
}