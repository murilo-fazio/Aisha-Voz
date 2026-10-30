package com.example.minilogin.repository;

import com.example.minilogin.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    long countByConversationId(Long conversationId);
}
