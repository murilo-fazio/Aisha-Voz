package com.example.minilogin.service;

/** Ja existe conta com o e-mail informado no cadastro. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String message) {
        super(message);
    }
}
