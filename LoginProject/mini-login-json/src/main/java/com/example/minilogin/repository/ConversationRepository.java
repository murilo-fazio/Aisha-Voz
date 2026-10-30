package com.example.minilogin.repository;

import com.example.minilogin.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Busca pelo par (id, dono).
     *
     * Sempre por dono, nunca so por id: e o que impede alguem de enviar o id de
     * uma conversa alheia e passar a conversar dentro dela.
     */
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Conversation> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);
}
