package com.example.minilogin.service;

import com.example.minilogin.PostgresTestBase;
import com.example.minilogin.dto.LoginRequest;
import com.example.minilogin.dto.RegisterRequest;
import com.example.minilogin.model.User;
import com.example.minilogin.repository.LoginAttemptRepository;
import com.example.minilogin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuthServiceTest extends PostgresTestBase {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @BeforeEach
    void limparBanco() {
        // O freio de tentativas sobrevive ao deleteAll dos usuarios: sem
        // limpa-lo, as falhas de um teste contariam no seguinte.
        loginAttemptRepository.deleteAll();
        userRepository.deleteAll();
    }

    private static RegisterRequest cadastro(String nome, String email, String senha) {
        RegisterRequest r = new RegisterRequest();
        r.setName(nome);
        r.setEmail(email);
        r.setPassword(senha);
        return r;
    }

    private static LoginRequest acesso(String email, String senha) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(senha);
        return r;
    }

    @Test
    void gravaOUsuarioComSenhaEmHashENuncaEmTextoPuro() {
        User salvo = authService.register(cadastro("Ana", "ana@aisha.dev", "senha123"));

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getPasswordHash())
                .startsWith("$2a$")
                .isNotEqualTo("senha123");
    }

    @Test
    void recusaCadastroComEmailJaUsadoIgnorandoMaiusculas() {
        authService.register(cadastro("Ana", "ana@aisha.dev", "senha123"));

        // O indice unico da V2 e sobre lower(email): a variacao de caixa nao
        // pode abrir brecha para duas contas com o mesmo e-mail.
        assertThatThrownBy(() -> authService.register(cadastro("Outra", "ANA@Aisha.DEV", "outra123")))
                .isInstanceOf(EmailAlreadyUsedException.class);

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void autenticaIgnorandoAMaiusculizacaoDoEmail() {
        authService.register(cadastro("Ana", "Ana@Aisha.dev", "senha123"));

        User logado = authService.login(acesso("ana@aisha.DEV", "senha123"));

        assertThat(logado.getName()).isEqualTo("Ana");
    }

    @Test
    void recusaSenhaErrada() {
        authService.register(cadastro("Ana", "ana@aisha.dev", "senha123"));

        assertThatThrownBy(() -> authService.login(acesso("ana@aisha.dev", "errada")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void naoRevelaSeOEmailExisteAoErrarAsCredenciais() {
        authService.register(cadastro("Ana", "ana@aisha.dev", "senha123"));

        String comEmailInexistente = capturarMensagem(() -> authService.login(acesso("ninguem@aisha.dev", "x")));
        String comSenhaErrada = capturarMensagem(() -> authService.login(acesso("ana@aisha.dev", "x")));

        // Mensagens diferentes revelariam quais e-mails tem conta na plataforma.
        assertThat(comEmailInexistente).isEqualTo(comSenhaErrada);
    }

    private static String capturarMensagem(Runnable acao) {
        try {
            acao.run();
            throw new AssertionError("esperava falha de autenticacao");
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
    }
}
