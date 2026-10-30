package com.example.minilogin.Controller;

import com.example.minilogin.PostgresTestBase;
import com.example.minilogin.config.AuthenticationInterceptor;
import com.example.minilogin.config.CsrfTokenInterceptor;
import com.example.minilogin.model.User;
import com.example.minilogin.repository.LoginAttemptRepository;
import com.example.minilogin.repository.UserRepository;
import com.example.minilogin.dto.LoginRequest;
import com.example.minilogin.dto.RegisterRequest;
import com.example.minilogin.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Tela de configuracoes: perfil e troca de senha. */
@SpringBootTest
@AutoConfigureMockMvc
class SettingsControllerTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    private Long ana;

    @BeforeEach
    void prepararBanco() {
        loginAttemptRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest cadastro = new RegisterRequest();
        cadastro.setName("Ana Souza");
        cadastro.setEmail("ana@aisha.dev");
        cadastro.setPassword("senha123");
        ana = authService.register(cadastro).getId();
    }

    private MockHttpSession sessao() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedUser", "Ana Souza");
        session.setAttribute("loggedEmail", "ana@aisha.dev");
        session.setAttribute(AuthenticationInterceptor.SESSION_USER_ID, ana);
        return session;
    }

    private String csrfDe(MockHttpSession session) throws Exception {
        mockMvc.perform(get("/configuracoes").session(session));
        return (String) session.getAttribute(CsrfTokenInterceptor.TOKEN_ATTRIBUTE);
    }

    private static LoginRequest acesso(String senha) {
        LoginRequest r = new LoginRequest();
        r.setEmail("ana@aisha.dev");
        r.setPassword(senha);
        return r;
    }

    @Test
    void mostraNomeEmailEIniciaisDoUsuario() throws Exception {
        mockMvc.perform(get("/configuracoes").session(sessao()))
                .andExpect(status().isOk())
                .andExpect(view().name("configuracoes"))
                .andExpect(model().attribute("email", "ana@aisha.dev"))
                .andExpect(model().attribute("userInitials", "AS"));
    }

    @Test
    void exigeSessaoComoQualquerTelaInterna() throws Exception {
        mockMvc.perform(get("/configuracoes")).andExpect(status().is3xxRedirection());
    }

    @Test
    void salvaONomeNovoNoBancoENaSessao() throws Exception {
        MockHttpSession session = sessao();

        mockMvc.perform(post("/configuracoes/perfil")
                        .session(session)
                        .param("_csrf", csrfDe(session))
                        .param("name", "Ana Maria Souza"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("sucesso"));

        assertThat(userRepository.findById(ana).orElseThrow().getName()).isEqualTo("Ana Maria Souza");
        // Sem atualizar a sessao, a barra lateral mostraria o nome antigo ate
        // o proximo login.
        assertThat(session.getAttribute("loggedUser")).isEqualTo("Ana Maria Souza");
    }

    @Test
    void recusaNomeEmBranco() throws Exception {
        MockHttpSession session = sessao();

        mockMvc.perform(post("/configuracoes/perfil")
                        .session(session)
                        .param("_csrf", csrfDe(session))
                        .param("name", "   "))
                .andExpect(model().attributeExists("erro"));

        assertThat(userRepository.findById(ana).orElseThrow().getName()).isEqualTo("Ana Souza");
    }

    @Test
    void trocaASenhaQuandoAAtualConfere() throws Exception {
        MockHttpSession session = sessao();

        mockMvc.perform(post("/configuracoes/senha")
                        .session(session)
                        .param("_csrf", csrfDe(session))
                        .param("currentPassword", "senha123")
                        .param("newPassword", "novasenha")
                        .param("confirmPassword", "novasenha"))
                .andExpect(model().attributeExists("sucesso"));

        assertThatCode(() -> authService.login(acesso("novasenha"))).doesNotThrowAnyException();
    }

    @Test
    void recusaTrocaDeSenhaComASenhaAtualErrada() throws Exception {
        MockHttpSession session = sessao();

        // Sem esta checagem, quem sentasse no computador de alguem com a
        // sessao aberta trocaria a senha e tomaria a conta.
        mockMvc.perform(post("/configuracoes/senha")
                        .session(session)
                        .param("_csrf", csrfDe(session))
                        .param("currentPassword", "chutei")
                        .param("newPassword", "novasenha")
                        .param("confirmPassword", "novasenha"))
                .andExpect(model().attributeExists("erro"));

        assertThatCode(() -> authService.login(acesso("senha123"))).doesNotThrowAnyException();
    }

    @Test
    void recusaQuandoAConfirmacaoNaoBate() throws Exception {
        MockHttpSession session = sessao();

        mockMvc.perform(post("/configuracoes/senha")
                        .session(session)
                        .param("_csrf", csrfDe(session))
                        .param("currentPassword", "senha123")
                        .param("newPassword", "novasenha")
                        .param("confirmPassword", "outracoisa"))
                .andExpect(model().attributeExists("erro"));

        assertThatCode(() -> authService.login(acesso("senha123"))).doesNotThrowAnyException();
    }

    @Test
    void recusaSenhaNovaCurtaDemais() throws Exception {
        MockHttpSession session = sessao();

        mockMvc.perform(post("/configuracoes/senha")
                        .session(session)
                        .param("_csrf", csrfDe(session))
                        .param("currentPassword", "senha123")
                        .param("newPassword", "123")
                        .param("confirmPassword", "123"))
                .andExpect(model().attributeExists("erro"));

        assertThatThrownBy(() -> authService.login(acesso("123"))).isInstanceOf(RuntimeException.class);
    }
}
