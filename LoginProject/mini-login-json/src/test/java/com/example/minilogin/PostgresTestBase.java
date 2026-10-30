package com.example.minilogin;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes que precisam de banco.
 *
 * Container singleton: iniciado uma unica vez no bloco estatico e mantido de pe
 * ate o fim da JVM.
 *
 * Nao usamos @Testcontainers/@Container aqui de proposito. Essas anotacoes
 * gerenciam o ciclo de vida POR CLASSE de teste: o container pararia ao fim da
 * primeira classe e subiria de novo, em outra porta, para a segunda. Como o
 * Spring reaproveita o contexto entre classes de teste, o datasource em cache
 * continuaria apontando para a porta antiga e toda transacao ficaria pendurada
 * ate estourar o timeout. Com o singleton, o endereco nao muda durante a suite.
 *
 * A imagem e a mesma do docker-compose de proposito: testar contra uma versao
 * diferente da que roda em producao esconde justamente o que estes testes
 * deveriam pegar.
 *
 * @ServiceConnection injeta url/usuario/senha no contexto Spring, dispensando
 * qualquer datasource declarado no application.properties de teste.
 */
public abstract class PostgresTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
