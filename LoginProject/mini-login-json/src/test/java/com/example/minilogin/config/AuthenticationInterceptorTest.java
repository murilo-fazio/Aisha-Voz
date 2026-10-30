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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A regra que a centralizacao criou: o padrao e negar.
 *
 * Estes testes existem para que "esqueci de proteger a rota nova" vire uma
 * falha visivel, e nao uma pagina silenciosamente aberta ao mundo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationInterceptorTest extends PostgresTestBase {

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
        session.setAttribute(AuthenticationInterceptor.SESSION_USER_ID, ana);
        return session;
    }

    @Test
    void mandaParaOLoginQuemPedePaginaInternaSemSessao() throws Exception {
        for (String rota : new String[]{"/home", "/sobre", "/contato", "/conversa/1"}) {
            mockMvc.perform(get(rota))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
    }

    @Test
    void respondeJsonComPara401NasRotasDeApi() throws Exception {
        MockHttpSession session = new MockHttpSession();
        // Token valido, sessao sem login: isola o que esta sendo testado — o
        // que falta aqui e autenticacao, nao a prova de origem.
        mockMvc.perform(get("/login").session(session));
        String csrf = (String) session.getAttribute(CsrfTokenInterceptor.TOKEN_ATTRIBUTE);

        // Redirecionar um fetch para o HTML do login daria ao front uma
        // resposta que ele nao sabe ler.
        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .header(CsrfTokenInterceptor.TOKEN_HEADER, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"oi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("nao autenticado"));
    }

    @Test
    void deixaPassarAsPaginasPublicas() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
        mockMvc.perform(get("/")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void deixaPassarQuemTemSessao() throws Exception {
        mockMvc.perform(get("/home").session(sessaoLogada())).andExpect(status().isOk());
        mockMvc.perform(get("/sobre").session(sessaoLogada())).andExpect(status().isOk());
    }
}
