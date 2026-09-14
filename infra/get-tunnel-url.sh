#!/usr/bin/env bash
# Prints the current Cloudflare quick-tunnel public URL for the backend (api-gateway).
# The URL is random and changes every time the `cloudflared` container restarts, so
# re-run this after `docker compose up`/`restart cloudflared` and update the frontend's
# VITE_API_BASE_URL (GitHub repo variable) with the result + "/api".
set -euo pipefail

URL=$(docker logs fem-cloudflared 2>&1 | grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' | tail -1)

if [ -z "$URL" ]; then
  echo "Chưa thấy tunnel URL trong log. Đợi vài giây rồi chạy lại, hoặc xem trực tiếp: docker logs fem-cloudflared" >&2
  exit 1
fi

echo "$URL"
