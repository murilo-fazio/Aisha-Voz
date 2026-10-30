#!/usr/bin/env bash
# ==============================================================================
# Restaura um backup do banco da Aisha. Roda no SERVIDOR, na pasta do compose.
#
#     ./scripts/restore.sh                      # lista os backups disponiveis
#     ./scripts/restore.sh backups/aisha-20261027-030000.dump
#
# APAGA o conteudo atual do banco e coloca o do arquivo no lugar.
# ==============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ $# -ne 1 ]]; then
    echo "Uso: $0 backups/<arquivo>.dump"
    echo
    echo "Backups disponiveis (mais recentes por ultimo):"
    ls -1tr backups/*.dump 2>/dev/null || echo "  (nenhum)"
    exit 1
fi

ARQUIVO="$1"
[[ -f "$ARQUIVO" ]] || { echo "Arquivo nao encontrado: $ARQUIVO"; exit 1; }

# shellcheck disable=SC1091
set -a; source .env; set +a
DB="${POSTGRES_DB:-aisha}"
USR="${POSTGRES_USER:-aisha}"

echo "ATENCAO: o banco '$DB' sera SUBSTITUIDO pelo conteudo de $ARQUIVO."
read -r -p "Digite RESTAURAR para continuar: " CONFIRMA
[[ "$CONFIRMA" == "RESTAURAR" ]] || { echo "Cancelado."; exit 1; }

# Quem escreve no banco sai do ar durante a restauracao: o app gravando no
# meio dela deixaria o banco com metade de cada versao.
echo "Parando app e ai-service..."
docker compose stop app ai-service

echo "Restaurando..."
docker compose exec -T postgres \
    pg_restore --clean --if-exists --no-owner -U "$USR" -d "$DB" < "$ARQUIVO"

echo "Subindo app e ai-service..."
docker compose start ai-service app

echo "Restauracao concluida."
