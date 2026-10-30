-- ============================================================================
-- V4 — Freio para tentativas de login.
--
-- Ate aqui nao havia limite nenhum: um script podia testar senhas contra
-- /login o dia inteiro, na velocidade da rede. O BCrypt torna cada tentativa
-- cara, o que ajuda, mas nao impede a lista de senhas comuns ser percorrida
-- inteira contra uma conta conhecida.
--
-- POR QUE NO BANCO, e nao num contador em memoria: um contador no processo
-- some a cada restart — e reiniciar a aplicacao e algo que um atacante pode
-- provocar, ou apenas esperar. Alem disso, com mais de uma instancia da
-- aplicacao no ar, cada uma teria a sua propria contagem, multiplicando o
-- limite pelo numero de instancias. Foi a mesma licao da Fase 2.
-- ============================================================================

CREATE TABLE login_attempts (
    -- O e-mail em minusculas E a chave: o login sempre comparou sem diferenciar
    -- caixa, entao "Ana@x.com" e "ana@x.com" precisam somar no mesmo contador —
    -- caso contrario bastaria variar a caixa para multiplicar as tentativas.
    --
    -- Guardamos a chave mesmo quando o e-mail nao existe: responder diferente
    -- para conta existente e inexistente revelaria quais e-mails tem cadastro.
    email_lower  VARCHAR(255) PRIMARY KEY,
    failures     INT          NOT NULL DEFAULT 0,
    -- Nulo enquanto a conta nao esta bloqueada. Bloqueio por tempo, e nao
    -- permanente: uma trava definitiva transformaria o freio em uma forma de
    -- deixar qualquer pessoa fora da propria conta de proposito.
    locked_until TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Serve a limpeza periodica das linhas velhas (tentativas antigas de quem
-- apenas errou a senha uma vez e voltou a entrar normalmente).
CREATE INDEX idx_login_attempts_updated ON login_attempts (updated_at);
