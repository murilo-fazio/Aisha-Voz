package com.example.minilogin.repository;

import com.example.minilogin.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Substitui o antigo JsonUserRepository, que lia o arquivo inteiro do disco a
 * cada login e so sabia acrescentar linhas.
 *
 * As buscas ignoram maiusculas para casar com o indice unico sobre
 * lower(email) criado na migration V2.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
