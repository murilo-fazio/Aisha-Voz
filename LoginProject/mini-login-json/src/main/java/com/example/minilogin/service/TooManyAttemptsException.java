package com.example.minilogin.service;

/**
 * Lancada quando um e-mail excedeu o limite de tentativas de login e ainda
 * esta dentro da janela de bloqueio.
 *
 * Separada de InvalidCredentialsException de proposito: a mensagem aqui e
 * sobre o freio, nao sobre a senha, e o controller precisa distinguir as duas.
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
