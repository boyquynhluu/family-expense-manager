#!/usr/bin/env bash
# Gets the FIRST Let's Encrypt certificate for $DOMAIN (see infra/DEPLOY.md, bước 5). Run once, before the
# first `up`: nginx's HTTPS block refuses to start without a certificate, so this uses certbot's own
# standalone server on port 80 instead. Renewals afterwards are automatic (certbot container + webroot).
# Usage:  ./init-letsencrypt.sh            (real certificate)
#         STAGING=1 ./init-letsencrypt.sh  (Let's Encrypt staging — for trying things out without hitting rate limits)
#         FORCE=1 ./init-letsencrypt.sh    (replace an existing certificate, e.g. the staging one by a real one)
set -euo pipefail

cd "$(dirname "$0")"
ENV_FILE=.env.prod
COMPOSE="docker compose -f docker-compose.prod.yml --env-file $ENV_FILE"

DOMAIN="$(grep -E '^DOMAIN=' "$ENV_FILE" | cut -d= -f2-)"
EMAIL="$(grep -E '^LETSENCRYPT_EMAIL=' "$ENV_FILE" | cut -d= -f2-)"
if [ -z "$DOMAIN" ] || [ -z "$EMAIL" ]; then
  echo "Thiếu DOMAIN hoặc LETSENCRYPT_EMAIL trong $ENV_FILE" >&2
  exit 1
fi

# Port 80 must be free for certbot --standalone.
$COMPOSE stop nginx >/dev/null 2>&1 || true

STAGING_ARG=""
if [ "${STAGING:-0}" = "1" ]; then
  STAGING_ARG="--staging"
  echo "Dùng Let's Encrypt STAGING (chứng chỉ thử, trình duyệt sẽ báo không tin cậy)."
fi

FORCE_ARG=""
if [ "${FORCE:-0}" = "1" ]; then
  FORCE_ARG="--force-renewal"
fi

echo "Xin chứng chỉ cho $DOMAIN ..."
$COMPOSE run --rm -p 80:80 --entrypoint certbot certbot certonly \
  --standalone --non-interactive --agree-tos --no-eff-email \
  --email "$EMAIL" -d "$DOMAIN" $STAGING_ARG $FORCE_ARG

echo "Xong. Chạy tiếp:  $COMPOSE up -d --build"
