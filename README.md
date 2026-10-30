# Aisha — plataforma de chat com IA

Aplicação web de chat com uma assistente de IA ("Aisha"). O projeto é composto por
duas aplicações e um banco, orquestrados por Docker Compose:

| Componente | Pasta | O que faz |
|---|---|---|
| **Web / backend** | `LoginProject/mini-login-json` | Spring Boot 4.0.3 + Thymeleaf. Serve as páginas, cuida do login/sessão e expõe `POST /api/chat`. |
| **Serviço de IA** | `ai-service` | Flask + SDK da OpenAI. Recebe a mensagem do backend, chama o modelo e devolve a resposta. |
| **Banco de dados** | container `postgres` | PostgreSQL 17, banco único da plataforma. |

Quer só colocar para rodar? Vá direto ao **[Tutorial.md](Tutorial.md)**.

---

## Arquitetura

```text
   Navegador
      │  HTML + JS (Thymeleaf)
      ▼
┌─────────────────────┐   POST /chat    ┌──────────────────┐   HTTPS   ┌────────┐
│  Spring Boot :8080  │ ───────────────▶│ ai-service :5000 │ ─────────▶│ OpenAI │
│  (único ponto de    │                 │  (Flask)         │           └────────┘
│   entrada público)  │                 └──────────────────┘
└─────────┬───────────┘                          │
          │ JDBC                                 │ SQL
          ▼                                      ▼
     ┌──────────────────────────────────────────────┐
     │            PostgreSQL 17  :5432              │
     │  esquema versionado por Flyway (lado Java)   │
     └──────────────────────────────────────────────┘
```

Pontos que valem entender antes de mexer no código:

- **O navegador nunca fala com o serviço de IA nem com a OpenAI.** Todo o tráfego
  passa pelo Spring Boot. A chave da OpenAI vive apenas dentro do `ai-service`.
- **O serviço de IA não é exposto na internet.** Por padrão ele é publicado em
  `127.0.0.1:5000`, ou seja, alcançável apenas pela própria máquina e pela rede
  interna do Compose. Requisições vindas do Java carregam o `AI_INTERNAL_TOKEN`.
- **O Java é o dono do esquema do banco.** As migrations do Flyway
  (`src/main/resources/db/migration`) criam e evoluem as tabelas; o Hibernate roda
  com `ddl-auto=validate` e nunca altera estrutura. O serviço Python é apenas
  **consumidor** do mesmo banco: lê e escreve linhas, não cria nem altera tabelas.
- **Os dois serviços continuam separados de propósito.** O Python não será
  absorvido pelo Java.

### Fluxo de uma mensagem

1. O usuário digita no chat (`static/js/chat.js`); o navegador faz
   `POST /api/chat` com `{"message": "..."}`.
2. `ChatController` confere se há usuário na sessão. Sem sessão, responde
   "Usuário não autenticado".
3. `AiChatService` repassa a mensagem para `POST /chat` do `ai-service`.
4. O Flask monta o prompt (personalidade + memória) e chama o modelo da OpenAI.
5. A resposta volta pelo mesmo caminho e é renderizada na tela.

---

## Stack real

Apenas o que está de fato declarado no `pom.xml` e no serviço Python.

**Backend (Java)**

- Java 25, Maven (com wrapper `mvnw` versionado — não precisa de Maven instalado)
- Spring Boot 4.0.3: `starter-web`, `starter-thymeleaf`, `starter-validation`,
  `starter-data-jpa`, `devtools`, `starter-test`
- Driver `org.postgresql:postgresql` (runtime)
- Flyway: `flyway-core` + `flyway-database-postgresql`
- `spring-security-crypto` — **somente** para o hash BCrypt das senhas. O Spring
  Security completo não está habilitado; o controle de acesso é feito à mão,
  checando `HttpSession` em cada controller.

**Serviço de IA (Python)**

- Flask + Flask-CORS
- SDK oficial da OpenAI — modelo padrão `gpt-4o-mini` (configurável por `CHAT_MODEL`)
- Driver PostgreSQL. As dependências ficam em `ai-service/` e são instaladas na
  imagem pelo Dockerfile do serviço.

**Frontend**

- Templates Thymeleaf, CSS e JavaScript puro (sem framework)

**Infra**

- Docker Compose (projeto `aisha`), compatível também com Podman
- PostgreSQL 17 (`postgres:17-alpine`), dados em volume nomeado `aisha-pgdata`

> Ollama, LangChain, TinyDB e MySQL **não** fazem parte do projeto. Se aparecerem
> em algum arquivo antigo, é resquício de uma versão anterior, não a realidade
> atual. As dependências reais do Python estão em `ai-service/requirements.txt`.

---

## Estrutura do repositório

```text
.
├── docker-compose.yml              # postgres + ai-service + app (perfil "full")
├── .env.example                    # modelo das variáveis — copie para .env
├── Makefile                        # atalhos: up, up-full, down, logs, db, test...
├── ai-service/
│   ├── app.py                      # servidor Flask: GET /health e POST /chat
│   └── Dockerfile
├── LoginProject/mini-login-json/
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd             # wrapper do Maven
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/example/minilogin/
│       │   ├── Controller/         # PageController, AuthController, ChatController
│       │   ├── service/            # AuthService, AiChatService
│       │   ├── repository/         # repositório de usuários
│       │   ├── dto/                # ChatRequest/ChatResponse, Login/RegisterRequest
│       │   ├── model/              # User
│       │   └── config/             # RestClientConfig (cliente HTTP do ai-service)
│       └── resources/
│           ├── application.properties          # defaults para rodar fora do Docker
│           ├── application-docker.properties   # perfil "docker": hosts da rede interna
│           ├── db/migration/       # migrations do Flyway
│           ├── templates/          # login, register, home, sobre, contato
│           └── static/             # css/ e js/
├── README.md
└── Tutorial.md                     # passo a passo de instalação
```

---

## Começando rápido

Passo a passo completo, com explicação de cada etapa e solução de problemas, em
**[Tutorial.md](Tutorial.md)**. Versão curta:

```bash
cp .env.example .env    # 1. cria seu arquivo de variáveis
$EDITOR .env            # 2. preencha OPENAI_API_KEY, POSTGRES_PASSWORD e AI_INTERNAL_TOKEN
make up                 # 3. sobe postgres + ai-service
```

Depois escolha **um** dos dois modos de trabalho:

| Modo | Comando | Quando usar |
|---|---|---|
| **Desenvolvimento** | `make up` + rodar o Spring pela IDE (ou `./mvnw spring-boot:run`) | Dia a dia: debug e hot reload no Java, dependências containerizadas. Publica Postgres e `ai-service` no host, porque o Java roda fora da rede do Compose. |
| **Tudo containerizado** | `make up-full` | Uso normal e demonstração. Publica **uma única porta** (8080); banco e IA ficam só na rede interna. Não exige JDK nem Maven na máquina. |
| **Containerizado + depuração** | `make up-full-tools` | Igual ao anterior, mas reabre Postgres e `ai-service` no host para inspecionar com DBeaver ou `curl`. |

Nos dois casos a aplicação responde em <http://localhost:8080>.

`make up` **não** sobe o app Java: o serviço `app` está no perfil `full` justamente
para o Spring da IDE não brigar pela porta 8080 com um container.

### Atalhos do Makefile

Rode `make` (ou `make help`) para ver a lista. Os principais:

| Alvo | O que faz |
|---|---|
| `make up` | Sobe postgres + ai-service em segundo plano. |
| `make up-full` | Sobe a stack inteira, incluindo o app Spring Boot. |
| `make down` | Para e remove os containers, **preservando** os dados do banco. |
| `make logs` | Acompanha os logs em tempo real (Ctrl+C sai dos logs, não derruba nada). |
| `make ps` | Mostra o estado dos containers. |
| `make build` | Reconstrói as imagens do `ai-service` e do `app`. |
| `make db` | Abre um `psql` interativo dentro do container do Postgres. |
| `make test` | Roda os testes do Maven via wrapper. |
| `make clean` | Derruba tudo e **apaga o volume do banco** (perde os dados). |

O Makefile detecta sozinho se usa `docker compose` ou `podman compose`. Para forçar:
`make up COMPOSE="podman compose"`.

---

## Variáveis de ambiente

Todas ficam no `.env` na raiz do projeto. O `.env` **não** é versionado; o modelo
versionado é o `.env.example`, que contém apenas nomes e placeholders — nunca um
segredo real.

| Variável | Para que serve | Obrigatória | Padrão |
|---|---|---|---|
| `OPENAI_API_KEY` | Chave da API da OpenAI usada pelo `ai-service`. | **Sim** | — |
| `POSTGRES_PASSWORD` | Senha do usuário do banco. Usada pelo Postgres, pelo Java e pelo Python. | **Sim** | — |
| `AI_INTERNAL_TOKEN` | Token compartilhado entre o Spring Boot e o `ai-service`; o Python recusa requisições sem ele. | **Sim** | — |
| `POSTGRES_DB` | Nome do banco. | Não | `aisha` |
| `POSTGRES_USER` | Usuário dono do banco. | Não | `aisha` |
| `POSTGRES_PORT` | Porta do Postgres no host. Só vale nos modos que carregam o `docker-compose.tools.yml` (`make up`, `make up-full-tools`); em `make up-full` o banco não é publicado. | Não | `5433` |
| `AI_PORT` | Porta do `ai-service` no host. Como acima, só vale nos modos com o `docker-compose.tools.yml`. | Não | `5000` |
| `AI_BIND` | Interface onde o `ai-service` é publicado. `127.0.0.1` = só esta máquina; `0.0.0.0` expõe na rede local. | Não | `127.0.0.1` |
| `CHAT_MODEL` | Modelo da OpenAI usado nas respostas. | Não | `gpt-4o-mini` |
| `APP_PORT` | Porta do Spring Boot publicada no host (usada no modo `up-full`). | Não | `8080` |

**Nenhuma variável obrigatória tem valor padrão em lugar nenhum do código.** Se
faltar, o processo falha no boot dizendo qual variável está ausente — de propósito.
É melhor não subir do que subir quebrado.

Para gerar os segredos:

```bash
openssl rand -base64 24   # POSTGRES_PASSWORD
openssl rand -hex 32      # AI_INTERNAL_TOKEN
```

### Variáveis internas do Compose

Estas você normalmente não define no `.env`; o `docker-compose.yml` as injeta nos
containers, e o `application.properties` cai nos defaults de `localhost` quando o
Spring roda fora do Docker.

| Variável | Quem usa | Valor dentro do Compose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Java | `jdbc:postgresql://postgres:5432/aisha` |
| `AI_SERVICE_URL` | Java | `http://ai-service:5000` |
| `AI_CONNECT_TIMEOUT_SECONDS` / `AI_READ_TIMEOUT_SECONDS` | Java | `5` / `60` (segundos) |
| `PGHOST` / `PGPORT` | Python | `postgres` / `5432` |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Python | repassadas do `.env` |

O `ai-service` ainda aceita alguns ajustes finos, todos opcionais:
`LOG_LEVEL` (`INFO`), `OPENAI_TIMEOUT_SECONDS` (`30`), `MAX_HISTORY` (`5`, quantas
mensagens antes de resumir a conversa), `MEMORY_MAX_SUMMARIES` (`50`, teto de
resumos injetados no prompt — segura o custo por requisição), `CORS_ORIGINS`
(`http://localhost:8080`) e `DB_POOL_MIN` / `DB_POOL_MAX` / `DB_POOL_TIMEOUT`.

### Os dois perfis do Spring

- **Sem perfil (padrão):** `application.properties` aponta para `localhost` — é o
  cenário de rodar o Spring pela IDE contra as dependências containerizadas.
- **Perfil `docker`:** ativado pelo `SPRING_PROFILES_ACTIVE=docker` da imagem, troca
  os defaults pelos nomes de host da rede interna (`postgres`, `ai-service`) e move o
  arquivo legado de usuários para `/app/data/users.txt`.

Nos dois casos os segredos vêm apenas do ambiente. Nenhum valor real mora em
arquivo de configuração.

### Endereços: dentro x fora do Compose

| De onde você chama | Postgres | ai-service |
|---|---|---|
| De dentro de um container | `postgres:5432` | `http://ai-service:5000` |
| Da sua máquina, com `make up` ou `make up-full-tools` | `localhost:${POSTGRES_PORT}` | `http://localhost:${AI_PORT}` |
| Da sua máquina, com `make up-full` | não publicado — use `make db` | não publicado |

`localhost` dentro de um container aponta para o próprio container, não para o
host. Por isso a configuração containerizada usa o **nome do serviço**.

---

## Endpoints

**Spring Boot (8080)**

| Método | Rota | Descrição |
|---|---|---|
| GET | `/` | Redireciona para `/home` (logado) ou `/login`. |
| GET | `/login`, `/register` | Formulários. |
| POST | `/login`, `/register` | Autenticação e cadastro. |
| POST | `/logout` | Encerra a sessão. |
| GET | `/home`, `/sobre`, `/contato` | Páginas internas; exigem sessão. |
| POST | `/api/chat` | Envia a mensagem para a IA. Exige sessão. |

```json
// POST /api/chat — requisição
{ "message": "Olá, Aisha!" }

// resposta
{ "response": "Oi. O que você quer saber?" }
```

**ai-service (5000) — interno, não deve ser chamado pelo navegador**

| Método | Rota | Autenticação | Descrição |
|---|---|---|---|
| GET | `/health` | nenhuma | Confere se o banco responde. Usado pelo healthcheck do Compose. Devolve `200 {"status":"ok","db":"ok"}` ou `503` com `"status":"degraded"`. |
| POST | `/chat` | header `X-Internal-Token` | Mesmo contrato de requisição/resposta acima. Sem o token, `401`. |

Erros do `ai-service` seguem um envelope único:

```json
{ "error": { "code": "upstream_error", "message": "O provedor de IA demorou demais para responder." } }
```

Testando o serviço de IA direto (com o `.env` carregado no shell):

```bash
curl -s localhost:5000/health
curl -s -X POST localhost:5000/chat \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: $AI_INTERNAL_TOKEN" \
  -d '{"message":"oi"}'
```

---

## Estado atual do projeto

Esta seção existe para você não descobrir na marra. O que **ainda não** está pronto:

- **Esquema de domínio em construção.** As tabelas de conversas, mensagens e
  anexos ainda estão sendo modeladas. A persistência do chat hoje se resume à
  tabela `conversation_summaries` (migration `V1`), lida e escrita pelo
  `ai-service`.
- **Identidade por usuário ainda não chegou ao chat.** O login existe e protege as
  páginas, mas o `ai-service` **não separa contexto por usuário**: a memória e o
  histórico recente são globais ao processo. Na prática, enquanto isso não for
  implementado, **o que um usuário conversa pode influenciar a resposta dada a
  outro usuário**. Não use este projeto com dados sensíveis nem em ambiente
  público.
- ~~Cadastro de usuários em arquivo local.~~ **Resolvido.** Os usuários vivem na
  tabela `users` (migration `V2`), com unicidade garantida por índice único sobre
  `lower(email)`. O `users.txt` e o `JsonUserRepository` foram removidos.
- **Sem Spring Security completo.** A proteção das rotas é uma checagem manual de
  `HttpSession` em cada controller. Não há CSRF token, nem papéis, nem expiração
  configurada de sessão.

Resumindo: é um projeto de aprendizado em evolução e roda bem localmente. Não é um
sistema multiusuário pronto para produção, e este README não vai fingir que é.

---

## Autoria

Desenvolvido por Guilherme Lima — <https://github.com/Guilherme-lima-18>

Projeto de estudo sobre integração entre um backend Java e um serviço de IA em Python.
