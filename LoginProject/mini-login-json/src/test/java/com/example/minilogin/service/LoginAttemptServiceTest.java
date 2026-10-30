package com.example.minilogin.service;

import com.example.minilogin.PostgresTestBase;
import com.example.minilogin.dto.LoginRequest;
import com.example.minilogin.dto.RegisterRequest;
import com.example.minilogin.model.LoginAttempt;
import com.example.minilogin.repository.LoginAttemptRepository;
import com.example.minilogin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O freio contra forca bruta.
 *
 * O que estes testes descrevem, no fim, e uma unica promessa: errar a senha
 * muitas vezes seguidas custa tempo de espera, e esse custo nao depende de a
 * conta existir.
 */
@SpringBootTest
class LoginAttemptServiceTest extends PostgresTestBase {

    private static final int LIMITE = 5;

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private LoginAttemptRepository loginAttempts;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void limparBanco() {
        loginAttempts.deleteAll();
        userRepository.deleteAll();

        RegisterRequest cadastro = new RegisterRequest();
        cadastro.setName("Ana");
        cadastro.setEmail("ana@aisha.dev");
        cadastro.setPassword("senha123");
        authService.register(cadastro);
    }

    private static LoginRequest acesso(String email, String senha) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(senha);
        return r;
    }

    private void errarSenha(String email, int vezes) {
        for (int i = 0; i < vezes; i++) {
            assertThatThrownBy(() -> authService.login(acesso(email, "errada")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Test
    void bloqueiaDepoisDoLimiteDeTentativas() {
        errarSenha("ana@aisha.dev", LIMITE);

        // A senha CERTA tambem e recusada: o bloqueio e sobre a conta, nao
        // sobre a senha digitada. E o que torna a forca bruta inviavel.
        assertThatThrownBy(() -> authService.login(acesso("ana@aisha.dev", "senha123")))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessageContaining("Tente novamente em");
    }

    @Test
    void aindaDeixaEntrarUmaTentativaAntesDoLimite() {
        errarSenha("ana@aisha.dev", LIMITE - 1);

        assertThatCode(() -> authService.login(acesso("ana@aisha.dev", "senha123")))
                .doesNotThrowAnyException();
    }

    @Test
    void zeraAContagemQuandoAlguemEntra() {
        errarSenha("ana@aisha.dev", LIMITE - 1);
        authService.login(acesso("ana@aisha.dev", "senha123"));

        // Sem o zeramento, quem errou 4 vezes ontem seria bloqueado ao errar
        // uma unica vez hoje.
        assertThat(loginAttempts.findById("ana@aisha.dev")).isEmpty();
        errarSenha("ana@aisha.dev", LIMITE - 1);
        assertThatCode(() -> authService.login(acesso("ana@aisha.dev", "senha123")))
                .doesNotThrowAnyException();
    }

    @Test
    void liberaSozinhoQuandoAJanelaPassa() {
        errarSenha("ana@aisha.dev", LIMITE);

        // Simula o tempo passando: o bloqueio venceu ha um minuto.
        LoginAttempt bloqueio = loginAttempts.findById("ana@aisha.dev").orElseThrow();
        bloqueio.bloquearAte(OffsetDateTime.now().minusMinutes(1));
        loginAttempts.save(bloqueio);

        assertThatCode(() -> authService.login(acesso("ana@aisha.dev", "senha123")))
                .doesNotThrowAnyException();
    }

    @Test
    void naoDistingueContaExistenteDeInexistente() {
        errarSenha("ninguem@aisha.dev", LIMITE);

        // Se so as contas reais fossem bloqueadas, a diferenca de resposta
        // diria quais e-mails tem cadastro aqui.
        assertThatThrownBy(() -> authService.login(acesso("ninguem@aisha.dev", "qualquer")))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void contaAsTentativasIgnorandoAMaiusculizacaoDoEmail() {
        // Variar a caixa nao pode multiplicar o numero de tentativas.
        errarSenha("ana@aisha.dev", 3);
        errarSenha("ANA@Aisha.DEV", 2);

        assertThatThrownBy(() -> authService.login(acesso("ana@aisha.dev", "senha123")))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void naoGravaTentativaDeQuemAcertaDePrimeira() {
        authService.login(acesso("ana@aisha.dev", "senha123"));

        assertThat(loginAttempts.count()).isZero();
    }
}
