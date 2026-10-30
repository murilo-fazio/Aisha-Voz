package com.example.minilogin.service;

import com.example.minilogin.PostgresTestBase;
import com.example.minilogin.model.Conversation;
import com.example.minilogin.model.Message;
import com.example.minilogin.model.User;
import com.example.minilogin.repository.ConversationRepository;
import com.example.minilogin.repository.MessageRepository;
import com.example.minilogin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da Fase 2: o estado da conversa saiu da memoria do processo e passou a
 * viver no banco, com dono.
 *
 * A garantia central verificada aqui e a de propriedade: nenhum caminho pode
 * devolver a conversa de uma pessoa para outra. Era exatamente isso que faltava
 * quando o contexto era uma lista global no ai-service.
 */
@SpringBootTest
class ConversationServiceTest extends PostgresTestBase {

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
        // As mensagens saem primeiro: o banco tem ON DELETE CASCADE, mas o
        // deleteAll do JPA emite um DELETE por tabela e esbarraria na chave
        // estrangeira se a ordem fosse invertida.
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        ana = criarUsuario("Ana", "ana@aisha.dev");
        bruno = criarUsuario("Bruno", "bruno@aisha.dev");
    }

    private Long criarUsuario(String nome, String email) {
        return userRepository.save(new User(null, nome, email, "$2a$10$hashdefachada")).getId();
    }

    @Test
    void abreUmaConversaQuandoOUsuarioAindaNaoTemNenhuma() {
        Conversation conversa = conversationService.currentConversation(ana, null);

        assertThat(conversa.getId()).isNotNull();
        assertThat(conversa.getUserId()).isEqualTo(ana);
    }

    @Test
    void ignoraOIdDeUmaConversaDeOutroUsuario() {
        Conversation doBruno = conversationService.currentConversation(bruno, null);

        // Cenario do ataque: a sessao da Ana apresenta o id da conversa do
        // Bruno. Ela tem de receber uma conversa dela, nunca a dele.
        Conversation daAna = conversationService.currentConversation(ana, doBruno.getId());

        assertThat(daAna.getId()).isNotEqualTo(doBruno.getId());
        assertThat(daAna.getUserId()).isEqualTo(ana);
    }

    @Test
    void ignoraUmIdDeConversaInexistente() {
        Conversation conversa = conversationService.currentConversation(ana, 999_999L);

        assertThat(conversa.getUserId()).isEqualTo(ana);
    }

    @Test
    void continuaNaConversaDaSessaoQuandoElaEDoProprioUsuario() {
        Conversation primeira = conversationService.currentConversation(ana, null);
        conversationService.appendUserMessage(primeira.getId(), "oi");
        Conversation segunda = conversationService.startNewConversation(ana);

        // Com o id na sessao, a conversa antiga continua acessivel mesmo nao
        // sendo mais a mais recente.
        Conversation retomada = conversationService.currentConversation(ana, primeira.getId());

        assertThat(retomada.getId()).isEqualTo(primeira.getId());
        assertThat(retomada.getId()).isNotEqualTo(segunda.getId());
    }

    @Test
    void guardaPerguntaERespostaNaOrdemEmQueOChatAsExibe() {
        Conversation conversa = conversationService.currentConversation(ana, null);

        conversationService.appendUserMessage(conversa.getId(), "Qual e a capital do Chile?");
        conversationService.appendAssistantMessage(conversa.getId(), "Santiago.");

        List<Message> historico = conversationService.history(conversa.getId());

        assertThat(historico).hasSize(2);
        assertThat(historico.get(0).isFromUser()).isTrue();
        assertThat(historico.get(0).getContent()).isEqualTo("Qual e a capital do Chile?");
        assertThat(historico.get(1).isFromUser()).isFalse();
        assertThat(historico.get(1).getContent()).isEqualTo("Santiago.");
    }

    @Test
    void naoMisturaOHistoricoDeUsuariosDiferentes() {
        Conversation daAna = conversationService.currentConversation(ana, null);
        Conversation doBruno = conversationService.currentConversation(bruno, null);

        conversationService.appendUserMessage(daAna.getId(), "segredo da Ana");
        conversationService.appendUserMessage(doBruno.getId(), "assunto do Bruno");

        assertThat(conversationService.history(doBruno.getId()))
                .extracting(Message::getContent)
                .containsExactly("assunto do Bruno");
    }

    @Test
    void usaAPrimeiraMensagemComoTituloESoAPrimeira() {
        Conversation conversa = conversationService.currentConversation(ana, null);

        conversationService.appendUserMessage(conversa.getId(), "  Me   explica   recursao ");
        conversationService.appendUserMessage(conversa.getId(), "e agora, sobre ponteiros");

        Conversation recarregada = conversationRepository.findById(conversa.getId()).orElseThrow();
        assertThat(recarregada.getTitle()).isEqualTo("Me explica recursao");
    }

    @Test
    void encurtaTitulosLongos() {
        Conversation conversa = conversationService.currentConversation(ana, null);
        String pergunta = "a".repeat(200);

        conversationService.appendUserMessage(conversa.getId(), pergunta);

        String titulo = conversationRepository.findById(conversa.getId()).orElseThrow().getTitle();
        // O limite protege a coluna, que aceita 200 caracteres.
        assertThat(titulo).hasSize(80).endsWith("…");
    }

    @Test
    void naoAbreUmaSegundaConversaVaziaAoPedirNovoChat() {
        Conversation primeira = conversationService.startNewConversation(ana);
        Conversation segunda = conversationService.startNewConversation(ana);

        // Clicar em "Novo chat" duas vezes sem escrever nada nao deve deixar
        // uma conversa vazia orfa para tras.
        assertThat(segunda.getId()).isEqualTo(primeira.getId());
        assertThat(conversationRepository.findByUserIdOrderByUpdatedAtDesc(ana)).hasSize(1);
    }

    @Test
    void listaAsConversasComAMaisRecenteNoTopo() {
        Conversation antiga = conversationService.startNewConversation(ana);
        conversationService.appendUserMessage(antiga.getId(), "primeira conversa");
        Conversation nova = conversationService.startNewConversation(ana);
        conversationService.appendUserMessage(nova.getId(), "segunda conversa");

        // A ordem da barra lateral vem do updated_at, tocado a cada mensagem.
        assertThat(conversationService.listConversations(ana))
                .extracting(Conversation::getId)
                .containsExactly(nova.getId(), antiga.getId());
    }

    @Test
    void naoListaConversasDeOutroUsuario() {
        Conversation doBruno = conversationService.currentConversation(bruno, null);
        conversationService.appendUserMessage(doBruno.getId(), "assunto do Bruno");

        assertThat(conversationService.listConversations(ana)).isEmpty();
    }

    @Test
    void abreUmaConversaNovaQuandoAAtualJaTemMensagens() {
        Conversation primeira = conversationService.currentConversation(ana, null);
        conversationService.appendUserMessage(primeira.getId(), "oi");

        Conversation nova = conversationService.startNewConversation(ana);

        assertThat(nova.getId()).isNotEqualTo(primeira.getId());
        // A conversa nova nasce sem contexto: e isso que o botao promete.
        assertThat(conversationService.history(nova.getId())).isEmpty();
    }
}
