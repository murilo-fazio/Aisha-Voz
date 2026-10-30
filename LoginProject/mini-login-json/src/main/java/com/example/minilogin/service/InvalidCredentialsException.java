package com.example.minilogin.service;

/**
 * E-mail inexistente ou senha errada.
 *
 * Um unico tipo para os dois casos de proposito: distinguir "esse e-mail nao
 * existe" de "a senha esta errada" permitiria descobrir quais e-mails tem conta
 * na plataforma.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
