#!/usr/bin/env bash
# Nightly MySQL backup for the VPS (see infra/DEPLOY.md). Dumps the 3 databases from the running
# fem-mysql-db container into BACKUP_DIR as one gzip per run, and keeps the last KEEP_DAYS days.
# Cron (as root, 03:00):  0 3 * * * /opt/family-expense-manager/infra/backup-mysql.sh >> /var/log/fem-backup.log 2>&1
# Copy the backups OFF the VPS too (rclone/rsync to another machine) — a backup on the same disk dies with it.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env.prod}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/fem}"
KEEP_DAYS="${KEEP_DAYS:-14}"

MYSQL_ROOT_PASSWORD="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/fem-$(date +%Y%m%d-%H%M%S).sql.gz"

# --single-transaction: a consistent snapshot of InnoDB tables without locking the app out.
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" fem-mysql-db \
  mysqldump -uroot --single-transaction --routines --triggers --default-character-set=utf8mb4 \
  --databases fem_auth fem_expense fem_notify \
  | gzip > "$FILE"

find "$BACKUP_DIR" -name 'fem-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') backup OK: $FILE ($(du -h "$FILE" | cut -f1))"
