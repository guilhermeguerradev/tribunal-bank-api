package com.tribunalbank.auth.entity;

import com.tribunalbank.auth.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String token;

    // @ManyToOne → muitos RefreshTokens podem pertencer a um Usuario
    //              (um usuário logado em celular, tablet e PC = 3 tokens)
    //
    // FetchType.LAZY → o Usuario NÃO é carregado junto com o token automaticamente
    //                  só vai ao banco buscar o usuario quando você chamar
    //                  refreshToken.getUsuario() explicitamente
    //
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario  usuario;

    @Column(nullable = false)
    private LocalDateTime expiracao;

    // Controla se o token foi invalidado manualmente antes de expirar
    //
    // false → token válido, pode ser usado para gerar novo access token
    // true  → token revogado, mesmo que não tenha expirado é rejeitado
    //
    // Quando isso vira true?
    // → usuário clica em "sair" — revoga o token daquele dispositivo
    // → usuário clica em "sair de todos os dispositivos" — revoga todos os tokens dele
    // → administrador bloqueia o usuário — revoga todos os tokens
    // → usuário troca a senha — boa prática revogar todos os tokens antigos
    //
    // Sem esse campo não seria possível invalidar um token antes do prazo
    // você teria que esperar os 7 dias expirarem naturalmente
    @Column(nullable = false)
    private boolean revogado;
}