package com.example.minilogin.Controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Locale;

/**
 * Dados do usuario logado disponiveis em qualquer pagina.
 *
 * A barra lateral aparece em todas as telas internas e sempre mostra o mesmo
 * nome e as mesmas iniciais. Antes cada controller acrescentava "userName" ao
 * modelo por conta propria: quem criasse uma tela nova e esquecesse veria a
 * barra lateral com "Usuário" generico.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("userName")
    public Object userName(HttpSession session) {
        return session.getAttribute("loggedUser");
    }

    /**
     * Iniciais para o avatar: primeira letra do primeiro nome e primeira letra
     * do ultimo. "Maria Aparecida Souza" vira "MS".
     */
    @ModelAttribute("userInitials")
    public String userInitials(HttpSession session) {
        Object nome = session.getAttribute("loggedUser");
        return iniciais(nome == null ? null : nome.toString());
    }

    static String iniciais(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            return "?";
        }

        String[] partes = nomeCompleto.strip().split("\\s+");
        String primeira = String.valueOf(partes[0].charAt(0));

        // Nome unico: uma letra e o que ha. Duas iniciais iguais ("AA") diriam
        // menos do que a letra sozinha.
        if (partes.length == 1) {
            return primeira.toUpperCase(Locale.ROOT);
        }

        String ultima = String.valueOf(partes[partes.length - 1].charAt(0));
        return (primeira + ultima).toUpperCase(Locale.ROOT);
    }
}
