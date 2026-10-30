package com.example.minilogin.config;

import com.example.minilogin.PostgresTestBase;
import com.example.minilogin.model.User;
import com.example.minilogin.repository.ConversationRepository;
import com.example.minilogin.repository.MessageRepository;
import com.example.minilogin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A sessao vive num cookie, e o navegador manda cookie ate em requisicao
 * disparada por outro site. Estes testes descrevem o que separa uma requisicao
 * feita pelas nossas paginas de uma forjada por terceiros.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CsrfTokenInterceptorTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private Long ana;

    @BeforeEach
    void prepararBanco() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
        ana = userRepository.save(new User(null, "Ana", "ana@aisha.dev", "$2a$10$hashdefachada")).getId();
    }

    private MockHttpSession sessaoLogada() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedUser", "Ana");
        session.setAttribute("loggedUserId", ana);
        return session;
    }

    private static String tokenDa(MockHttpSession session) {
        return (String) session.getAttribute(CsrfTokenInterceptor.TOKEN_ATTRIBUTE);
    }

    @Test
    void publicaOTokenNaSessaoENoModeloAoAbrirUmaPagina() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/login").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists(CsrfTokenInterceptor.TOKEN_ATTRIBUTE));

        // Sem o valor na sessao nao ha com o que comparar depois.
        assertThat(tokenDa(session)).isNotBlank();
    }

    @Test
    void recusaLogoutSemToken() throws Exception {
        MockHttpSession session = sessaoLogada();

        // O formulario forjado por outro site nao tem como conhecer o token.
        mockMvc.perform(post("/logout").session(session))
                .andExpect(redirectedUrl("/login?expirado"));

        // A sessao continua de pe: o ataque nao deslogou ninguem.
        assertThat(session.getAttribute("loggedUserId")).isEqualTo(ana);
    }

    @Test
    void recusaLogoutComTokenErrado() throws Exception {
        MockHttpSession session = sessaoLogada();
        mockMvc.perform(get("/home").session(session));

        mockMvc.perform(post("/logout").session(session).param("_csrf", "token-inventado"))
                .andExpect(redirectedUrl("/login?expirado"));

        assertThat(session.getAttribute("loggedUserId")).isEqualTo(ana);
    }

    @Test
    void aceitaLogoutComOTokenDaPropriaPagina() throws Exception {
        MockHttpSession session = sessaoLogada();
        mockMvc.perform(get("/home").session(session));

        mockMvc.perform(post("/logout").session(session).param("_csrf", tokenDa(session)))
                .andExpect(redirectedUrl("/login"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void recusaOChatSemTokenAntesDeChamarAIa() throws Exception {
        MockHttpSession session = sessaoLogada();
        mockMvc.perform(get("/home").session(session));

        // 403 e nao 401: a sessao existe, o que falta e a prova de que a
        // requisicao saiu da nossa pagina. E o corpo vem no formato que o
        // chat.js ja sabe exibir. Se o interceptador deixasse passar, este
        // teste falharia tentando alcancar o ai-service.
        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"oi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("sessao expirou"));
    }

    @Test
    void naoExigeTokenParaLerPaginas() throws Exception {
        mockMvc.perform(get("/home").session(sessaoLogada()))
                .andExpect(status().isOk());
    }

    @Test
    void trocaODeTokenAoTrocarDeSessao() throws Exception {
        MockHttpSession primeira = new MockHttpSession();
        MockHttpSession segunda = new MockHttpSession();

        mockMvc.perform(get("/login").session(primeira));
        mockMvc.perform(get("/login").session(segunda));

        // Token preso a uma sessao: o de uma nao serve para a outra.
        assertThat(tokenDa(primeira)).isNotEqualTo(tokenDa(segunda));
    }
}
