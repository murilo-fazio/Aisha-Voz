package com.example.minilogin.service;

import com.example.minilogin.dto.AiChatRequest;
import com.example.minilogin.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final RestClient restClient;

    public AiChatService(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Encaminha a mensagem para o ai-service.
     *
     * Cada tipo de falha e registrado com o detalhe que permite diagnosticar:
     * o status e o corpo quando o servico respondeu, a causa quando nem chegou
     * a responder. Para o chamador, todas viram AiUnavailableException com um
     * texto que o usuario final consegue entender.
     */
    public ChatResponse askAi(String message, Long userId, Long conversationId) {
        ChatResponse response;

        try {
            response = restClient.post()
                    .uri("/chat")
                    .body(new AiChatRequest(message, userId, conversationId))
                    .retrieve()
                    .body(ChatResponse.class);

        } catch (RestClientResponseException e) {
            // O servico respondeu, mas com erro. O corpo traz o envelope
            // {"error": {"code": ..., "message": ...}} montado pelo Flask.
            log.error("ai-service respondeu {} em POST /chat. Corpo: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401) {
                throw new AiUnavailableException(
                        "O servico de IA recusou a autenticacao interna. Confira AI_INTERNAL_TOKEN.", e);
            }
            throw new AiUnavailableException("O servico de IA respondeu com erro.", e);

        } catch (ResourceAccessException e) {
            // Nem chegou a responder: servico fora do ar, host errado ou timeout.
            log.error("Falha de rede ao chamar o ai-service em POST /chat.", e);
            throw new AiUnavailableException("Nao foi possivel alcancar o servico de IA.", e);
        }

        if (response == null || response.getResponse() == null || response.getResponse().isBlank()) {
            log.error("ai-service devolveu 200 sem conteudo utilizavel em POST /chat.");
            throw new AiUnavailableException("O servico de IA respondeu sem conteudo.");
        }

        // Devolve a resposta inteira, e nao so o texto: junto pode vir uma acao
        // para a interface (ex.: aumentar as letras), decidida pela IA.
        return response;
    }
}
