# Aisha em produção (servidor Umbrel)

Esta pasta é o ambiente de produção. Ela é copiada inteira para
`/home/umbrel/aisha` no servidor. Para rodar o projeto no seu computador, use o
[Tutorial.md](../Tutorial.md); nada aqui é necessário para desenvolver.

```text
navegador ──HTTPS──▶ Cloudflare ──túnel──▶ cloudflared ─▶ nginx ─▶ app (Spring) ─▶ ai-service
                                            (tudo dentro do servidor,      │            │
                                             nenhuma porta aberta)         └── postgres ┘
```

| Arquivo | Para que serve |
|---|---|
| `docker-compose.yml` | Os 6 serviços: postgres, ai-service, app, nginx, cloudflared, backup |
| `.env.example` | Modelo das variáveis. No servidor vira `.env`, com os valores reais |
| `nginx/conf.d/aisha.conf` | Limite de requisições, cabeçalhos de segurança, proxy para o app |
| `scripts/deploy.sh` | Atualiza para as imagens mais novas (faz backup antes) |
| `scripts/backup.sh` | Backup do banco (o container `backup` roda todo dia) |
| `scripts/restore.sh` | Restaura um backup |

Pastas criadas no servidor e **fora do Git**: `.env`, `data/postgres/` (dados do
banco) e `backups/`.

**Por que `/home/umbrel` e não `/opt`:** as atualizações do umbrelOS substituem
a partição do sistema; só a área de dados, em `/home/umbrel`, permanece.

---

## Instalação (uma vez)

### 1. Domínio: GoDaddy → Cloudflare

O domínio é comprado na GoDaddy, mas o DNS passa para a Cloudflare (plano Free),
que fornece o túnel e o HTTPS.

1. Crie uma conta em <https://dash.cloudflare.com> e clique em **Add a domain**.
   Informe o domínio e escolha o plano **Free**.
2. A Cloudflare mostra **dois nameservers** (ex.: `ana.ns.cloudflare.com`).
3. Na GoDaddy, abra **Meus Produtos → domínio → DNS → Servidores de nomes →
   Alterar → "Usarei meus próprios servidores de nomes"**. Cole os dois
   nameservers da Cloudflare e salve.
4. Aguarde a Cloudflare marcar o domínio como **Active**. Pode levar de minutos a
   algumas horas.
5. Em **SSL/TLS → Edge Certificates**, ligue **Always Use HTTPS**.

Se a Cloudflare não ativar: desligue o **DNSSEC** na GoDaddy (ele aponta para os
nameservers antigos). E se a GoDaddy não deixar trocar os nameservers de um
`.com.br`, a troca é feita no painel do **Registro.br**.

### 2. Túnel na Cloudflare

1. Na Cloudflare, abra **Zero Trust → Networks → Tunnels → Create a tunnel**.
2. Escolha o tipo **Cloudflared** e dê o nome `aisha`.
3. Na tela de instalação, copie **só o token**: o texto longo que vem depois de
   `--token`. Ele vai no `.env` como `CLOUDFLARE_TUNNEL_TOKEN`. Não precisa
   instalar nada; o container já faz isso.
4. Em **Public Hostname**, crie **duas** entradas, ambas com **Type** `HTTP` e
   **URL** `nginx:80`:
   - **Subdomain** vazio, **Domain** `aisha.com.br`
   - **Subdomain** `www`, **Domain** `aisha.com.br` (o nginx redireciona
     `www.aisha.com.br` para `aisha.com.br`)

### 3. Token para baixar as imagens

O repositório é privado, então as imagens no GHCR também são. O servidor precisa
de um token só de leitura:

1. No GitHub, abra **Settings → Developer settings → Personal access tokens →
   Tokens (classic) → Generate new token**.
2. Marque apenas o escopo **`read:packages`**.
3. Guarde o token; ele é usado no passo 5.

As imagens só existem depois que o workflow **Publicar imagens** roda pela
primeira vez, o que acontece no merge desta pasta na `main`. Confira em
**Actions** no GitHub.

### 4. Copiar os arquivos para o Umbrel

Do seu computador, na raiz do repositório:

```bash
scp -r deploy umbrel@umbrel.local:/home/umbrel/aisha
```

A senha do SSH é a mesma do painel do Umbrel. Se `umbrel.local` não resolver,
use o IP do servidor.

Antes de seguir, confira duas coisas no servidor (`ssh umbrel@umbrel.local`):

```bash
uname -m                 # precisa ser x86_64: as imagens são só linux/amd64
docker compose version   # se pedir permissão, use sudo nos comandos daqui em diante
```

Se `uname -m` mostrar `aarch64` (Raspberry Pi), acrescente `linux/arm64` em
`platforms` no `.github/workflows/publish.yml` antes do primeiro deploy.

### 5. Configurar e subir

```bash
ssh umbrel@umbrel.local
cd ~/aisha

cp .env.example .env
chmod 600 .env
nano .env          # preencha AISHA_DOMAIN, senhas, chave do OpenRouter, token do túnel

# Gerar as senhas do .env:
openssl rand -hex 24   # POSTGRES_PASSWORD
openssl rand -hex 32   # AI_INTERNAL_TOKEN

# Login no GHCR (uma vez só; o Docker guarda a credencial)
echo SEU_TOKEN | docker login ghcr.io -u SEU_USUARIO_GITHUB --password-stdin

./scripts/deploy.sh
```

Abra `https://seu-dominio` no celular. Se a página de login aparecer com o
cadeado, terminou.

### 6. Antes de divulgar o endereço

- [ ] **Limite de crédito na chave do OpenRouter.** Qualquer pessoa pode se
      cadastrar e conversar, e cada mensagem custa.
- [ ] Fazer um backup na hora e testar a restauração:
      `docker compose exec backup sh /scripts/backup.sh` e depois
      `./scripts/restore.sh backups/<arquivo>.dump`.
- [ ] Copiar `~/aisha/backups` para fora do servidor de tempos em tempos. Um
      backup no mesmo disco não ajuda se o disco falhar.

---

## Dia a dia

| Quero... | Comando (em `~/aisha`) |
|---|---|
| Publicar a versão nova da `main` | `./scripts/deploy.sh` |
| Ver os logs | `docker compose logs -f app` (ou `ai-service`, `nginx`, `cloudflared`) |
| Ver o estado dos serviços | `docker compose ps` |
| Voltar a uma versão anterior | No `.env`, `AISHA_TAG=sha-xxxxxxx` (tags em GitHub → Packages); depois `./scripts/deploy.sh` |
| Backup agora | `docker compose exec backup sh /scripts/backup.sh` |
| Restaurar | `./scripts/restore.sh backups/<arquivo>.dump` |
| Abrir o psql | `docker compose exec postgres psql -U aisha -d aisha` |
| Atualizar esta pasta (compose, nginx, scripts) | `scp -r deploy/* umbrel@umbrel.local:/home/umbrel/aisha/` e depois `./scripts/deploy.sh` |

O `scp` da última linha não apaga nada: `.env`, `data/` e `backups/` não existem
no repositório, então não são sobrescritos.
