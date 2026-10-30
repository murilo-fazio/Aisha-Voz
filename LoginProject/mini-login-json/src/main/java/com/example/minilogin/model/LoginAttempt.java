package com.example.minilogin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Contagem de tentativas de login malsucedidas de um e-mail.
 *
 * A chave e o proprio e-mail em minusculas — nao ha id gerado. A linha existe
 * enquanto ha falhas a lembrar e desaparece quando alguem entra com sucesso.
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @Column(name = "email_lower", length = 255)
    private String emailLower;

    @Column(nullable = false)
    private int failures;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public LoginAttempt() {
    }

    public LoginAttempt(String emailLower) {
        this.emailLower = emailLower;
        this.failures = 0;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getEmailLower() {
        return emailLower;
    }

    public int getFailures() {
        return failures;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Registra mais uma falha e atualiza o carimbo de tempo. */
    public void registrarFalha() {
        this.failures++;
        this.updatedAt = OffsetDateTime.now();
    }

    public void bloquearAte(OffsetDateTime instante) {
        this.lockedUntil = instante;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Fim do bloqueio: zera a contagem para a janela seguinte. */
    public void liberar() {
        this.failures = 0;
        this.lockedUntil = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean estaBloqueado(OffsetDateTime agora) {
        return lockedUntil != null && lockedUntil.isAfter(agora);
    }
}
