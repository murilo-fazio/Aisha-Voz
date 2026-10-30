package com.example.minilogin.Controller;

import com.example.minilogin.config.AuthenticationInterceptor;
import com.example.minilogin.dto.ChatRequest;
import com.example.minilogin.dto.ChatResponse;
import com.example.minilogin.model.Conversation;
import com.example.minilogin.service.AiChatService;
import com.example.minilogin.service.AiUnavailableException;
import com.example.minilogin.service.ConversationService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /** Chave da conversa ativa na sessao do navegador. */
    public static final String SESSION_CONVERSATION = "currentConversationId";

    private final AiChatService aiChatService;
    private final ConversationService conversationService;

    public ChatController(AiChatService aiChatService, ConversationService conversationService) {
        this.aiChatService = aiChatService;
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody(required = false) ChatRequest request,
                                             HttpSession session) {

        // A sessao ja foi exigida pelo AuthenticationInterceptor.
        Long userId = (Long) session.getAttribute(AuthenticationInterceptor.SESSION_USER_ID);

        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ChatResponse("Mensagem vazia."));
        }

        String message = request.getMessage().strip();

        // A conversa vem da sessao, mas quem confere o dono e o service.
        Conversation conversation = conversationService.currentConversation(
                userId, (Long) session.getAttribute(SESSION_CONVERSATION));
        session.setAttribute(SESSION_CONVERSATION, conversation.getId());

        // Grava ANTES de chamar a IA: o ai-service monta o contexto lendo as
        // mensagens da conversa no banco, entao a pergunta atual precisa ja
        // estar la quando ele for buscar.
        conversationService.appendUserMessage(conversation.getId(), message);

        log.debug("Mensagem para a IA: usuario={} conversa={} ({} caracteres).",
                userId, conversation.getId(), message.length());

        ChatResponse resposta;
        try {
            resposta = aiChatService.askAi(message, userId, conversation.getId());
        } catch (AiUnavailableException e) {
            // A pergunta fica gravada mesmo sem resposta: some-la faria a
            // mensagem desaparecer da tela do usuario no proximo carregamento.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ChatResponse(e.getMessage()));
        }

        // So o texto e gravado: a acao e um efeito na tela de agora, nao parte da
        // conversa que sera relida amanha.
        conversationService.appendAssistantMessage(conversation.getId(), resposta.getResponse());
        return ResponseEntity.ok(resposta);
    }

    /** Abre uma conversa nova; o "Novo chat" da barra lateral passa a ser real. */
    @PostMapping("/nova")
    public ResponseEntity<Void> novaConversa(HttpSession session) {
        Long userId = (Long) session.getAttribute(AuthenticationInterceptor.SESSION_USER_ID);
        Conversation nova = conversationService.startNewConversation(userId);
        session.setAttribute(SESSION_CONVERSATION, nova.getId());
        return ResponseEntity.noContent().build();
    }
}
