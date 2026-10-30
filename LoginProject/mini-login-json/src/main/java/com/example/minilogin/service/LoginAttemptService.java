package com.example.minilogin.service;

import com.example.minilogin.model.LoginAttempt;
import com.example.minilogin.repository.LoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Freio para tentativas de login.
 *
 * Depois de {@code app.login.max-failures} senhas erradas, o e-mail fica
 * bloqueado por {@code app.login.lock-minutes}. Sem isto, testar a lista das
 * mil senhas mais comuns contra uma conta conhecida e questao de minutos.
 *
 * O bloqueio e por e-mail e por tempo. Duas consequencias assumidas:
 *
 * - Um e-mail inexistente tambem acumula falhas. E de proposito: se apenas as
 *   contas reais fossem bloqueadas, a diferenca de resposta diria quais
 *   e-mails tem cadastro aqui.
 * - Quem souber o e-mail de alguem consegue trancar aquela conta por alguns
 *   minutos de proposito. Por isso a janela e curta e o desbloqueio e
 *   automatico: prender a conta ate um administrador intervir trocaria um
 *   ataque de forca bruta por um ataque de negacao de servico.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final LoginAttemptRepository attempts;
    private final int maxFailures;
    private final Duration lockDuration;

    public LoginAttemptService(LoginAttemptRepository attempts,
                               @Value("${app.login.max-failures:5}") int maxFailures,
                               @Value("${app.login.lock-minutes:15}") long lockMinutes) {
        this.attempts = attempts;
        this.maxFailures = maxFailures;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    /**
     * Barra a tentativa se o e-mail estiver bloqueado.
     *
     * Chamado ANTES de conferir a senha: o objetivo e nao gastar um BCrypt (e
     * nao dar nenhum sinal sobre a conta) enquanto a janela nao passar.
     */
    @Transactional
    public void ensureNotLocked(String email) {
        LoginAttempt attempt = attempts.findById(chave(email)).orElse(null);
        if (attempt == null) {
            return;
        }

        OffsetDateTime agora = OffsetDateTime.now();
        if (attempt.estaBloqueado(agora)) {
            long minutosRestantes = Math.max(1,
                    Duration.between(agora, attempt.getLockedUntil()).toMinutes() + 1);
            throw new TooManyAttemptsException(
                    "Muitas tentativas de login. Tente novamente em " + minutosRestantes
                            + (minutosRestantes == 1 ? " minuto." : " minutos."));
        }

        // A janela venceu: a contagem recomeca do zero, e nao de onde parou.
        if (attempt.getLockedUntil() != null) {
            attempt.liberar();
            attempts.save(attempt);
        }
    }

    @Transactional
    public void registerFailure(String email) {
        String chave = chave(email);
        LoginAttempt attempt = attempts.findById(chave).orElseGet(() -> new LoginAttempt(chave));
        attempt.registrarFalha();

        if (attempt.getFailures() >= maxFailures) {
            attempt.bloquearAte(OffsetDateTime.now().plus(lockDuration));
            // Sem o e-mail no log: ele diria quais contas estao sob ataque, e o
            // proprio log viraria uma lista de e-mails cadastrados.
            log.warn("Limite de tentativas atingido; login bloqueado por {} minutos.",
                    lockDuration.toMinutes());
        }

        attempts.save(attempt);
    }

    /** Entrou: a contagem deixa de existir. */
    @Transactional
    public void registerSuccess(String email) {
        // findById + delete, e nao deleteById: apagar o que nao existe e o caso
        // comum aqui (quem acerta a senha de primeira nunca teve linha), e o
        // comportamento do deleteById para id ausente ja mudou entre versoes
        // do Spring Data.
        attempts.findById(chave(email)).ifPresent(attempts::delete);
    }

    private static String chave(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
