# ============================================================
#  Servico de IA da Aisha  -  Flask + OpenAI + Postgres
# ============================================================
FROM python:3.13-slim

# Sem .pyc em disco, saida de log sem buffer (o Docker precisa ver o log na hora).
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Usuario nao-root: o processo nunca roda como root dentro do container.
RUN useradd --system --create-home --shell /usr/sbin/nologin aisha

# Dependencias antes do codigo: mudanca em app.py nao invalida o cache do pip.
COPY requirements.txt ./
RUN pip install --no-cache-dir --requirement requirements.txt

# Agora sim o codigo da aplicacao.
COPY . .

RUN chown -R aisha:aisha /app
USER aisha

EXPOSE 5000

# A imagem slim nao traz curl/wget; o healthcheck usa a propria stdlib do Python.
# urlopen levanta excecao em 503, entao o script sai com codigo != 0 e o
# container e marcado como unhealthy.
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
    CMD python -c "import urllib.request, sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:5000/health', timeout=4).status == 200 else 1)"

# gunicorn em producao (nunca o servidor de desenvolvimento do Flask).
#
# --workers 2: o servico ficou sem estado. O contexto da conversa e a memoria
# de longo prazo agora vivem no Postgres, com dono, entao processos diferentes
# atendem requisicoes diferentes sem quebrar continuidade nenhuma. Ate a Fase 2
# isso era impossivel: havia uma lista global na memoria do processo, e um
# segundo worker teria a sua propria copia.
CMD ["gunicorn", \
     "--bind", "0.0.0.0:5000", \
     "--workers", "2", \
     "--threads", "4", \
     "--timeout", "120", \
     "--access-logfile", "-", \
     "--error-logfile", "-", \
     "app:app"]
