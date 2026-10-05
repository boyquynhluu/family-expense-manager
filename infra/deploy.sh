#!/usr/bin/env bash
# CD deploy on the VPS: pull main, back up MySQL, rebuild + restart the prod stack, then wait for the site.
# Run by .github/workflows/deploy.yml on the self-hosted runner (user `deploy`) — also runnable by hand.
# Never `down -v` here: that would wipe the MySQL and certbot volumes.
set -euo pipefail

# Everything lives in main(), called on the last line: bash then parses the whole script before running any of it,
# so the `git pull` below can safely rewrite this very file mid-run.
main() {
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  APP_DIR="$(dirname "$SCRIPT_DIR")"
  ENV_FILE="$SCRIPT_DIR/.env.prod"
  COMPOSE=(docker compose -f "$SCRIPT_DIR/docker-compose.prod.yml" --env-file "$ENV_FILE")
  DOMAIN="$(grep -E '^DOMAIN=' "$ENV_FILE" | cut -d= -f2-)"

  cd "$APP_DIR"
  git fetch origin main
  git checkout main
  git pull --ff-only origin main
  echo "Deploying $(git log -1 --format='%h %s')"

  # Flyway migrations run on startup — snapshot the DB first (only if MySQL is already up, e.g. not on a fresh VPS).
  # Own dir under $HOME: /var/backups/fem belongs to root's nightly cron.
  if docker ps --format '{{.Names}}' | grep -qx fem-mysql-db; then
    BACKUP_DIR="${PREDEPLOY_BACKUP_DIR:-$HOME/fem-backups}" KEEP_DAYS=7 "$SCRIPT_DIR/backup-mysql.sh"
  fi

  "${COMPOSE[@]}" up -d --build
  docker image prune -f

  # nginx runs a stock image with infra/nginx bind-mounted, so `up` never recreates it when only that config
  # changes — restart it then (templates are rendered at start). The workflow pulls before this script runs, so
  # compare against the config hash of the last deploy instead of the previous commit. Missing file = restart.
  NGINX_HASH="$(git rev-parse HEAD:infra/nginx)"
  NGINX_HASH_FILE="$HOME/.fem-nginx-config-hash"
  if [[ "$(cat "$NGINX_HASH_FILE" 2>/dev/null)" != "$NGINX_HASH" ]]; then
    echo "nginx config changed — restarting nginx"
    "${COMPOSE[@]}" restart nginx
    echo "$NGINX_HASH" > "$NGINX_HASH_FILE"
  fi

  # The JVM services take a while to register with Eureka — poll the site through nginx for up to 5 minutes.
  for _ in $(seq 1 30); do
    if curl -fsS -o /dev/null "https://$DOMAIN/"; then
      echo "Deploy OK: https://$DOMAIN/"
      "${COMPOSE[@]}" ps
      exit 0
    fi
    sleep 10
  done

  echo "Site not healthy 5 minutes after deploy" >&2
  "${COMPOSE[@]}" ps
  exit 1
}

main "$@"
