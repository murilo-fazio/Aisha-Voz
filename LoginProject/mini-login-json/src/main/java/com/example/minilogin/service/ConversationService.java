package com.example.minilogin.service;

import com.example.minilogin.model.Conversation;
import com.example.minilogin.model.Message;
import com.example.minilogin.repository.ConversationRepository;
import com.example.minilogin.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dono das conversas e das mensagens.
 *
 * Cada metodo abre a sua propria transacao curta. A chamada ao ai-service NAO
 * acontece aqui de proposito: ela pode levar dezenas de segundos, e manter uma
 * transacao (e uma conexao do pool) aberta durante esse tempo esgotaria o pool
 * com poucos usuarios simultaneos. Quem orquestra a ordem e o ChatController.
 */
@Service
public class ConversationService {

    /** Tamanho maximo do titulo derivado da primeira mensagem. */
    private static final int TITLE_MAX = 80;

    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public ConversationService(ConversationRepository conversations, MessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    /**
     * Devolve a conversa em que o usuario deve continuar.
     *
     * O id vem da sessao, mas e sempre reconferido contra o dono: um id que nao
     * seja do usuario e simplesmente ignorado, e ele recebe uma conversa sua.
     */
    @Transactional
    public Conversation currentConversation(Long userId, Long candidateId) {
        if (candidateId != null) {
            var owned = conversations.findByIdAndUserId(candidateId, userId);
            if (owned.isPresent()) {
                return owned.get();
            }
        }
        return conversations.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .orElseGet(() -> conversations.save(new Conversation(userId)));
    }

    /**
     * Abre uma conversa nova — ou devolve a ultima, se ela ainda estiver vazia.
     *
     * Sem essa checagem cada clique em "Novo chat" inseria uma linha em
     * conversations: quem clicasse cinco vezes antes de escrever ficaria com
     * quatro conversas vazias e permanentes na lista.
     */
    @Transactional
    public Conversation startNewConversation(Long userId) {
        var ultima = conversations.findFirstByUserIdOrderByUpdatedAtDesc(userId);
        if (ultima.isPresent() && messages.countByConversationId(ultima.get().getId()) == 0) {
            return ultima.get();
        }
        return conversations.save(new Conversation(userId));
    }

    @Transactional
    public void appendUserMessage(Long conversationId, String content) {
        messages.save(new Message(conversationId, Message.ROLE_USER, content));

        conversations.findById(conversationId).ifPresent(conversation -> {
            // A primeira fala do usuario vira o titulo da conversa.
            if (conversation.getTitle() == null) {
                conversation.setTitle(resumirTitulo(content));
            }
            conversation.touch();
        });
    }

    @Transactional
    public void appendAssistantMessage(Long conversationId, String content) {
        messages.save(new Message(conversationId, Message.ROLE_ASSISTANT, content));
        conversations.findById(conversationId).ifPresent(Conversation::touch);
    }

    @Transactional(readOnly = true)
    public List<Message> history(Long conversationId) {
        return messages.findByConversationIdOrderByIdAsc(conversationId);
    }

    /**
     * As conversas do usuario, mais recentes primeiro — o que a barra lateral
     * mostra. So as dele: a consulta filtra por user_id, nunca lista tudo.
     */
    @Transactional(readOnly = true)
    public List<Conversation> listConversations(Long userId) {
        return conversations.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    private static String resumirTitulo(String content) {
        String limpo = content.strip().replaceAll("\\s+", " ");
        return limpo.length() <= TITLE_MAX ? limpo : limpo.substring(0, TITLE_MAX - 1) + "…";
    }
}
