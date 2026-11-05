#!/usr/bin/env bash
# Proves a backup can be recovered, using a separate scratch database and a temporary folder.
# It never touches the production database or uploads.
# Usage: scripts/restore-check.sh /path/to/backup-folder
set -euo pipefail
umask 077

BACKUP=${1:?Usage: scripts/restore-check.sh /path/to/backup-folder}
BACKUP=$(cd "$BACKUP" && pwd)
cd "$(dirname "$0")/.."
ENV_FILE=${ENV_FILE:-.env}
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${COMPOSE_FILE_PATH:-compose.prod.yaml}")
if [ -n "${COMPOSE_PROJECT:-}" ]; then COMPOSE+=(-p "$COMPOSE_PROJECT"); fi

setting() { grep -E "^$1=" "$ENV_FILE" | tail -n1 | cut -d= -f2- || true; }
DB_USER=$(setting DB_USER); DB_USER=${DB_USER:-upoharlab}
SCRATCH_DB="restore_check_$(date +%s)"
SCRATCH_DIR=$(mktemp -d)
psql_in() { "${COMPOSE[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "$DB_USER" "$@"; }

cleanup() {
  psql_in -d postgres -qc "DROP DATABASE IF EXISTS $SCRATCH_DB" >/dev/null 2>&1 || true
  case "$SCRATCH_DIR" in
    /tmp/*|/var/folders/*|/private/*) rm -rf -- "$SCRATCH_DIR" ;;
  esac
}
trap cleanup EXIT

(cd "$BACKUP" && { shasum -a 256 -c SHA256SUMS 2>/dev/null || sha256sum -c SHA256SUMS; }) >/dev/null
echo "Checksums match."

psql_in -d postgres -qc "CREATE DATABASE $SCRATCH_DB" >/dev/null
"${COMPOSE[@]}" exec -T postgres pg_restore --no-owner -U "$DB_USER" -d "$SCRATCH_DB" \
  < "$BACKUP/database.dump"
tar -xzf "$BACKUP/uploads.tar.gz" -C "$SCRATCH_DIR"

ORDERS=$(psql_in -d "$SCRATCH_DB" -Atc "SELECT count(*) FROM orders")
PRODUCTS=$(psql_in -d "$SCRATCH_DB" -Atc "SELECT count(*) FROM product")
PAYMENTS=$(psql_in -d "$SCRATCH_DB" -Atc "SELECT count(*) FROM payment")
LATEST=$(psql_in -d "$SCRATCH_DB" -Atc "SELECT code FROM orders ORDER BY id DESC LIMIT 1")
echo "Restored: $ORDERS orders, $PRODUCTS products, $PAYMENTS payments. Latest order: ${LATEST:-none}."

PHOTOS=0
MISSING=0
while read -r name; do
  [ -z "$name" ] && continue
  PHOTOS=$((PHOTOS + 1))
  if [ ! -s "$SCRATCH_DIR/$name" ] || [ ! -s "$SCRATCH_DIR/thumb-$name" ]; then
    MISSING=$((MISSING + 1))
    echo "Missing photo file: $name" >&2
  fi
done < <(psql_in -d "$SCRATCH_DB" -Atc "SELECT filename FROM product_image")
echo "Photos: $PHOTOS in the database, $MISSING missing from the backup."

if [ "$MISSING" -ne 0 ]; then
  echo "RESTORE CHECK FAILED." >&2
  exit 1
fi
echo "RESTORE CHECK PASSED."
