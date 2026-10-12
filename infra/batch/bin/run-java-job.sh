#!/usr/bin/env bash
# Runs one Spring Batch job (backend/batch) in a run-once container and exits with the job's exit code
# (0 = COMPLETED, 5 = FAILED, 1 = could not start — e.g. that job instance already completed). README "Batch".
#
# Usage: run-java-job.sh <jobBeanName> [key=value ...]
#   run-java-job.sh exampleJob
#   run-java-job.sh monthlySummaryJob yearMonth=2026-09
#
# --build rebuilds the batch image first when backend/ changed (BuildKit cache: a no-op otherwise), so a job
# always runs the code that is checked out on the VPS.

if [[ $# -lt 1 ]]; then
  echo "Usage: $(basename "$0") <jobBeanName> [key=value ...]" >&2
  exit 2
fi
JOB="$1"
shift
TASK_NAME="java-$JOB"   # one lock and log name per job: different jobs may run at the same time

source "$(dirname "$0")/../lib/common.sh"
load_env
acquire_lock
require_cmd docker

COMPOSE=(docker compose -f "$INFRA_DIR/docker-compose.prod.yml" --env-file "$INFRA_DIR/.env.prod")

log "start job=$JOB params=$*"
started=$(date +%s)
"${COMPOSE[@]}" run --rm --build batch "--spring.batch.job.name=$JOB" "$@"
log "done job=$JOB in $(( $(date +%s) - started ))s"
