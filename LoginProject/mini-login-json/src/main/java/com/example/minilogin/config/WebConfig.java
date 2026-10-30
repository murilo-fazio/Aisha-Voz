package com.example.minilogin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registro dos interceptadores do MVC.
 *
 * Ambos valem para tudo por padrao. CSS e JS ficam de fora porque sao servidos
 * pelo handler de recursos estaticos: passar por la nao acrescenta protecao
 * nenhuma (sao GET publicos) e so faria cada arquivo abrir uma sessao.
 *
 * A ORDEM IMPORTA. O CSRF vem primeiro: uma requisicao forjada por outro site
 * e recusada antes de qualquer outra pergunta, inclusive antes de se descobrir
 * se ha sessao. Ele tambem e quem cria o token, e a pagina de login precisa
 * receber um mesmo sem ninguem estar autenticado.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] RECURSOS_ESTATICOS = {"/css/**", "/js/**", "/favicon.ico"};

    private final CsrfTokenInterceptor csrfTokenInterceptor;
    private final AuthenticationInterceptor authenticationInterceptor;

    public WebConfig(CsrfTokenInterceptor csrfTokenInterceptor,
                     AuthenticationInterceptor authenticationInterceptor) {
        this.csrfTokenInterceptor = csrfTokenInterceptor;
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(csrfTokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(RECURSOS_ESTATICOS);

        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(RECURSOS_ESTATICOS);
    }
}
