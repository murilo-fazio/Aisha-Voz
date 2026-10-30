package com.example.minilogin.Controller;

import com.example.minilogin.config.AuthenticationInterceptor;
import com.example.minilogin.dto.LoginRequest;
import com.example.minilogin.dto.RegisterRequest;
import com.example.minilogin.model.Conversation;
import com.example.minilogin.service.ConversationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    private final ConversationService conversationService;

    public PageController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/")
    public String root(HttpSession session) {
        if (session.getAttribute("loggedUser") != null) {
            return "redirect:/home";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(Model model, HttpSession session) {
        if (session.getAttribute("loggedUser") != null) {
            return "redirect:/home";
        }
        model.addAttribute("loginRequest", new LoginRequest());
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model, HttpSession session) {
        if (session.getAttribute("loggedUser") != null) {
            return "redirect:/home";
        }
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }

    // Quem exige sessao nestas rotas e o AuthenticationInterceptor, num ponto
    // so. Repetir a checagem aqui daria a impressao de que ela e opcional.
    @GetMapping("/home")
    public String homePage(HttpSession session, Model model) {
        Object user = session.getAttribute("loggedUser");
        Long userId = (Long) session.getAttribute(AuthenticationInterceptor.SESSION_USER_ID);

        // Recarregar a pagina deixa de perder a conversa: o historico e lido do
        // banco e renderizado no servidor, em vez de existir so no DOM.
        Conversation conversa = conversationService.currentConversation(
                userId, (Long) session.getAttribute(ChatController.SESSION_CONVERSATION));
        session.setAttribute(ChatController.SESSION_CONVERSATION, conversa.getId());

        model.addAttribute("mensagens", conversationService.history(conversa.getId()));
        model.addAttribute("conversas", conversationService.listConversations(userId));
        model.addAttribute("conversaAtualId", conversa.getId());
        return "home";
    }

    /**
     * Troca a conversa ativa e volta para o chat.
     *
     * O id vem da URL, ou seja, do usuario — por isso quem decide e o service,
     * que so devolve conversa do proprio dono. Pedir a conversa de outra pessoa
     * nao da erro nem revela que ela existe: a sessao simplesmente continua na
     * conversa que ja era do usuario.
     */
    @GetMapping("/conversa/{id}")
    public String abrirConversa(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute(AuthenticationInterceptor.SESSION_USER_ID);
        Conversation conversa = conversationService.currentConversation(userId, id);
        session.setAttribute(ChatController.SESSION_CONVERSATION, conversa.getId());
        return "redirect:/home";
    }

    @GetMapping("/sobre")
    public String sobrePage() {
        return "sobre";
    }

    @GetMapping("/contato")
    public String contatoPage() {
        return "contato";
    }
}