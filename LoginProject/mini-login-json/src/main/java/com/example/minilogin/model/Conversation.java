package com.example.minilogin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Uma conversa pertence a um usuario.
 *
 * As chaves estrangeiras sao Long simples, e nao @ManyToOne. Com
 * open-in-view=false, associacoes preguicosas estouram ao serem tocadas dentro
 * do render do Thymeleaf, fora da transacao. Guardando o id, as consultas ficam
 * explicitas e nao ha carregamento escondido.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 200)
    private String title;

    @Column(name = "summarized_until_message_id")
    private Long summarizedUntilMessageId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;

    public Conversation() {
    }

    public Conversation(Long userId) {
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getSummarizedUntilMessageId() {
        return summarizedUntilMessageId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Marca atividade na conversa; ordena a lista "mais recentes primeiro". */
    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void setSummarizedUntilMessageId(Long id) {
        this.summarizedUntilMessageId = id;
    }
}
