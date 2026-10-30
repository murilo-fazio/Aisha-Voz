package com.example.minilogin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corpo enviado do Java para o ai-service.
 *
 * Separado do ChatRequest (que e o corpo vindo do navegador) de proposito: o
 * navegador manda apenas a mensagem, e quem esta falando e de onde e decidido
 * aqui, a partir da sessao. Se o usuario pudesse informar o proprio user_id,
 * bastaria trocar um numero para ler a memoria de outra pessoa.
 *
 * Os nomes em snake_case sao os que o Flask espera.
 */
public class AiChatRequest {

    private String message;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("conversation_id")
    private Long conversationId;

    public AiChatRequest() {
    }

    public AiChatRequest(String message, Long userId, Long conversationId) {
        this.message = message;
        this.userId = userId;
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getConversationId() {
        return conversationId;
    }
}
