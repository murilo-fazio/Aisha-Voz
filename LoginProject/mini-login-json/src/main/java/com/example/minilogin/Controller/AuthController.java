package com.example.minilogin.Controller;

import com.example.minilogin.dto.LoginRequest;
import com.example.minilogin.dto.RegisterRequest;
import com.example.minilogin.model.User;
import com.example.minilogin.service.AuthService;
import com.example.minilogin.service.EmailAlreadyUsedException;
import com.example.minilogin.service.InvalidCredentialsException;
import com.example.minilogin.service.TooManyAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest registerRequest,
                           BindingResult result,
                           Model model) {

        if (result.hasErrors()) {
            model.addAttribute("registerRequest", registerRequest);
            return "register";
        }

        try {
            authService.register(registerRequest);
            return "redirect:/login?success";
        } catch (EmailAlreadyUsedException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("registerRequest", registerRequest);
            return "register";
        }
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginRequest loginRequest,
                        BindingResult result,
                        HttpSession session,
                        HttpServletRequest request,
                        Model model) {

        if (result.hasErrors()) {
            model.addAttribute("loginRequest", loginRequest);
            return "login";
        }

        try {
            User user = authService.login(loginRequest);
            // Renova o id da sessao apos autenticar: impede que um id obtido
            // antes do login continue valido depois dele (session fixation).
            session.invalidate();
            session = request.getSession(true);
            session.setAttribute("loggedUserId", user.getId());
            session.setAttribute("loggedUser", user.getName());
            session.setAttribute("loggedEmail", user.getEmail());
            return "redirect:/home";
        } catch (InvalidCredentialsException | TooManyAttemptsException e) {
            // As duas viram a mesma tela; a mensagem e que difere — uma fala de
            // credencial, a outra do tempo de espera.
            model.addAttribute("error", e.getMessage());
            model.addAttribute("loginRequest", loginRequest);
            return "login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}