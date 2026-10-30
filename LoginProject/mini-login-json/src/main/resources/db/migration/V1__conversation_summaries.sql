-- ============================================================================
-- V1 — Memoria de longo prazo da Aisha.
--
-- O ai-service (Python) ja escrevia e lia esta tabela desde o inicio, mas ela
-- nunca havia sido criada: as duas queries falhavam a cada mensagem e o erro
-- so aparecia no log. Pior, a chamada de sumarizacao acontece ANTES da
-- gravacao, entao cada falha custava uma chamada paga ao modelo cujo
-- resultado era descartado.
--
-- As colunas abaixo sao exatamente as que app.py assume:
--   INSERT INTO conversation_summaries (summary) VALUES (%s)
--   SELECT id   FROM conversation_summaries WHERE summary = %s LIMIT 1
--   SELECT summary FROM conversation_summaries ORDER BY created_at DESC, id DESC LIMIT %s
--
-- Nao ha user_id aqui de proposito: a memoria ainda e global ao sistema. A
-- separacao por usuario entra na Fase 2, junto com conversations/messages.
-- ============================================================================

CREATE TABLE conversation_summaries (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    summary    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Casa com o ORDER BY created_at DESC, id DESC ... LIMIT n do load_memory().
CREATE INDEX idx_conversation_summaries_recent
    ON conversation_summaries (created_at DESC, id DESC);
