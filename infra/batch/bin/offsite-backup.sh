#!/usr/bin/env bash
# Copies the newest local MySQL dump (infra/backup-mysql.sh → $BACKUP_DIR/fem-YYYYMMDD-HHMMSS.sql.gz) to S3,
# encrypted on the VPS by the rclone remote s3crypt (infra/BACKUP-S3.md), under <remote>/YYYY/MM/ with a
# .sha256 next to it. Runs after the local backup (cron 03:30). README "Batch".
#
# Usage: offsite-backup.sh

source "$(dirname "$0")/../lib/common.sh"
load_env
acquire_lock
require_cmd rclone gzip sha256sum find stat

BACKUP_DIR="${BACKUP_DIR:-/var/backups/fem}"
REMOTE="${OFFSITE_REMOTE:-s3crypt:mysql}"
MAX_AGE_MINUTES="${OFFSITE_MAX_AGE_MINUTES:-120}"

log "start"

# 1. Newest dump. No dump at all means the local backup never ran — that is an error, not "nothing to do".
latest=$(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'fem-*.sql.gz' -printf '%T@ %p\n' \
  | sort -n | tail -1 | cut -d' ' -f2-)
[[ -n "$latest" ]] || die "no fem-*.sql.gz in $BACKUP_DIR"
name=$(basename "$latest")

# 2. It must be tonight's dump, non-empty and a valid gzip — never ship an old or broken file as "the backup".
age_minutes=$(( ($(date +%s) - $(stat -c %Y "$latest")) / 60 ))
(( age_minutes <= MAX_AGE_MINUTES )) \
  || die "newest dump $name is ${age_minutes} min old (limit ${MAX_AGE_MINUTES}) — did backup-mysql.sh fail?"
[[ -s "$latest" ]] || die "$name is empty"
gzip -t "$latest" || die "$name is not a valid gzip file"

# 3. Checksum, uploaded next to the dump so a restore can verify the file it downloaded.
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
(cd "$BACKUP_DIR" && sha256sum "$name") > "$work_dir/$name.sha256"

# 4. Destination by the dump's own date (fem-YYYYMMDD-…), not today's: s3crypt:mysql/2026/10/
stamp=${name#fem-}
dest="$REMOTE/${stamp:0:4}/${stamp:4:2}"

# copy, never sync: backup-mysql.sh prunes local dumps after 14 days and sync would delete them on S3 too.
# --immutable: never overwrite an object that already exists with different content.
rclone copy "$latest" "$dest" --immutable
rclone copy "$work_dir/$name.sha256" "$dest" --immutable

# 5. Verify: crypt remotes have no plain hashes, so cryptcheck re-encrypts the local file and compares.
rclone cryptcheck "$BACKUP_DIR" "$dest" --one-way --include "$name"

# 6. Done: heartbeat, so a cron that silently stopped running gets noticed too.
size=$(du -h "$latest" | cut -f1)
log "done: $name ($size) → $dest"
if [[ -n "${HEALTHCHECK_URL:-}" ]]; then
  curl -fsS -m 10 -o /dev/null "$HEALTHCHECK_URL" || log "WARN: heartbeat ping failed"
fi
