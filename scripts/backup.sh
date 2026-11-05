#!/usr/bin/env bash
# Daily backup of the database and uploads. Keeps the newest 14 successful backups.
# Usage: scripts/backup.sh   (run from anywhere; settings come from .env)
# Backups contain customer data: keep BACKUP_DIR and BACKUP_REMOTE private.
set -euo pipefail
umask 077

cd "$(dirname "$0")/.."
ENV_FILE=${ENV_FILE:-.env}
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${COMPOSE_FILE_PATH:-compose.prod.yaml}")
if [ -n "${COMPOSE_PROJECT:-}" ]; then COMPOSE+=(-p "$COMPOSE_PROJECT"); fi

setting() { grep -E "^$1=" "$ENV_FILE" | tail -n1 | cut -d= -f2- || true; }
DB_NAME=$(setting DB_NAME); DB_NAME=${DB_NAME:-upoharlab}
DB_USER=$(setting DB_USER); DB_USER=${DB_USER:-upoharlab}
BACKUP_DIR=${BACKUP_DIR:-$(setting BACKUP_DIR)}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/upoharlab}
BACKUP_REMOTE=${BACKUP_REMOTE:-$(setting BACKUP_REMOTE)}
KEEP=${BACKUP_KEEP:-14}

notify_failure() {
  local token chat
  token=$(setting TELEGRAM_BOT_TOKEN); chat=$(setting TELEGRAM_CHAT_ID)
  if [ -n "$token" ] && [ -n "$chat" ]; then
    curl -fsS -m 10 "https://api.telegram.org/bot${token}/sendMessage" \
      --data-urlencode "chat_id=${chat}" \
      --data-urlencode "text=Upohar Lab backup failed: $1" >/dev/null || true
  fi
}

PAUSED=false
WORK=""
STEP="making the backup"
finish() {
  local code=$?
  if [ "$PAUSED" = true ]; then "${COMPOSE[@]}" unpause app >/dev/null 2>&1 || true; fi
  case "$WORK" in
    "$BACKUP_DIR"/.incomplete-*) rm -rf -- "$WORK" ;;
  esac
  if [ "$code" -ne 0 ]; then
    echo "BACKUP FAILED while $STEP. Earlier backups were not changed." >&2
    notify_failure "failed while $STEP. See the server's backup log."
  fi
  exit "$code"
}
trap finish EXIT

mkdir -p "$BACKUP_DIR"
STAMP=$(date -u +%Y-%m-%dT%H%M%SZ)
WORK="$BACKUP_DIR/.incomplete-$STAMP"
mkdir "$WORK"

# Pausing briefly means the database and photos describe the same moment.
"${COMPOSE[@]}" pause app >/dev/null
PAUSED=true
"${COMPOSE[@]}" exec -T postgres pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$WORK/database.dump"
"${COMPOSE[@]}" run --rm --no-deps -T --entrypoint tar app -C /data/uploads -cf - . \
  | gzip > "$WORK/uploads.tar.gz"
"${COMPOSE[@]}" unpause app >/dev/null
PAUSED=false

# A backup only counts once it has been read back.
# Plain grep reads all the output; grep -q would stop early and could fail the pipeline.
STEP="checking the backup"
"${COMPOSE[@]}" exec -T postgres pg_restore --list < "$WORK/database.dump" \
  | grep 'TABLE DATA public orders' >/dev/null
gzip -t "$WORK/uploads.tar.gz"
(cd "$WORK" && shasum -a 256 database.dump uploads.tar.gz > SHA256SUMS 2>/dev/null \
  || sha256sum database.dump uploads.tar.gz > SHA256SUMS)

FINAL="$BACKUP_DIR/$STAMP"
mv "$WORK" "$FINAL"
WORK=""
echo "Backup saved: $FINAL"

# Only finished backups are counted, so a failed run can never push out a good one.
ls -1 "$BACKUP_DIR" | grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{6}Z$' | sort -r | tail -n +"$((KEEP + 1))" \
  | while read -r old; do rm -rf -- "${BACKUP_DIR:?}/$old"; done

# Copy only this backup, never mirror the folder. A mirror from a rebuilt server with an empty
# backup folder would delete every off-server backup, just when they are needed most.
if [ -n "$BACKUP_REMOTE" ]; then
  STEP="copying off-server. The backup in $FINAL is saved"
  rclone copy "$FINAL" "$BACKUP_REMOTE/$STAMP"
  rclone check "$FINAL" "$BACKUP_REMOTE/$STAMP" --one-way
  echo "Copied off-server: $BACKUP_REMOTE/$STAMP"
  # Only runs after this backup is safely copied, and only touches finished backup folders.
  STEP="removing old off-server backups. The new backup is saved in both places"
  REMOTE_BACKUPS=$(rclone lsf --dirs-only "$BACKUP_REMOTE")
  printf '%s\n' "$REMOTE_BACKUPS" | sed 's:/$::' \
    | { grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{6}Z$' || true; } | sort -r | tail -n +"$((KEEP + 1))" \
    | while read -r old; do rclone purge "$BACKUP_REMOTE/$old"; done
else
  echo "WARNING: BACKUP_REMOTE is not set, so this backup exists only on this server." >&2
fi
