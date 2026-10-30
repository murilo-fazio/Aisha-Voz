# ============================================================
#  app.py  -  Servico de IA da Aisha  |  Flask + OpenRouter + Postgres
# ------------------------------------------------------------
#  Este servico e CONSUMIDOR do banco. O dono do esquema e a
#  aplicacao Java (via Flyway). Aqui nao se cria nem se altera
#  tabela: se o esquema nao existir, o erro aparece no log.
#
#  O Java tambem e quem grava as mensagens. Este servico LE o
#  contexto da conversa, e so escreve na tabela de resumos.
# ============================================================

from __future__ import annotations

import hmac
import json
import logging
import os

from dotenv import load_dotenv
from flask import Flask, Response, jsonify, request
from flask_cors import CORS
from openai import APITimeoutError, OpenAI, OpenAIError
from psycopg.conninfo import make_conninfo
from psycopg_pool import ConnectionPool

# Carrega o .env quando rodando fora do container (desenvolvimento local).
load_dotenv()

# ============================================================
# Logging  -  nunca usar print neste arquivo
# ============================================================
logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)-8s [%(name)s] %(message)s",
)
logger = logging.getLogger("aisha.ai_service")


# ============================================================
# Configuracao  -  100% por variavel de ambiente
# ============================================================
def require_env(name: str) -> str:
    """
    Le uma variavel de ambiente obrigatoria.

    Nao existe default falso: se a variavel faltar, o processo falha no boot
    com mensagem clara dizendo exatamente qual variavel esta ausente.
    """
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(
            f"Variavel de ambiente obrigatoria ausente ou vazia: {name}. "
            "Defina-a no .env (ou no ambiente do container) antes de subir o servico."
        )
    return value


def env_int(name: str, default: int) -> int:
    """Le uma variavel numerica opcional, falhando claramente se o valor for invalido."""
    raw = os.getenv(name, "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise RuntimeError(f"Variavel de ambiente {name} deve ser um numero inteiro; recebido: {raw!r}") from exc


# --- Segredos: obrigatorios, sem default ---
OPENROUTER_API_KEY = require_env("OPENROUTER_API_KEY")
AI_INTERNAL_TOKEN = require_env("AI_INTERNAL_TOKEN")
POSTGRES_PASSWORD = require_env("POSTGRES_PASSWORD")

# --- Banco: nomes de host/porta/base tem defaults reais (nao sao segredo) ---
PGHOST = os.getenv("PGHOST", "postgres")
PGPORT = env_int("PGPORT", 5432)
POSTGRES_DB = os.getenv("POSTGRES_DB", "aisha")
POSTGRES_USER = os.getenv("POSTGRES_USER", "aisha")

# --- Provedor de IA: OpenRouter ---
# O OpenRouter expoe uma API compativel com a da OpenAI, entao continuamos
# usando o SDK oficial da OpenAI: muda apenas a base_url e a chave. Nenhuma
# dependencia nova em requirements.txt.
OPENROUTER_BASE_URL = os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1")

# Identificador do modelo no catalogo do OpenRouter, sempre no formato
# "fornecedor/modelo" (o antigo "gpt-4o-mini" da OpenAI nao vale mais aqui).
# A lista atual fica em https://openrouter.ai/models — confira antes de trocar,
# porque os identificadores mudam com o tempo.
#
# NUNCA use um id terminado em ":batch": essas variantes sao para
# processamento assincrono em lote, nao para chat interativo.
CHAT_MODEL = os.getenv("CHAT_MODEL", "google/gemini-3.8-flash")

AI_TIMEOUT_SECONDS = env_int("AI_TIMEOUT_SECONDS", 30)
# Chamadas de audio podem demorar mais que uma resposta de chat.
AUDIO_TIMEOUT_SECONDS = env_int("AUDIO_TIMEOUT_SECONDS", 120)
STT_MODEL = os.getenv("STT_MODEL", "openai/whisper-1").strip()
TTS_MODEL = os.getenv("TTS_MODEL", "microsoft/mai-voice-2.1").strip()
TTS_VOICE = os.getenv("TTS_VOICE", "pt-BR-Luana:MAI-Voice-2.1").strip()
VOICE_MAX_AUDIO_MB = env_int("VOICE_MAX_AUDIO_MB", 10)
TTS_MAX_CHARS = env_int("TTS_MAX_CHARS", 4000)

# --- Controle de raciocinio ---
# Os modelos "flash" recentes do Gemini raciocinam antes de responder, e o
# raciocinio consome o mesmo orcamento de max_tokens da resposta. Medido neste
# projeto com max_tokens=150: 140 tokens foram para o raciocinio e a frase
# visivel chegou cortada no meio. Com effort=low o raciocinio zera, a resposta
# sai completa e a chamada fica cerca de 14x mais barata.
#
# Valores aceitos pelo OpenRouter: minimal, low, medium, high.
# Deixe vazio para nao enviar o parametro — necessario para modelos que nao
# suportam raciocinio e recusam o campo.
AI_REASONING_EFFORT = os.getenv("AI_REASONING_EFFORT", "low").strip()

# --- Teto de tokens por resposta ---
# effort=low e uma DICA, nao uma garantia: medido neste projeto, cerca de 1 em
# cada 5 chamadas raciocina assim mesmo, mesmo modelo e mesmo provedor. Quando
# isso acontece com um teto apertado, o raciocinio consome a cota inteira, a
# resposta sai truncada no meio (finish_reason=length) e o usuario recebe algo
# como "):**" em vez de uma frase.
#
# A folga abaixo cobre esse caso. Ela nao encarece o uso normal: cobra-se pelos
# tokens efetivamente gerados, e o teto so limita. Nas chamadas em que o modelo
# nao raciocina, a resposta continua custando os mesmos ~25 tokens de sempre.
# Quem segura o tamanho da resposta e a instrucao do system prompt ("uma ou
# duas frases"), nao o teto.
AI_MAX_TOKENS = env_int("AI_MAX_TOKENS", 800)
AI_SUMMARY_MAX_TOKENS = env_int("AI_SUMMARY_MAX_TOKENS", 500)

# Atribuicao opcional do OpenRouter: aparece no ranking publico deles e no
# painel de uso da conta, ajudando a identificar de onde veio cada chamada.
OPENROUTER_APP_URL = os.getenv("OPENROUTER_APP_URL", "http://localhost:8080")
OPENROUTER_APP_NAME = os.getenv("OPENROUTER_APP_NAME", "Aisha")

# --- Memoria de longo prazo ---
# Teto de resumos injetados no system prompt: sem isso o prompt cresce sem
# limite a cada conversa e o custo por requisicao sobe indefinidamente.
MEMORY_MAX_SUMMARIES = env_int("MEMORY_MAX_SUMMARIES", 50)

# --- CORS: origem restrita, nunca liberado para o mundo ---
CORS_ORIGINS = [
    origin.strip()
    for origin in os.getenv("CORS_ORIGINS", "http://localhost:8080").split(",")
    if origin.strip()
]

# --- Bind do servidor de desenvolvimento ---
# Dentro do container o bind correto e 0.0.0.0:5000; a restricao de exposicao
# ao host e feita pelo docker-compose (AI_BIND/AI_PORT), nao aqui.
AI_LISTEN_HOST = os.getenv("AI_LISTEN_HOST", "0.0.0.0")
AI_LISTEN_PORT = env_int("AI_LISTEN_PORT", 5000)

# --- Pool de conexoes ---
DB_POOL_MIN = env_int("DB_POOL_MIN", 1)
DB_POOL_MAX = env_int("DB_POOL_MAX", 5)
DB_POOL_TIMEOUT = env_int("DB_POOL_TIMEOUT", 10)


# ============================================================
# Constantes de conversa  -  comportamento preservado
# ============================================================
BOT_NAME = "Aisha"
# Quantas mensagens nao resumidas uma conversa acumula antes de virar um resumo
# na memoria de longo prazo. Conta mensagens da conversa no banco, nao um
# contador em memoria do processo.
MAX_HISTORY = env_int("MAX_HISTORY", 5)

PERSONALITY = (
    "Você é uma inteligência artificial acolhedora, paciente e gentil, criada para oferecer companhia, conversas significativas e apoio emocional para pessoas idosas. Sua principal missão é ajudar a reduzir sentimentos de solidão, promover bem-estar emocional e tornar o dia das pessoas mais leve através de interações calorosas e respeitosas."
    "Sua personalidade transmite serenidade, empatia e sabedoria. Você conversa como um amigo atencioso que sempre tem tempo para ouvir, sem julgamentos ou pressa. Demonstra interesse genuíno pelas histórias, experiências e memórias dos usuários, valorizando suas vivências e conhecimentos acumulados ao longo da vida."
    "Você utiliza uma linguagem clara, educada e acessível, evitando termos técnicos desnecessários. Suas respostas são acolhedoras, positivas e fáceis de compreender. Quando necessário, explica conceitos de forma simples e gradual, respeitando o ritmo de cada pessoa."
    "Você gosta de incentivar conversas sobre família, amizades, hobbies, música, filmes, histórias de vida, viagens, culinária, jardinagem, animais de estimação e lembranças especiais. Sempre busca criar momentos agradáveis e estimular recordações positivas, ajudando os usuários a compartilharem suas experiências e sentimentos."
    "Quando alguém demonstra tristeza, ansiedade, medo ou solidão, você responde com empatia e compreensão. Você reconhece os sentimentos da pessoa, oferece apoio emocional e incentiva conexões saudáveis com familiares, amigos ou profissionais quando apropriado. Seu papel não é substituir relacionamentos humanos, mas complementar o apoio emocional e a companhia."
    "Você celebra pequenas conquistas, encoraja hábitos saudáveis e promove autoestima, autonomia e senso de propósito. Gosta de sugerir atividades leves e prazerosas, como leitura, caminhadas, exercícios simples, jogos de memória, música ou novas aprendizagens."
    "Seu tom é sempre respeitoso, caloroso, paciente e encorajador. Você nunca é rude, sarcástico, agressivo ou impaciente. Mesmo quando corrige informações, faz isso de forma gentil e cuidadosa."
    "Características principais:"
    "* Empática e acolhedora."
    "* Paciente e compreensiva."
    "* Educada e respeitosa."
    "* Boa ouvinte"
    "* Incentivadora e positiva."
    "* Clara e acessível."
    "* Interessada em histórias e memórias pessoais."
    "* Companheira para conversas do dia a dia."
    "* Promotora de bem-estar emocional."
    "Objetivo central:"
    "Fazer com que cada usuário se sinta ouvido, valorizado, respeitado e acompanhado, transformando cada conversa em uma experiência humana, confortável e significativa."
)

# O contexto da conversa NAO vive mais na memoria do processo.
#
# Antes havia aqui uma lista global compartilhada por todas as requisicoes:
# como ela nao tinha nocao de quem estava falando, o que um usuario conversava
# entrava no prompt do outro. Era tambem o que prendia o gunicorn a um unico
# worker, ja que um segundo processo teria a sua propria copia da lista.
#
# Agora o contexto e lido do banco por conversa, e a memoria de longo prazo e
# filtrada por usuario. O servico ficou sem estado.


# ============================================================
# Cliente do provedor de IA (OpenRouter via SDK da OpenAI)
# ============================================================
client = OpenAI(
    api_key=OPENROUTER_API_KEY,
    base_url=OPENROUTER_BASE_URL,
    timeout=AI_TIMEOUT_SECONDS,
    default_headers={
        "HTTP-Referer": OPENROUTER_APP_URL,
        "X-Title": OPENROUTER_APP_NAME,
    },
)

# Cliente separado para audio: preserva o timeout curto do chat sem interromper
# transcricoes ou geracoes de voz que precisem de mais tempo. Usa a mesma chave
# e o mesmo endpoint do OpenRouter.
audio_client = OpenAI(
    api_key=OPENROUTER_API_KEY,
    base_url=OPENROUTER_BASE_URL,
    timeout=AUDIO_TIMEOUT_SECONDS,
    default_headers={
        "HTTP-Referer": OPENROUTER_APP_URL,
        "X-Title": OPENROUTER_APP_NAME,
    },
)
logger.info(
    "Provedor de IA: %s (modelo %s, reasoning effort %s).",
    OPENROUTER_BASE_URL, CHAT_MODEL, AI_REASONING_EFFORT or "nao enviado",
)


# ============================================================
# Pool de conexoes Postgres (psycopg 3)
# ============================================================
# make_conninfo escapa corretamente valores com espaco/aspas (ex.: senha).
CONNINFO = make_conninfo(
    host=PGHOST,
    port=PGPORT,
    dbname=POSTGRES_DB,
    user=POSTGRES_USER,
    password=POSTGRES_PASSWORD,
)

# open=False + open() explicito: o pool nao bloqueia o boot se o banco ainda
# nao subiu. Quem responde pela disponibilidade do banco e o /health.
pool = ConnectionPool(
    conninfo=CONNINFO,
    min_size=DB_POOL_MIN,
    max_size=DB_POOL_MAX,
    timeout=DB_POOL_TIMEOUT,
    open=False,
    name="aisha-pg-pool",
)
pool.open()
logger.info(
    "Pool Postgres configurado: host=%s porta=%s base=%s usuario=%s (min=%s max=%s)",
    PGHOST, PGPORT, POSTGRES_DB, POSTGRES_USER, DB_POOL_MIN, DB_POOL_MAX,
)


# ============================================================
# Funcoes de Memoria (Postgres)
# ============================================================
def save_summary(user_id: int, summary: str) -> None:
    """Salva um resumo para um usuario, evitando duplicatas exatas dele."""
    try:
        with pool.connection() as conn, conn.cursor() as cur:
            # A checagem de duplicata e por usuario: dois usuarios podem
            # legitimamente ter resumos identicos, e um nao deve suprimir o do
            # outro.
            cur.execute(
                "SELECT id FROM conversation_summaries WHERE user_id = %s AND summary = %s LIMIT 1",
                (user_id, summary),
            )
            if cur.fetchone() is None:
                cur.execute(
                    "INSERT INTO conversation_summaries (user_id, summary) VALUES (%s, %s)",
                    (user_id, summary),
                )
                logger.info("Resumo salvo para o usuario %s (%d caracteres).", user_id, len(summary))
            else:
                logger.debug("Resumo identico ja existente para o usuario %s.", user_id)
    except Exception:
        logger.exception("Falha ao salvar resumo em conversation_summaries.")


def load_memory(user_id: int) -> str:
    """
    Carrega os resumos mais recentes DO USUARIO, limitados por MEMORY_MAX_SUMMARIES.

    A consulta pega os N mais novos (ORDER BY created_at DESC) e a ordem e
    revertida em seguida, para que o prompt receba a memoria em ordem cronologica.

    O filtro por user_id e o que impede a Aisha de "lembrar" de uma conversa
    que nao foi com esta pessoa.
    """
    try:
        with pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                """
                SELECT summary
                  FROM conversation_summaries
                 WHERE user_id = %s
                 ORDER BY created_at DESC, id DESC
                 LIMIT %s
                """,
                (user_id, MEMORY_MAX_SUMMARIES),
            )
            rows = cur.fetchall()
        return "\n".join(row[0] for row in reversed(rows))
    except Exception:
        logger.exception("Falha ao carregar memoria de conversation_summaries.")
        return ""


def load_context(conversation_id: int) -> list[dict[str, str]]:
    """
    Mensagens da conversa ainda nao resumidas, em ordem cronologica.

    O corte por summarized_until_message_id reproduz o comportamento antigo de
    esvaziar a lista logo apos gerar um resumo — so que por conversa, e nao
    para o processo inteiro.
    """
    with pool.connection() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT m.id, m.role, m.content
              FROM messages m
              JOIN conversations c ON c.id = m.conversation_id
             WHERE m.conversation_id = %s
               AND m.id > COALESCE(c.summarized_until_message_id, 0)
             ORDER BY m.id
            """,
            (conversation_id,),
        )
        return [{"id": r[0], "role": r[1], "content": r[2]} for r in cur.fetchall()]


def mark_summarized(conversation_id: int, last_message_id: int) -> None:
    """Avanca a marca de ate onde a conversa ja foi resumida."""
    with pool.connection() as conn, conn.cursor() as cur:
        cur.execute(
            "UPDATE conversations SET summarized_until_message_id = %s WHERE id = %s",
            (last_message_id, conversation_id),
        )


# ============================================================
# Camada do provedor de IA  -  erros mapeados para status HTTP corretos
# ============================================================
class UpstreamError(RuntimeError):
    """Falha ao falar com o provedor de IA, ja com o status HTTP a devolver."""

    def __init__(self, message: str, status: int) -> None:
        super().__init__(message)
        self.status = status


def _log_usage(label: str, response) -> None:
    """Registra o consumo de tokens da chamada: sem isso ninguem sabe o custo."""
    usage = getattr(response, "usage", None)
    if usage is None:
        logger.warning("Chamada '%s' nao retornou dados de uso de tokens.", label)
        return
    # Tokens de raciocinio entram no completion_tokens mas nao aparecem na
    # resposta; sem registra-los o custo sobe sem explicacao visivel.
    details = getattr(usage, "completion_tokens_details", None)
    reasoning_tokens = getattr(details, "reasoning_tokens", None) if details else None

    logger.info(
        "Uso de tokens [%s] modelo=%s prompt_tokens=%s completion_tokens=%s "
        "reasoning_tokens=%s total_tokens=%s",
        label,
        CHAT_MODEL,
        getattr(usage, "prompt_tokens", None),
        getattr(usage, "completion_tokens", None),
        reasoning_tokens,
        getattr(usage, "total_tokens", None),
    )


def _chat_completion(label: str, **kwargs):
    """Executa uma chamada de chat completion tratando falhas e registrando o custo."""
    # 'reasoning' nao existe na API da OpenAI; e uma extensao do OpenRouter, e
    # por isso viaja em extra_body em vez de ser um argumento do SDK.
    if AI_REASONING_EFFORT:
        kwargs.setdefault("extra_body", {})["reasoning"] = {"effort": AI_REASONING_EFFORT}

    try:
        response = client.chat.completions.create(model=CHAT_MODEL, **kwargs)
    except APITimeoutError as exc:
        logger.error("Timeout na chamada '%s' ao OpenRouter: %s", label, exc)
        raise UpstreamError("O provedor de IA demorou demais para responder.", 504) from exc
    except OpenAIError as exc:
        logger.exception("Falha na chamada '%s' ao OpenRouter.", label)
        raise UpstreamError("Falha ao consultar o provedor de IA.", 502) from exc

    # Truncamento nunca deve passar em silencio: e assim que uma resposta pela
    # metade chega ao usuario parecendo resposta legitima.
    choice = response.choices[0] if response.choices else None
    if choice is not None and choice.finish_reason == "length":
        logger.warning(
            "Resposta de '%s' truncada por atingir o teto de tokens. Aumente "
            "AI_MAX_TOKENS (chat) ou AI_SUMMARY_MAX_TOKENS (resumo).",
            label,
        )

    _log_usage(label, response)
    return response


# ============================================================
# Ferramentas que a IA pode acionar
# ============================================================
#
# A Aisha nao mexe na tela por conta propria: ela PEDE, e o navegador executa.
# O modelo escolhe chamar a ferramenta, este servico devolve o pedido junto da
# resposta em "action", e o JavaScript aplica.
#
# Por que ferramenta e nao uma regra de texto ("se a pessoa escrever 'aumentar
# fonte', entao..."): quem precisa deste recurso e justamente quem nao vai
# escrever o comando exato. "nao to enxergando direito", "ta muito miudo isso
# aqui", "meus olhos nao ajudam" e "da pra deixar maior?" precisam levar ao
# mesmo lugar, e reconhecer isso e o que o modelo faz melhor do que qualquer
# lista de palavras-chave que a gente escrevesse.
TOOL_TAMANHO_TEXTO = {
    "type": "function",
    "function": {
        "name": "ajustar_tamanho_do_texto",
        "description": (
            "Aumenta ou diminui o tamanho das letras na tela do usuario. "
            "Use sempre que a pessoa indicar dificuldade para ler ou pedir letras "
            "maiores ou menores, mesmo que de forma indireta — por exemplo "
            "'nao consigo ler', 'esta muito pequeno', 'ficou grande demais'."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "direcao": {
                    "type": "string",
                    "enum": ["aumentar", "diminuir", "normal"],
                    "description": "aumentar, diminuir, ou normal para voltar ao tamanho padrao.",
                },
                "passos": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 3,
                    "description": (
                        "Intensidade do ajuste: 1 para um ajuste comum, 2 ou 3 quando a "
                        "pessoa pedir bem maior ou disser que ainda nao consegue ler."
                    ),
                },
            },
            "required": ["direcao"],
        },
    },
}

TOOLS = [TOOL_TAMANHO_TEXTO]

# Instrucao anexada ao system prompt. Fica separada da PERSONALITY de proposito:
# personalidade e decisao de produto, isto e mecanica da ferramenta.
INSTRUCAO_FERRAMENTAS = (
    "Voce pode mudar o tamanho das letras da tela chamando a ferramenta "
    "ajustar_tamanho_do_texto. Faca isso sempre que a pessoa tiver dificuldade de "
    "leitura, mesmo que ela nao peca com essas palavras. Depois de ajustar, diga o "
    "que fez em uma frase curta e pergunte se ficou bom assim ou se ela quer maior "
    "ainda, para que ela possa responder e voce ajustar de novo."
)


def executar_ferramenta(nome: str, argumentos: dict) -> tuple[dict, dict]:
    """
    Executa uma chamada de ferramenta.

    Devolve (resultado_para_o_modelo, acao_para_o_navegador). O resultado conta
    ao modelo o que aconteceu, para ele escrever a frase de confirmacao; a acao
    e o que o front recebe e aplica.
    """
    if nome != "ajustar_tamanho_do_texto":
        logger.warning("Modelo chamou ferramenta desconhecida: %s", nome)
        return {"status": "erro", "motivo": "ferramenta inexistente"}, {}

    direcao = str(argumentos.get("direcao", "aumentar")).strip().lower()
    if direcao not in ("aumentar", "diminuir", "normal"):
        direcao = "aumentar"

    try:
        passos = int(argumentos.get("passos", 1))
    except (TypeError, ValueError):
        passos = 1
    passos = max(1, min(3, passos))

    acao = {"tipo": "tamanho_texto", "direcao": direcao, "passos": passos}

    # O limite real e do navegador (ele conhece o tamanho atual); aqui apenas
    # descrevemos o que foi pedido, para o modelo confirmar com a pessoa.
    resultado = {
        "status": "ok",
        "direcao": direcao,
        "passos": passos,
        "observacao": (
            "O tamanho das letras foi ajustado na tela. Confirme com a pessoa se "
            "ficou bom assim ou se ela prefere outro tamanho."
        ),
    }
    return resultado, acao


def summarize_events(history: list[str]) -> str:
    """Resume o historico recente usando o modelo para criar um snapshot de memoria."""
    history_text = "\n".join(history)
    response = _chat_completion(
        "summarize",
        temperature=0.3,
        max_tokens=AI_SUMMARY_MAX_TOKENS,
        messages=[
            {
                "role": "user",
                "content": (
                    "Resuma o seguinte histórico de conversa em um parágrafo, "
                    "focando apenas em eventos importantes e detalhes relevantes. Ignore conversa fiada. "
                    "Retorne apenas o resumo, nada mais.\n\n"
                    f"Histórico:\n{history_text}"
                ),
            }
        ],
    )
    return (response.choices[0].message.content or "").strip()


# ============================================================
# API Flask
# ============================================================
app = Flask(__name__)
# Limite total inclui uma pequena margem para o multipart/form-data.
VOICE_MAX_AUDIO_BYTES = VOICE_MAX_AUDIO_MB * 1024 * 1024
app.config["MAX_CONTENT_LENGTH"] = VOICE_MAX_AUDIO_BYTES + 1024 * 1024

# CORS restrito: apenas as origens declaradas em CORS_ORIGINS.
CORS(
    app,
    resources={r"/*": {"origins": CORS_ORIGINS}},
    allow_headers=["Content-Type", "X-Internal-Token"],
    methods=["GET", "POST", "OPTIONS"],
)


def error_response(code: str, message: str, status: int):
    """Envelope de erro unico para todas as rotas."""
    return jsonify({"error": {"code": code, "message": message}}), status


@app.before_request
def enforce_internal_token():
    """
    Guard de token interno: o servico so aceita chamadas do backend Java.

    /health fica de fora porque o healthcheck do docker-compose bate nela sem
    credenciais. O preflight OPTIONS tambem passa: o navegador nao envia
    headers customizados no preflight.
    """
    if request.path == "/health" or request.method == "OPTIONS":
        return None

    provided = request.headers.get("X-Internal-Token", "")
    if not hmac.compare_digest(provided, AI_INTERNAL_TOKEN):
        logger.warning("Requisicao rejeitada por token interno invalido: %s %s", request.method, request.path)
        return error_response("unauthorized", "Token interno ausente ou invalido.", 401)
    return None


@app.errorhandler(Exception)
def handle_unexpected_error(exc):
    """
    Rede de seguranca: qualquer excecao nao tratada volta no mesmo envelope de
    erro, para o backend Java nunca receber HTML de stacktrace do Flask.
    """
    # Excecoes HTTP do proprio Flask (404, 405, 415...) ja trazem code/description.
    status = getattr(exc, "code", None)
    if isinstance(status, int) and 400 <= status < 600:
        description = str(getattr(exc, "description", "") or "Requisicao invalida.")
        return error_response("http_error", description, status)

    logger.exception("Erro nao tratado em %s %s", request.method, request.path)
    return error_response("internal_error", "Erro interno no servico de IA.", 500)


@app.get("/health")
def health():
    """Liveness/readiness: confirma que o banco responde. Nao exige token."""
    try:
        with pool.connection(timeout=DB_POOL_TIMEOUT) as conn, conn.cursor() as cur:
            cur.execute("SELECT 1")
            cur.fetchone()
    except Exception:
        logger.exception("Healthcheck falhou: banco de dados indisponivel.")
        return jsonify({"status": "degraded", "db": "error"}), 503
    return jsonify({"status": "ok", "db": "ok"}), 200


@app.post("/stt")
def transcribe_audio():
    """Recebe audio do Spring e devolve a transcricao em portugues."""
    uploaded = request.files.get("audio")
    if uploaded is None:
        return error_response("missing_audio", "Nenhum arquivo de audio foi enviado.", 400)

    filename = (uploaded.filename or "audio.webm").rsplit("/", 1)[-1].rsplit("\\", 1)[-1]
    audio_bytes = uploaded.read()
    if not audio_bytes:
        return error_response("empty_audio", "O arquivo de audio esta vazio.", 400)
    if len(audio_bytes) > VOICE_MAX_AUDIO_BYTES:
        return error_response("audio_too_large", f"O audio deve ter no maximo {VOICE_MAX_AUDIO_MB} MB.", 413)

    try:
        result = audio_client.audio.transcriptions.create(
            model=STT_MODEL,
            file=(filename, audio_bytes, uploaded.mimetype or "application/octet-stream"),
            language="pt",
        )
    except APITimeoutError as exc:
        logger.error("Timeout na transcricao de audio via OpenRouter.")
        return error_response("audio_timeout", "A transcricao demorou demais. Tente gravar um audio mais curto.", 504)
    except OpenAIError as exc:
        logger.exception("Falha na transcricao de audio via OpenRouter.")
        return error_response("transcription_failed", "Nao foi possivel transcrever o audio.", 502)

    transcript = (getattr(result, "text", None) or "").strip()
    if not transcript:
        return error_response("empty_transcription", "Nao consegui identificar fala nesse audio.", 422)

    logger.info("Audio transcrito: modelo=%s bytes=%s caracteres=%s", STT_MODEL, len(audio_bytes), len(transcript))
    return jsonify({"text": transcript})


@app.post("/tts")
def synthesize_speech():
    """Gera MP3 a partir do texto usando a voz configurada no OpenRouter."""
    data = request.get_json(silent=True) or {}
    text = data.get("text")
    if not isinstance(text, str) or not text.strip():
        return error_response("empty_text", "Informe o texto que deve ser falado.", 400)

    text = text.strip()
    if len(text) > TTS_MAX_CHARS:
        return error_response("text_too_large", f"O texto deve ter no maximo {TTS_MAX_CHARS} caracteres.", 413)

    try:
        audio = audio_client.audio.speech.create(
            model=TTS_MODEL,
            voice=TTS_VOICE,
            input=text,
            response_format="mp3",
        )
    except APITimeoutError as exc:
        logger.error("Timeout na geracao de voz via OpenRouter.")
        return error_response("speech_timeout", "A geracao da voz demorou demais. Tente novamente.", 504)
    except OpenAIError as exc:
        logger.exception("Falha na geracao de voz via OpenRouter.")
        return error_response("speech_failed", "Nao foi possivel gerar o audio da resposta.", 502)

    content = getattr(audio, "content", b"")
    if not content:
        logger.error("OpenRouter devolveu audio vazio para o modelo %s.", TTS_MODEL)
        return error_response("empty_audio_response", "O provedor devolveu um audio vazio.", 502)

    response = Response(content, mimetype="audio/mpeg")
    response.headers["Cache-Control"] = "no-store"
    response.headers["Content-Disposition"] = 'inline; filename="aisha.mp3"'
    logger.info("Voz gerada: modelo=%s voz=%s caracteres=%s bytes=%s", TTS_MODEL, TTS_VOICE, len(text), len(content))
    return response


@app.post("/chat")
def chat():
    # 1. Ler e validar a requisicao.
    #
    # user_id e conversation_id vem do backend Java, derivados da sessao — o
    # navegador nunca os informa. Sao obrigatorios: sem eles nao ha como saber
    # de quem e a memoria, e responder "sem dono" foi exatamente o problema que
    # esta versao encerra.
    data = request.get_json(silent=True) or {}
    user_input = (data.get("message") or "").strip()

    if not user_input:
        return error_response("empty_message", "Nenhuma mensagem fornecida.", 400)

    try:
        user_id = int(data["user_id"])
        conversation_id = int(data["conversation_id"])
    except (KeyError, TypeError, ValueError):
        logger.warning("Requisicao sem user_id/conversation_id validos.")
        return error_response(
            "missing_identity",
            "Requisicao sem identificacao de usuario ou conversa.",
            400,
        )

    try:
        # 2. Contexto recente da conversa. O Java ja gravou a mensagem atual
        #    antes de chamar, entao ela esta inclusa aqui.
        context = load_context(conversation_id)

        # 3. Ao atingir o limite, resumir o trecho e avancar a marca. O
        #    contexto seguinte recomeca do zero, mas o resumo entra na memoria
        #    de longo prazo do usuario.
        if len(context) >= MAX_HISTORY:
            history_text = [
                f"{'Usuário' if m['role'] == 'user' else BOT_NAME}: {m['content']}"
                for m in context
            ]
            summary = summarize_events(history_text)
            if summary:
                save_summary(user_id, summary)
            # A marca para na mensagem ANTERIOR a atual. A pergunta que acabou
            # de chegar ainda nao foi respondida, entao precisa continuar
            # visivel nas proximas requisicoes: marca-la aqui faria a chamada
            # seguinte carregar a resposta da Aisha sem a pergunta que a gerou.
            # Assim o que sobra no banco e exatamente o que a linha abaixo
            # mantem em memoria.
            if len(context) >= 2:
                mark_summarized(conversation_id, context[-2]["id"])
            context = [{"role": "user", "content": user_input}]

        # 4. Memoria de longo prazo, so deste usuario.
        memory = load_memory(user_id)

        # 5. System prompt: personalidade + memoria + ferramentas.
        system_prompt = (
            f"Você é {BOT_NAME}, um chatbot com uma personalidade marcante.\n\n"
            f"{PERSONALITY}\n\n"
            f"Isso é o que você lembra do usuário:\n{memory if memory else 'Nada ainda.'}\n\n"
            f"{INSTRUCAO_FERRAMENTAS}\n\n"
            "Seja breve e direto. Mantenha sua resposta em uma ou duas frases. Responda sempre em português."
        )

        conversa = [
            {"role": "system", "content": system_prompt},
            *[{"role": m["role"], "content": m["content"]} for m in context],
        ]

        # 6. Chamar o modelo, oferecendo as ferramentas.
        response = _chat_completion(
            "chat",
            temperature=0.7,
            max_tokens=AI_MAX_TOKENS,
            messages=conversa,
            tools=TOOLS,
        )

        mensagem = response.choices[0].message
        acao = None

        # 7. O modelo pediu uma ferramenta: executamos, contamos o resultado a
        #    ele e pedimos a frase final. A segunda chamada vai SEM tools, para
        #    a conversa nao entrar num vaivem de chamadas.
        if getattr(mensagem, "tool_calls", None):
            conversa.append({
                "role": "assistant",
                "content": mensagem.content or "",
                "tool_calls": [
                    {
                        "id": tc.id,
                        "type": "function",
                        "function": {"name": tc.function.name, "arguments": tc.function.arguments},
                    }
                    for tc in mensagem.tool_calls
                ],
            })

            for tc in mensagem.tool_calls:
                try:
                    argumentos = json.loads(tc.function.arguments or "{}")
                except json.JSONDecodeError:
                    logger.warning("Argumentos invalidos na chamada de ferramenta %s.", tc.function.name)
                    argumentos = {}

                resultado, acao_pedida = executar_ferramenta(tc.function.name, argumentos)
                if acao_pedida:
                    # Varias chamadas na mesma resposta: vale a ultima, ja que
                    # todas mexem no mesmo tamanho de letra.
                    acao = acao_pedida

                conversa.append({
                    "role": "tool",
                    "tool_call_id": tc.id,
                    "content": json.dumps(resultado, ensure_ascii=False),
                })

            logger.info("Ferramenta acionada pela IA na conversa %s: %s", conversation_id, acao)

            response = _chat_completion(
                "chat-apos-ferramenta",
                temperature=0.7,
                max_tokens=AI_MAX_TOKENS,
                messages=conversa,
            )
            mensagem = response.choices[0].message

        ai_response = (mensagem.content or "").strip()

    except UpstreamError as exc:
        return error_response("upstream_error", str(exc), exc.status)

    if not ai_response:
        logger.error("Modelo devolveu resposta vazia para a conversa %s.", conversation_id)
        return error_response("empty_response", "O provedor de IA devolveu uma resposta vazia.", 502)

    # 8. Quem grava a resposta e o Java, que e o dono das tabelas de conversa.
    #    A acao viaja junto, mas nao e gravada: e um efeito na tela de agora.
    corpo = {"response": ai_response}
    if acao:
        corpo["action"] = acao
    return jsonify(corpo)


# ============================================================
# Sobre o antigo hook atexit
# ------------------------------------------------------------
# O hook atexit que resumia o contexto ao encerrar o processo FOI REMOVIDO.
# Motivos:
#   - disparava uma chamada paga ao provedor a cada reload do servidor de
#     desenvolvimento e a cada restart do container;
#   - gravava resumo de conversa incompleta/contaminada, poluindo a memoria
#     de longo prazo com material que o usuario nunca terminou de conversar;
#   - nao ha garantia de execucao em SIGKILL, entao nunca foi confiavel.
# A sumarizacao agora acontece apenas no fluxo normal, a cada MAX_HISTORY.
# ============================================================


# ============================================================
# Entry Point  -  apenas desenvolvimento local.
# Em container quem serve e o gunicorn (ver Dockerfile). debug nunca ligado.
# ============================================================
if __name__ == "__main__":
    logger.info(
        "Iniciando servidor de desenvolvimento Flask em %s:%s (modelo %s).",
        AI_LISTEN_HOST, AI_LISTEN_PORT, CHAT_MODEL,
    )
    app.run(host=AI_LISTEN_HOST, port=AI_LISTEN_PORT, debug=False)
