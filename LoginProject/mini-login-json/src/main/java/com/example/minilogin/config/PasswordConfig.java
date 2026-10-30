package com.example.minilogin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * O encoder era instanciado com 'new' dentro do AuthService. Como bean, ele
 * passa a ser um ponto unico de configuracao (custo do BCrypt, troca futura de
 * algoritmo) e fica injetavel nos testes.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
