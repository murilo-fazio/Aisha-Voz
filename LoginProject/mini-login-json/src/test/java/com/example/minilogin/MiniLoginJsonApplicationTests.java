package com.example.minilogin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prova que o contexto sobe inteiro contra um Postgres real — o que inclui as
 * migracoes do Flyway rodando e o Hibernate validando as entidades contra o
 * esquema que elas criaram.
 */
@SpringBootTest
class MiniLoginJsonApplicationTests extends PostgresTestBase {

    @Test
    void contextLoads() {
    }
}
