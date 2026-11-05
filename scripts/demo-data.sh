#!/usr/bin/env bash
# Adds or removes clearly labelled test data in the LOCAL development database.
# Usage: scripts/demo-data.sh add | remove
# It only uses compose.yaml, so it cannot reach the production stack in compose.prod.yaml.
set -euo pipefail
cd "$(dirname "$0")/.."

ACTION=${1:-}
COMPOSE=(docker compose -f compose.yaml)
psql_in() {
  "${COMPOSE[@]}" exec -T postgres sh -c \
    'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$1"
}

case "$ACTION" in
  add)
    for photo in scripts/demo/photos/*.jpg; do
      "${COMPOSE[@]}" cp "$photo" "app:/data/uploads/$(basename "$photo")" >/dev/null 2>&1
    done
    psql_in scripts/demo/seed.sql
    echo
    echo "Test data added. Shop: http://localhost:8080  Admin: http://localhost:8080/admin"
    ;;
  remove)
    psql_in scripts/demo/remove.sql
    "${COMPOSE[@]}" exec -T app sh -c \
      'find /data/uploads -type f -name "*d0000000-0000-4000-8000-*.jpg" -delete'
    echo "Test data removed."
    ;;
  *)
    echo "Usage: scripts/demo-data.sh add | remove" >&2
    exit 1
    ;;
esac
