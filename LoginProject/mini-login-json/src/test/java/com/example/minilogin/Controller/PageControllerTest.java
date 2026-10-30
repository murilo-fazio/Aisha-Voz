package com.example.minilogin.Controller;

import com.example.minilogin.PostgresTestBase;
import com.example.minilogin.model.Conversation;
import com.example.minilogin.model.User;
import com.example.minilogin.repository.ConversationRepository;
import com.example.minilogin.repository.MessageRepository;
import com.example.minilogin.repository.UserRepository;
import com.example.minilogin.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Testes das paginas do chat pelo caminho HTTP.
 *
 * Aqui interessa o que o ConversationServiceTest nao alcanca: que o template
 * home.html realmente renderiza (erro de expressao Thymeleaf so aparece em
 * tempo de execucao) e que a rota /conversa/{id}, cujo id vem do usuario, nao
 * abre conversa alheia.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PageControllerTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    private Long ana;
    private Long bruno;

    @BeforeEach
    void prepararBanco() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        ana = userRepository.save(new User(null, "Ana", "ana@aisha.dev", "$2a$10$hashdefachada")).getId();
        bruno = userRepository.save(new User(null, "Bruno", "bruno@aisha.dev", "$2a$10$hashdefachada")).getId();
    }

    private MockHttpSession sessaoDe(String nome, Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedUser", nome);
        session.setAttribute("loggedUserId", userId);
        return session;
    }

    @Test
    void mandaVisitanteSemSessaoParaOLogin() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void renderizaOChatComOHistoricoEAListaDeConversas() throws Exception {
        Conversation conversa = conversationService.currentConversation(ana, null);
        conversationService.appendUserMessage(conversa.getId(), "Qual e a capital do Chile?");
        conversationService.appendAssistantMessage(conversa.getId(), "Santiago.");

        mockMvc.perform(get("/home").session(sessaoDe("Ana", ana)))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attributeExists("mensagens", "conversas", "conversaAtualId"));
    }

    @Test
    void naoAbreAConversaDeOutroUsuario() throws Exception {
        Conversation doBruno = conversationService.currentConversation(bruno, null);
        conversationService.appendUserMessage(doBruno.getId(), "assunto do Bruno");

        MockHttpSession sessaoDaAna = sessaoDe("Ana", ana);

        // A Ana digita na URL o id da conversa do Bruno.
        mockMvc.perform(get("/conversa/" + doBruno.getId()).session(sessaoDaAna))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));

        Long ativa = (Long) sessaoDaAna.getAttribute(ChatController.SESSION_CONVERSATION);
        assertThat(ativa).isNotNull().isNotEqualTo(doBruno.getId());
        assertThat(conversationRepository.findById(ativa).orElseThrow().getUserId()).isEqualTo(ana);
    }

    @Test
    void trocaDeConversaQuandoElaEDoProprioUsuario() throws Exception {
        Conversation primeira = conversationService.currentConversation(ana, null);
        conversationService.appendUserMessage(primeira.getId(), "oi");
        Conversation segunda = conversationService.startNewConversation(ana);

        MockHttpSession sessao = sessaoDe("Ana", ana);
        sessao.setAttribute(ChatController.SESSION_CONVERSATION, segunda.getId());

        mockMvc.perform(get("/conversa/" + primeira.getId()).session(sessao))
                .andExpect(redirectedUrl("/home"));

        assertThat(sessao.getAttribute(ChatController.SESSION_CONVERSATION)).isEqualTo(primeira.getId());
    }
}
