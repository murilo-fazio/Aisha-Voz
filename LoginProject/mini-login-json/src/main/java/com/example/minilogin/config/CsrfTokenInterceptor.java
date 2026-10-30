package com.example.minilogin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Protecao contra CSRF (falsificacao de requisicao entre sites).
 *
 * O PROBLEMA: a sessao vive num cookie, e o navegador envia cookie em toda
 * requisicao para este dominio — inclusive nas disparadas por outro site. Sem
 * esta checagem, uma pagina qualquer que a pessoa abrisse noutra aba poderia
 * enviar um formulario para /api/chat, /logout ou /register em nome dela, e o
 * servidor obedeceria: a sessao esta valida, afinal.
 *
 * A DEFESA: cada sessao carrega um valor aleatorio que o site externo nao tem
 * como ler (a politica de mesma origem do navegador o impede). Toda requisicao
 * que muda estado precisa apresenta-lo, no campo _csrf do formulario ou no
 * header X-CSRF-Token. Requisicoes de leitura (GET, HEAD) nao exigem nada.
 *
 * Isto seria de graca com o Spring Security completo; enquanto ele nao entra, a
 * protecao existe aqui, num unico ponto, e nao espalhada pelos controllers.
 */
@Component
public class CsrfTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenInterceptor.class);

    /** Onde o valor fica guardado na sessao, e como chega aos templates. */
    public static final String TOKEN_ATTRIBUTE = "csrfToken";

    /** Campo escondido dos formularios HTML. */
    public static final String TOKEN_PARAMETER = "_csrf";

    /** Header usado pelas chamadas fetch do front. */
    public static final String TOKEN_HEADER = "X-CSRF-Token";

    /** Metodos que apenas leem: nao mudam estado, nao precisam do token. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        HttpSession session = request.getSession(true);
        String expected = (String) session.getAttribute(TOKEN_ATTRIBUTE);

        if (expected == null) {
            // 32 bytes de SecureRandom: imprevisivel o bastante para nao ser
            // adivinhado por quem nao consegue le-lo.
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            expected = ENCODER.encodeToString(bytes);
            session.setAttribute(TOKEN_ATTRIBUTE, expected);
        }

        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }

        String presented = request.getHeader(TOKEN_HEADER);
        if (presented == null) {
            presented = request.getParameter(TOKEN_PARAMETER);
        }

        if (presented == null || !constantTimeEquals(expected, presented)) {
            // O caminho legitimo que cai aqui e a sessao expirada: o token da
            // pagina aberta ha horas nao vale para a sessao nova.
            log.warn("Requisicao {} {} recusada: token CSRF ausente ou invalido.",
                    request.getMethod(), request.getRequestURI());
            recusar(request, response);
            return false;
        }

        return true;
    }

    /**
     * Entrega o token aos templates.
     *
     * Feito aqui, e nao em cada controller, para que uma pagina nova nasca
     * protegida sem ninguem precisar lembrar de nada.
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {

        if (modelAndView == null || modelAndView.getViewName() == null
                || modelAndView.getViewName().startsWith("redirect:")) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            modelAndView.addObject(TOKEN_ATTRIBUTE, session.getAttribute(TOKEN_ATTRIBUTE));
        }
    }

    /**
     * Comparacao em tempo constante.
     *
     * Um equals() comum para na primeira letra diferente, e a diferenca de
     * tempo entre "errou no primeiro caractere" e "errou no ultimo" permite
     * descobrir o valor correto caractere a caractere.
     */
    private static boolean constantTimeEquals(String expected, String presented) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private static void recusar(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (request.getRequestURI().startsWith("/api/")) {
            // O front do chat le este corpo e mostra a frase ao usuario.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"response\":\"Sua sessao expirou. Recarregue a pagina e tente de novo.\"}");
            return;
        }
        // Envio de formulario: a pessoa vai para a tela de login, que explica.
        response.sendRedirect(request.getContextPath() + "/login?expirado");
    }
}
