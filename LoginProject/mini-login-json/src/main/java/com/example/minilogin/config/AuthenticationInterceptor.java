package com.example.minilogin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Exige sessao autenticada em tudo que nao esteja na lista de rotas publicas.
 *
 * O PROBLEMA que isto encerra: cada controller repetia a mesma checagem de
 * HttpSession na mao. Funcionava enquanto ninguem esquecia — e a falha de
 * esquecer nao aparece em lugar nenhum: a rota simplesmente responde a todo
 * mundo, sem erro, sem log, ate alguem notar.
 *
 * Aqui a regra se inverte: o padrao e NEGAR. Uma rota nova nasce protegida, e
 * torna-la publica passa a ser um ato explicito — acrescentar o caminho a
 * lista abaixo, onde a decisao fica visivel e revisavel.
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationInterceptor.class);

    /** Atributo gravado no login; a presenca dele e o que define "autenticado". */
    public static final String SESSION_USER_ID = "loggedUserId";

    /**
     * Rotas alcancaveis sem sessao.
     *
     * "/" entra porque ele proprio decide para onde mandar cada visitante, e
     * "/error" porque a pagina de erro nao pode depender de estar logado —
     * seria um erro dentro do tratamento do erro.
     */
    private static final Set<String> PUBLIC_PATHS = Set.of("/", "/login", "/register", "/error");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (PUBLIC_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_USER_ID) != null) {
            return true;
        }

        log.debug("Acesso sem sessao a {} {}.", request.getMethod(), request.getRequestURI());

        if (request.getRequestURI().startsWith("/api/")) {
            // Chamada de programa: 401 com o mesmo corpo que o front ja exibe.
            // Redirecionar aqui devolveria o HTML do login para um fetch que
            // espera JSON.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"response\":\"Usuario nao autenticado.\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/login");
        }
        return false;
    }
}
