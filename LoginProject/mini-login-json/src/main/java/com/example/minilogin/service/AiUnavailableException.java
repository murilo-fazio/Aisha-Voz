package com.example.minilogin.service;

/**
 * O ai-service nao conseguiu produzir uma resposta.
 *
 * Existe para o controller devolver um status HTTP honesto (503) em vez de
 * mandar o texto do erro como se fosse fala da Aisha, que era o comportamento
 * anterior: qualquer falha virava uma resposta 200 com uma frase generica, sem
 * log, e ninguem descobria o que tinha acontecido de verdade.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
