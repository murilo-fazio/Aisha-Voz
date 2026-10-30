# ==============================================================================
# Aisha — atalhos para o ambiente containerizado
# ==============================================================================
# Rode `make` (ou `make help`) para ver os alvos disponiveis.
#
# Este Makefile funciona tanto com Docker quanto com Podman rootless: o usuario
# desta maquina nao esta no grupo docker, entao a linha abaixo detecta qual
# runtime esta de fato utilizavel. Para forcar um deles:
#     make up COMPOSE="podman compose"
# ==============================================================================

COMPOSE ?= $(shell command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1 && echo "docker compose" || echo "podman compose")

ENV_FILE := .env
APP_DIR  := LoginProject/mini-login-json

# Sobreposicao que publica Postgres e ai-service no host. Necessaria apenas
# para rodar o Spring pela IDE ou para inspecionar o banco de fora; o modo
# containerizado nao usa, e assim expoe uma unica porta.
TOOLS    := -f docker-compose.yml -f docker-compose.tools.yml

.DEFAULT_GOAL := help

.PHONY: help up up-full up-full-tools down logs ps build restart db test clean check-env

help: ## Lista todos os alvos disponiveis (alvo padrao)
	@printf "\nAisha — alvos disponiveis (runtime detectado: $(COMPOSE))\n\n"
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-9s\033[0m %s\n", $$1, $$2}'
	@printf "\n"

# Guarda interna: nenhum alvo que sobe containers roda sem um .env preenchido.
check-env:
	@test -f $(ENV_FILE) || { \
		printf "\nERRO: arquivo %s nao encontrado na raiz do projeto.\n" "$(ENV_FILE)"; \
		printf "Crie o seu a partir do modelo e preencha os segredos:\n\n"; \
		printf "    cp .env.example .env\n\n"; \
		exit 1; \
	}

up: check-env ## Sobe as dependencias com as portas publicadas; o Spring roda pela IDE
	$(COMPOSE) $(TOOLS) up -d

up-full: check-env ## Sobe a stack inteira; publica SO a porta da aplicacao (8080)
	$(COMPOSE) --profile full up -d
	@printf "\nAisha no ar: http://localhost:$${APP_PORT:-8080}\n\n"

up-full-tools: check-env ## Stack inteira + Postgres e ai-service publicados (depuracao)
	$(COMPOSE) $(TOOLS) --profile full up -d

down: ## Para e remove os containers, preservando os dados do banco
	$(COMPOSE) $(TOOLS) --profile full down --remove-orphans

logs: ## Acompanha os logs de todos os servicos em tempo real
	$(COMPOSE) $(TOOLS) --profile full logs -f --tail=100

ps: ## Mostra o estado dos containers da stack
	$(COMPOSE) $(TOOLS) --profile full ps

build: ## Reconstroi as imagens do ai-service e do app
	$(COMPOSE) --profile full build

restart: ## Reinicia os containers que estao no ar
	$(COMPOSE) --profile full restart

db: ## Abre um psql interativo dentro do container do Postgres
	$(COMPOSE) exec postgres sh -c 'psql -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"'

# Os testes sobem um Postgres descartavel via Testcontainers, que precisa falar
# com um runtime de containers. Com o Docker no ar ele se vira sozinho; no
# Podman rootless e preciso apontar o DOCKER_HOST para o socket do usuario e
# desligar o Ryuk (o coletor do Testcontainers nao funciona bem sem root).
PODMAN_SOCK := /run/user/$(shell id -u)/podman/podman.sock

test: ## Roda os testes do Maven (sobe um Postgres descartavel; precisa de Docker ou Podman)
	@if docker info >/dev/null 2>&1; then \
		cd $(APP_DIR) && ./mvnw test; \
	elif [ -S "$(PODMAN_SOCK)" ]; then \
		printf "Docker indisponivel; usando o socket do Podman.\n"; \
		cd $(APP_DIR) && DOCKER_HOST="unix://$(PODMAN_SOCK)" TESTCONTAINERS_RYUK_DISABLED=true ./mvnw test; \
	else \
		printf "\nERRO: os testes precisam de um runtime de containers.\n"; \
		printf "Inicie o Docker, ou habilite o socket do Podman:\n\n"; \
		printf "    systemctl --user enable --now podman.socket\n\n"; \
		exit 1; \
	fi

clean: ## Derruba tudo e APAGA o volume do banco (perde todos os dados)
	$(COMPOSE) $(TOOLS) --profile full down -v --remove-orphans
