#!/bin/sh
# ==============================================================================
# Backup do banco da Aisha.
#
# Roda DENTRO do container 'backup' (o crond chama todo dia). Para um backup na
# hora, do servidor:
#     docker compose exec backup sh /scripts/backup.sh
#
# Formato custom do pg_dump (-Fc): ja vem comprimido e o pg_restore consegue
# restaurar com --clean, apagando o que existe antes. Um .sql puro exigiria
# derrubar o banco na mao.
#
# Este backup mora no MESMO disco do banco: protege contra erro humano e
# migracao ruim, nao contra o disco morrer. Copie ./backups para fora de vez em
# quando.
# ==============================================================================
set -eu

DESTINO=/backups
ARQUIVO="$DESTINO/aisha-$(date +%Y%m%d-%H%M%S).dump"
MANTER_DIAS="${BACKUP_KEEP_DAYS:-14}"

# Grava com nome temporario e so renomeia no fim: um dump interrompido nunca
# fica com cara de backup valido.
pg_dump -Fc --no-owner -f "$ARQUIVO.parcial"
mv "$ARQUIVO.parcial" "$ARQUIVO"

echo "[backup] $(date '+%F %T') gravado $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1))"

# Rotacao. -mtime +N = modificado ha mais de N dias.
find "$DESTINO" -name 'aisha-*.dump' -mtime +"$MANTER_DIAS" -print -delete \
    | sed 's/^/[backup] removido /'
# Restos de dumps que falharam no meio.
find "$DESTINO" -name '*.parcial' -mmin +60 -delete
