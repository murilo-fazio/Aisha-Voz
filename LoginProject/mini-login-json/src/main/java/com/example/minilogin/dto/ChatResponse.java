package com.example.minilogin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Resposta do chat, usada nos dois sentidos: o que o ai-service devolve para o
 * Java e o que o Java devolve para o navegador.
 *
 * O campo {@code action} carrega um pedido de mudanca na interface decidido
 * pela IA — hoje, aumentar ou diminuir o tamanho das letras quando a pessoa
 * pede. Vem como mapa solto de proposito: quem define o formato e o
 * ai-service, e o Java so repassa. Inventar aqui uma classe por acao obrigaria
 * a mexer no Java a cada acao nova do lado da IA.
 *
 * Fica fora do JSON quando nulo, que e o caso da esmagadora maioria das
 * respostas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    private String response;

    private Map<String, Object> action;

    public ChatResponse() {
    }

    public ChatResponse(String response) {
        this.response = response;
    }

    public ChatResponse(String response, Map<String, Object> action) {
        this.response = response;
        this.action = action;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public Map<String, Object> getAction() {
        return action;
    }

    public void setAction(Map<String, Object> action) {
        this.action = action;
    }
}
