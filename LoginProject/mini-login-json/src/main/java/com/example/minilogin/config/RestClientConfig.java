package com.example.minilogin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP usado para falar com o ai-service.
 *
 * Tudo aqui vem de propriedade (app.ai.*), nunca de valor escrito no codigo:
 * rodando pela IDE a base-url cai em localhost, e dentro do compose o perfil
 * 'docker' troca por http://ai-service:5000. Antes desta classe o endereco
 * estava fixo em localhost, o que quebrava a stack containerizada — o Java
 * acabava chamando o proprio container.
 */
@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    /** Header que o ai-service exige em toda rota exceto /health. */
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Bean
    public RestClient aiRestClient(
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.internal-token:}") String internalToken,
            @Value("${app.ai.timeout.connect:5}") long connectTimeoutSeconds,
            @Value("${app.ai.timeout.read:60}") long readTimeoutSeconds) {

        // O ai-service responde 401 sem este token. Falhar em silencio aqui foi
        // exatamente o que escondeu o problema ate agora, entao avisamos alto.
        if (internalToken.isBlank()) {
            log.warn(
                    "app.ai.internal-token esta vazio: o ai-service vai recusar todas as chamadas com 401. "
                            + "Defina AI_INTERNAL_TOKEN no ambiente (veja o .env.example).");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        log.info(
                "Cliente do ai-service configurado: base-url={} connect={}s read={}s token={}",
                baseUrl, connectTimeoutSeconds, readTimeoutSeconds,
                internalToken.isBlank() ? "AUSENTE" : "presente");

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(INTERNAL_TOKEN_HEADER, internalToken)
                .build();
    }
}
