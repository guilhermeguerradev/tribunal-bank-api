package com.tribunalbank.auth.service;

import com.tribunalbank.auth.entity.Usuario;
import com.tribunalbank.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

// ═══════════════════════════════════════════════════════════
// USUARIO DETAILS SERVICE
// ═══════════════════════════════════════════════════════════
//
// PROBLEMA QUE RESOLVE:
// O AuthenticationManager precisa saber como buscar
// o usuário no banco para validar as credenciais no login.
// Ele não sabe nada sobre nosso UsuarioRepository —
// só conhece a interface UserDetailsService do Spring Security.
//
// SOLUÇÃO:
// Implementamos UserDetailsService e ensinamos o Spring
// a buscar nosso Usuario pelo email.
//
// FLUXO NO LOGIN:
// authenticationManager.authenticate(email, senha)
//         ↓
// Spring chama loadUserByUsername(email)
//         ↓
// Buscamos o Usuario no banco pelo email
//         ↓
// Convertemos para UserDetails (objeto do Spring Security)
//         ↓
// Spring compara a senha digitada com o hash BCrypt
//         ↓
// Autenticado ou BadCredentialsException
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    // O Spring Security chama esse método automaticamente
    // durante o processo de autenticação
    //
    // username aqui é o email — Spring usa o nome genérico "username"
    // mas pode ser qualquer identificador único
    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        // Busca o usuário no banco pelo email
        // Se não encontrar lança UsernameNotFoundException
        // que o AuthenticationManager converte para BadCredentialsException
        // (não revelamos se o email existe ou não — segurança)
        Usuario usuario = usuarioRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Usuário não encontrado")
                );

        // Converte as roles do nosso Enum para
        // SimpleGrantedAuthority do Spring Security
        // Ex: Role.ROLE_USER → new SimpleGrantedAuthority("ROLE_USER")
        var authorities = usuario.getRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(Collectors.toList());

        // Retorna o UserDetails que o Spring Security entende
        // User.builder() é o builder do Spring Security
        // não confundir com nossa entidade Usuario
        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenha())   // hash BCrypt
                .authorities(authorities)        // roles convertidas
                .disabled(!usuario.isAtivo())   // conta ativa?
                .build();
    }
}