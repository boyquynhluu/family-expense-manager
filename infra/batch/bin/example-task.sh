#!/usr/bin/env bash
# TEMPLATE — copy to bin/<task-name>.sh for a new shell task (README "Batch" → "Thêm một tác vụ shell"), then
# delete this file. Rules: idempotent (safe to run again), exit non-zero on failure, log what it did.
#
# Usage: example-task.sh

source "$(dirname "$0")/../lib/common.sh"
load_env        # variables from conf/batch.env, e.g. RETENTION_DAYS
acquire_lock    # never two runs of this task at the same time
require_cmd df  # every external command the task needs

log "start"

# --- the task -------------------------------------------------------------------------------------------------
# Example: report how full the root filesystem is. A failing command stops the script (set -e) and the ERR trap
# logs + notifies; use `cmd || true` only where a failure is genuinely acceptable.
usage=$(df --output=pcent / | tail -1 | tr -dc '0-9')
log "root filesystem ${usage}% used"
if (( usage >= ${DISK_WARN_PERCENT:-85} )); then
  notify "⚠️ fem: disk / is ${usage}% full on $(hostname)"
fi
# ---------------------------------------------------------------------------------------------------------------

log "done"
