package com.example.minilogin.Controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Iniciais do avatar. Sem banco nem contexto Spring: e uma regra de texto.
 */
class GlobalModelAttributesTest {

    @Test
    void usaAPrimeiraLetraDoPrimeiroEDoUltimoNome() {
        assertThat(GlobalModelAttributes.iniciais("Maria Aparecida Souza")).isEqualTo("MS");
        assertThat(GlobalModelAttributes.iniciais("Ana Lima")).isEqualTo("AL");
    }

    @Test
    void usaSoUmaLetraQuandoHaApenasUmNome() {
        // "AA" nao diria mais do que "A", e ainda daria a impressao de
        // sobrenome que a pessoa nao informou.
        assertThat(GlobalModelAttributes.iniciais("Ana")).isEqualTo("A");
    }

    @Test
    void ignoraEspacosSobrandoEntreOsNomes() {
        assertThat(GlobalModelAttributes.iniciais("  joão   carlos   pereira  ")).isEqualTo("JP");
    }

    @Test
    void devolveInterrogacaoQuandoNaoHaNome() {
        assertThat(GlobalModelAttributes.iniciais(null)).isEqualTo("?");
        assertThat(GlobalModelAttributes.iniciais("   ")).isEqualTo("?");
    }
}
