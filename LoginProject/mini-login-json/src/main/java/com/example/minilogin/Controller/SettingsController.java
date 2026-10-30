package com.example.minilogin.Controller;

import com.example.minilogin.config.AuthenticationInterceptor;
import com.example.minilogin.model.User;
import com.example.minilogin.service.AuthService;
import com.example.minilogin.service.InvalidCredentialsException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Tela de configuracoes. Por enquanto so a secao de perfil.
 *
 * Aparencia (tema e tamanho das letras) fica de fora de proposito: e escolha
 * do aparelho, nao da conta, e vive no proprio navegador — quem usa o tablet
 * na sala e o celular no quarto costuma querer ajustes diferentes em cada um.
 */
@Controller
public class SettingsController {

    private final AuthService authService;

    public SettingsController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/configuracoes")
    public String configuracoes(HttpSession session, Model model) {
        model.addAttribute("email", session.getAttribute("loggedEmail"));
        return "configuracoes";
    }

    @PostMapping("/configuracoes/perfil")
    public String salvarPerfil(@RequestParam String name, HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute(AuthenticationInterceptor.SESSION_USER_ID);

        if (name == null || name.isBlank()) {
            return telaComErro(session, model, "O nome não pode ficar em branco.");
        }
        if (name.strip().length() > 120) {
            return telaComErro(session, model, "O nome é longo demais.");
        }

        User atualizado = authService.updateName(userId, name);
        // A sessao guarda o nome exibido: sem atualizar aqui, a barra lateral
        // continuaria mostrando o nome antigo ate o proximo login.
        session.setAttribute("loggedUser", atualizado.getName());

        model.addAttribute("email", session.getAttribute("loggedEmail"));
        model.addAttribute("sucesso", "Seu nome foi atualizado.");
        return "configuracoes";
    }

    @PostMapping("/configuracoes/senha")
    public String trocarSenha(@RequestParam String currentPassword,
                              @RequestParam String newPassword,
                              @RequestParam String confirmPassword,
                              HttpSession session,
                              Model model) {

        Long userId = (Long) session.getAttribute(AuthenticationInterceptor.SESSION_USER_ID);

        if (newPassword == null || newPassword.length() < 6) {
            return telaComErro(session, model, "A nova senha deve ter pelo menos 6 caracteres.");
        }
        if (!newPassword.equals(confirmPassword)) {
            return telaComErro(session, model, "A confirmação não é igual à nova senha.");
        }

        try {
            authService.changePassword(userId, currentPassword, newPassword);
        } catch (InvalidCredentialsException e) {
            return telaComErro(session, model, e.getMessage());
        }

        model.addAttribute("email", session.getAttribute("loggedEmail"));
        model.addAttribute("sucesso", "Sua senha foi alterada.");
        return "configuracoes";
    }

    private String telaComErro(HttpSession session, Model model, String mensagem) {
        model.addAttribute("email", session.getAttribute("loggedEmail"));
        model.addAttribute("erro", mensagem);
        return "configuracoes";
    }
}
