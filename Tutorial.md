# Tutorial — como rodar a Aisha no seu computador

Este guia é para quem faz parte da equipe e recebeu do responsável pelo projeto o
arquivo **`.env`**, que já vem preenchido. Não é preciso instalar Java, Maven, Python
nem PostgreSQL: **tudo roda dentro do Docker**.

Funciona em Windows, macOS e Linux. Os comandos aparecem primeiro para o Windows
(PowerShell) e, quando mudam, também para macOS/Linux.

Tempo estimado: 15 minutos, mais o download e a compilação na primeira vez.

---

## Índice

1. [O que você precisa ter](#1-o-que-você-precisa-ter)
2. [Baixar o projeto](#2-baixar-o-projeto)
3. [Colocar o `.env` no lugar certo](#3-colocar-o-env-no-lugar-certo)
4. [Subir a aplicação](#4-subir-a-aplicação)
5. [Usar a Aisha](#5-usar-a-aisha)
6. [Comandos do dia a dia](#6-comandos-do-dia-a-dia)
7. [Atualizar para a versão mais nova](#7-atualizar-para-a-versão-mais-nova)
8. [Deu problema?](#8-deu-problema)
9. [Para quem vai mexer no código](#9-para-quem-vai-mexer-no-código)

---

## 1. O que você precisa ter

| O quê | Onde conseguir | Como conferir no terminal |
|---|---|---|
| **Git** | [git-scm.com/downloads](https://git-scm.com/downloads) | `git --version` |
| **Docker Desktop** (Windows/macOS) ou **Docker Engine + Compose** (Linux) | [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/) | `docker compose version` |
| **Acesso ao repositório** no GitHub | Peça ao responsável para adicionar a sua conta na organização `Projeto-Aisha` | — |
| **O arquivo `.env`** | Enviado pelo responsável, em particular | — |

No Windows, aceite a opção **WSL 2** ao instalar o Docker Desktop (pode pedir para
reiniciar).

**Antes de qualquer comando, abra o Docker Desktop e espere o ícone da baleia parar
de animar.** Com ele fechado, nada funciona.

O terminal é o **PowerShell** no Windows e o **Terminal** no macOS/Linux.

---

## 2. Baixar o projeto

Vá até a pasta onde quer guardar o projeto (a Área de Trabalho, por exemplo) e rode:

```bash
git clone https://github.com/Projeto-Aisha/Projeto-Site-IA.git
```

```bash
cd Projeto-Site-IA
```

O Git pode pedir login no GitHub na primeira vez; use a sua conta, a mesma que tem
acesso à organização.

**Todos os comandos deste tutorial rodam dentro da pasta `Projeto-Site-IA`**, a que
contém o arquivo `docker-compose.yml`. Se fechar o terminal, entre nela de novo com
`cd` antes de continuar.

---

## 3. Colocar o `.env` no lugar certo

O `.env` guarda a chave da IA e as senhas locais. Ele **não** vem no GitHub de
propósito: segredo não se versiona.

1. Salve o arquivo que você recebeu **dentro da pasta `Projeto-Site-IA`**, ao lado do
   `docker-compose.yml` e do `.env.example`.
2. O nome tem que ser **exatamente `.env`**: com o ponto na frente e sem nada depois.

### Confira o nome (o erro mais comum)

E-mail, WhatsApp e o Bloco de Notas costumam renomear o arquivo para `env`, `.env.txt`
ou `env.env`, e o Windows esconde a extensão `.txt`. Confira pelo terminal:

```powershell
Get-ChildItem -Force -Filter "*env*"
```

No macOS/Linux:

```bash
ls -la | grep env
```

Devem aparecer **duas** linhas: `.env` e `.env.example`. Se aparecer `.env.txt`,
`env` ou outro nome, corrija:

```powershell
Rename-Item ".env.txt" ".env"
```

No macOS/Linux: `mv .env.txt .env` (troque `.env.txt` pelo nome que apareceu).

### Confira o conteúdo

Este comando lê o `.env` como o Docker vai ler. **Se não imprimir nada, está tudo
certo.** Se faltar alguma variável, ele diz qual:

```bash
docker compose --profile full config --quiet
```

### Três regras

1. **Não edite o `.env` que você recebeu**, a não ser que o tutorial mande (por
   exemplo, para trocar a porta, na seção 8).
2. **Nunca envie o `.env` para o GitHub, grupos ou prints.** Ele já está no
   `.gitignore`, então o Git ignora o arquivo sozinho; não force.
3. A chave do OpenRouter é **compartilhada pela equipe**: cada mensagem para a Aisha
   gasta crédito da mesma conta. Use à vontade para testar, mas não deixe nada
   rodando em loop.

---

## 4. Subir a aplicação

Ainda dentro de `Projeto-Site-IA`:

```bash
docker compose --profile full up -d --build
```

**A primeira vez demora**, de 5 a 15 minutos: o Docker baixa as imagens base e
compila o projeto Java dentro do container. Espere o comando terminar e devolver o
terminal. As próximas vezes levam segundos.

Confira se as três peças subiram:

```bash
docker compose --profile full ps
```

Devem aparecer três linhas, todas com `Up` no status:

```text
aisha-ai-service   Up 30 seconds (healthy)
aisha-app          Up 25 seconds
aisha-postgres     Up 35 seconds (healthy)
```

O `aisha-app` não mostra `(healthy)` aqui, e isso é normal. Ele leva de 10 a 30
segundos para atender depois de ligar, porque cria as tabelas do banco nesse momento
(seção 4.1).

> Só uma porta é aberta no seu computador: a **8080**, a do site. O banco e o serviço
> de IA ficam acessíveis apenas entre os containers.

### 4.1 O banco de dados da sua máquina

Cada pessoa tem **o seu próprio PostgreSQL**, que roda num container no seu
computador. Ele não é compartilhado com ninguém, nem com o site `aisha.com.br`.

Você não precisa instalar nem configurar nada. Na primeira subida, o Docker faz tudo
sozinho:

1. **Cria o banco** `aisha`, com o usuário `aisha` e a senha `POSTGRES_PASSWORD` que
   estão no seu `.env`.
2. **Cria as tabelas.** Ao ligar, o app Java roda as migrações do Flyway, que ficam em
   `LoginProject/mini-login-json/src/main/resources/db/migration`. Quando alguém da
   equipe acrescentar uma migração nova, ela é aplicada sozinha no próximo
   `up -d --build`.
3. **Guarda os dados** no volume `aisha-pgdata`, que sobrevive a desligar a aplicação e
   a reiniciar o computador.

Para conferir se o banco foi criado, rode:

```bash
docker compose exec postgres psql -U aisha -d aisha -c "\dt"
```

Devem aparecer seis tabelas: `conversation_summaries`, `conversations`,
`flyway_schema_history`, `login_attempts`, `messages` e `users`.

Para ver quais migrações já foram aplicadas, rode:

```bash
docker compose exec postgres psql -U aisha -d aisha -c "select version, description, success from flyway_schema_history order by installed_rank"
```

Para abrir um terminal SQL dentro do banco, rode o comando abaixo. Para sair, digite
`\q`.

```bash
docker compose exec postgres psql -U aisha -d aisha
```

#### Abrir o banco no DBeaver, pgAdmin ou na IDE

Por segurança, o banco não fica acessível de fora do Docker. Para conectar uma
ferramenta gráfica, suba a aplicação com as portas de depuração abertas:

```bash
docker compose -f docker-compose.yml -f docker-compose.tools.yml --profile full up -d
```

Depois crie uma conexão PostgreSQL com estes dados:

| Campo | Valor |
|---|---|
| Host | `localhost` |
| Porta | a de `POSTGRES_PORT` no `.env` (normalmente `5432`) |
| Banco | `aisha` |
| Usuário | `aisha` |
| Senha | a de `POSTGRES_PASSWORD` no `.env` |

Se você já tem um PostgreSQL instalado na máquina usando a porta 5432, troque para
`POSTGRES_PORT=5433` no `.env` antes de subir e use `5433` na conexão.

**Não crie nem altere tabelas pela ferramenta gráfica.** Quem cuida da estrutura do
banco são as migrações do Flyway. Uma tabela criada à mão some quando o banco for
recriado e não chega à máquina dos colegas. Mudança de estrutura vira um arquivo
`V5__descricao.sql` novo na pasta de migrações.

---

## 5. Usar a Aisha

Abra o navegador em **<http://localhost:8080>**.

1. Na tela de login, clique em **Criar conta grátis**.
2. Cadastre nome, e-mail e senha (mínimo de 6 caracteres). O e-mail não precisa ser
   real: nada é enviado para ele.
3. Entre e converse com a Aisha.

Bom saber:

- **O banco é só seu.** As contas e conversas ficam no seu computador; quem criou
  conta na máquina de um colega ou no site `aisha.com.br` precisa criar de novo aqui.
- **As conversas ficam salvas** entre reinícios. A barra lateral lista as anteriores.
- **"＋ Novo chat"** começa uma conversa sem o contexto da anterior.
- **5 senhas erradas bloqueiam aquele e-mail por 15 minutos.** É proteção contra
  força bruta; espere ou crie outra conta.

---

## 6. Comandos do dia a dia

Todos rodam dentro da pasta `Projeto-Site-IA`.

| O que você quer | Comando |
|---|---|
| Ligar | `docker compose --profile full up -d` |
| Desligar, **guardando** as conversas | `docker compose --profile full down` |
| Ver se está no ar | `docker compose --profile full ps` |
| Ver os logs ao vivo | `docker compose --profile full logs -f` |
| Ver só os logs da IA | `docker compose --profile full logs --tail=50 ai-service` |
| Reiniciar | `docker compose --profile full restart` |
| Apagar **tudo**, inclusive contas e conversas | `docker compose --profile full down -v` |

Nos logs, `Ctrl+C` só fecha a visualização; a aplicação continua ligada.

Os dados ficam no volume do Docker `aisha-pgdata` e sobrevivem a desligar a aplicação
e a reiniciar o computador. **Só o último comando da tabela apaga**, sem desfazer.

No macOS/Linux, quem tem `make` pode usar os atalhos `make up-full`, `make down`,
`make ps`, `make logs` e `make clean` (rode `make` para ver a lista). No Windows use
os comandos da tabela.

---

## 7. Atualizar para a versão mais nova

```bash
git pull
```

```bash
docker compose --profile full up -d --build
```

Sem o `--build`, você continua rodando a versão antiga. Contas e conversas são
preservadas; mudanças no banco são aplicadas sozinhas na subida.

Se o responsável avisar que o `.env` mudou, substitua o seu pelo novo e rode
`docker compose --profile full up -d` de novo. Alterar o `.env` não afeta o que já
está rodando até os containers serem recriados.

---

## 8. Deu problema?

### `docker: command not found` ou `the docker daemon is not running`

O Docker Desktop está fechado ou não foi instalado. Abra-o, espere a baleia parar de
animar e tente de novo. No Windows, se ele reclamar de WSL, aceite a instalação que
ele oferece e reinicie.

### `required variable ... is missing a value` ou `env file ... not found`

O Docker não achou o `.env`, ou ele está incompleto. Volte à seção 3 e confira o
**nome** e o **local** do arquivo. Se o nome estiver certo, peça o `.env` de novo ao
responsável; ele pode ter sido cortado no envio.

### `Bind for 0.0.0.0:8080 failed: port is already allocated`

Outro programa já usa a porta 8080. Duas saídas:

**a) É uma versão antiga da Aisha** (containers como `mini-login`, `aisha-ai` ou
`aisha-mysql`, da arquitetura anterior). Veja o que está rodando:

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

Pare os antigos pelo nome (exemplo com os três):

```bash
docker stop mini-login aisha-ai aisha-mysql
```

**b) É outro programa que você quer manter.** Abra o `.env`, troque a linha
`APP_PORT=8080` por `APP_PORT=8081` e suba de novo. O site passa a abrir em
<http://localhost:8081>.

### O `aisha-app` fica reiniciando e o log fala em `password authentication failed`

Você já tinha rodado a Aisha antes com **outro** `.env`. A senha do banco só é
gravada quando o banco é criado, então o banco antigo espera a senha antiga, e o
`.env` novo traz outra. Duas saídas:

- Se as contas e conversas locais não importam (o normal em teste), apague o banco
  local e suba limpo:

  ```bash
  docker compose --profile full down -v
  ```

  ```bash
  docker compose --profile full up -d --build
  ```

- Se quiser manter os dados, volte a usar o `.env` antigo.

### O site abre, mas a Aisha responde "Não consegui responder agora"

O site está no ar e o serviço de IA não conseguiu responder. Veja o motivo nos logs:

```bash
docker compose --profile full logs --tail=50 ai-service
```

- `401` ou `invalid api key`: a chave do `.env` está errada ou foi revogada. Peça o
  `.env` atualizado ao responsável.
- `402` ou `insufficient credits`: o crédito da conta do OpenRouter acabou. Avise o
  responsável.
- Depois de trocar o `.env`, recrie os containers:
  `docker compose --profile full up -d`.

### "Sua sessão expirou. Recarregue a página"

A página ficou mais de 30 minutos sem uso. Recarregue com F5 e entre de novo.

### Esqueci a senha da minha conta local

Ainda não existe recuperação de senha. Crie outra conta com outro e-mail, ou comece
do zero com `docker compose --profile full down -v`.

### Quero começar do zero

```bash
docker compose --profile full down -v
```

```bash
docker compose --profile full up -d --build
```

Apaga o banco local inteiro (contas, conversas e memória) e sobe tudo limpo.

---

## 9. Para quem vai mexer no código

Para desenvolver com debug e hot reload, rode o Spring Boot pela IDE e deixe só o
banco e o serviço de IA em containers. Nesse modo você precisa do **JDK 25**
instalado (o Maven vem no projeto, via `mvnw`).

Se a Aisha completa estiver no ar, desligue antes, para o Spring da IDE poder usar a
porta 8080:

```bash
docker compose --profile full down
```

### Passo 1 — subir banco e IA com as portas abertas para a sua máquina

```bash
docker compose -f docker-compose.yml -f docker-compose.tools.yml up -d
```

Isso publica o Postgres em `127.0.0.1:<POSTGRES_PORT do .env>` e o serviço de IA em
`127.0.0.1:5000`. Se você tem um PostgreSQL instalado na máquina usando a 5432, troque
`POSTGRES_PORT=5432` por `POSTGRES_PORT=5433` no `.env` antes deste passo.

### Passo 2 — carregar o `.env` no terminal e rodar o Spring

O Spring não lê o arquivo `.env` sozinho: as variáveis precisam estar no terminal
que o inicia.

**Windows (PowerShell)**, na pasta `Projeto-Site-IA`:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object { $nome, $valor = $_ -split '=', 2; Set-Item -Path "env:$nome" -Value $valor }
```

```powershell
cd LoginProject\mini-login-json
```

```powershell
.\mvnw.cmd spring-boot:run
```

**macOS/Linux**, na pasta `Projeto-Site-IA`:

```bash
set -a; source .env; set +a
```

```bash
cd LoginProject/mini-login-json && ./mvnw spring-boot:run
```

O site abre em <http://localhost:8080>. As variáveis valem só para aquele terminal:
num terminal novo, carregue o `.env` de novo.

Na IDE (IntelliJ, VS Code), em vez do terminal, cadastre as variáveis do `.env` na
configuração de execução da classe `MiniLoginJsonApplication`.

### Testes

Os testes sobem um PostgreSQL descartável via Testcontainers e exigem o Docker no ar:

```powershell
cd LoginProject\mini-login-json
```

```powershell
.\mvnw.cmd test
```

No macOS/Linux: `make test`.

A arquitetura, as variáveis de ambiente e as decisões de projeto estão no
[README.md](README.md). O ambiente de produção (servidor e domínio) está em
[deploy/README.md](deploy/README.md) e não é necessário para desenvolver.
