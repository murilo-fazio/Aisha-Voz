#!/usr/bin/env bash
# ==============================================================================
# Atualiza a Aisha no servidor para as imagens mais recentes.
#
#     ./scripts/deploy.sh
#
# Baixa as imagens que o GitHub Actions publicou no GHCR, recria so os
# containers que mudaram e confere se o site responde. Para voltar a uma versao
# anterior: troque AISHA_TAG no .env por uma tag sha-xxxxxxx e rode de novo.
# ==============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

[[ -f .env ]] || { echo "Falta o .env. Rode: cp .env.example .env  e preencha."; exit 1; }

# Pastas dos volumes. Criadas aqui para o Docker nao cria-las como root.
mkdir -p data/postgres backups

# O repositorio e privado, entao as imagens tambem sao. Sem login, o pull
# falha com 'denied' — melhor avisar com a instrucao certa.
if ! grep -q '"ghcr.io"' "${HOME}/.docker/config.json" 2>/dev/null; then
    echo "Docker sem login no ghcr.io. Rode uma vez (veja deploy/README.md):"
    echo "    echo SEU_TOKEN | docker login ghcr.io -u SEU_USUARIO_GITHUB --password-stdin"
    exit 1
fi

# Backup antes de qualquer imagem nova: se ela trouxer uma migracao que de
# errado, ha de onde voltar. Na primeira subida o banco ainda nao existe.
if docker compose ps --status running --services 2>/dev/null | grep -qx backup; then
    echo "==> Backup antes do deploy"
    docker compose exec -T backup sh /scripts/backup.sh
fi

echo "==> Baixando imagens"
docker compose pull

echo "==> Subindo servicos"
docker compose up -d --remove-orphans

# O Spring leva alguns segundos (Flyway roda no boot). Pergunta pelo nginx,
# que e exatamente o caminho que o publico usa.
echo "==> Aguardando o site responder"
for i in $(seq 1 30); do
    if docker compose exec -T nginx wget -q -O /dev/null http://127.0.0.1/login 2>/dev/null; then
        echo "Site no ar."
        docker image prune -f >/dev/null
        docker compose ps
        exit 0
    fi
    sleep 4
done

echo "O site nao respondeu em 2 minutos. Ultimos logs do app:"
docker compose logs --tail 60 app
exit 1
