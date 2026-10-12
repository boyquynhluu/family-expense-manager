# shellcheck shell=bash
# Shared helpers for every script in infra/batch/bin/ (README "Batch"). Usage, at the top of a bin/ script:
#
#   #!/usr/bin/env bash
#   source "$(dirname "$0")/../lib/common.sh"
#   load_env
#   acquire_lock
#   log "start"
#   ...
#
# Gives: strict mode, timestamped log lines, die/require_cmd, the optional conf/batch.env, a per-task flock so a
# task never runs twice at once, and an ERR trap that logs the failing line and sends a notification. Exit codes:
# 0 = OK, anything else = failed (cron and notify rely on it).

set -Eeuo pipefail

BATCH_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"    # infra/batch
APP_DIR="$(cd "$BATCH_HOME/../.." && pwd)"                         # repository root
INFRA_DIR="$APP_DIR/infra"
# Name used in log lines, the lock file and notifications; defaults to the script's file name without .sh.
TASK_NAME="${TASK_NAME:-$(basename "${0%.sh}")}"

log() {
  printf '%s [%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$TASK_NAME" "$*"
}

die() {
  log "ERROR: $*" >&2
  exit 1
}

require_cmd() {
  local cmd
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || die "required command not found: $cmd"
  done
}

# Loads infra/batch/conf/batch.env (not committed; copy batch.env.template) if it exists — every variable in it
# is exported to the script and to the commands it runs.
load_env() {
  local env_file="$BATCH_HOME/conf/batch.env"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
}

# Holds an exclusive flock for the rest of the script (released automatically when it exits); a second run of
# the same task while the first is still going stops at once instead of running on top of it.
acquire_lock() {
  require_cmd flock
  local lock_file="${LOCK_DIR:-/tmp}/fem-batch-${TASK_NAME}.lock"
  exec 9>"$lock_file"
  flock -n 9 || die "already running (lock held: $lock_file)"
}

# shellcheck source=notify.sh
source "$BATCH_HOME/lib/notify.sh"

on_error() {
  local exit_code=$? line=$1
  log "FAILED (exit $exit_code) at line $line"
  notify "❌ fem batch '$TASK_NAME' failed (exit $exit_code) on $(hostname)"
  exit "$exit_code"
}
trap 'on_error $LINENO' ERR
