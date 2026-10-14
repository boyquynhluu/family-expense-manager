#!/usr/bin/env bash
# Monthly wallet balance snapshot: runs the Spring Batch job walletMonthlySnapshotJob (backend/batch,
# job/walletsnapshot) for a month that has ended, then exports a TSV report and sends a summary. README "Batch".
#
# Usage: wallet-snapshot.sh [yyyy-MM]      (default: last month — what cron runs on the 1st)
#   wallet-snapshot.sh            # last month
#   wallet-snapshot.sh 2026-08    # catch up / re-try a given month
#
# Exit codes: 0 = snapshot done (or that month was already done), 5 = the job FAILED — run the same command again
# and Spring Batch resumes from the last committed chunk, anything else = could not run.

source "$(dirname "$0")/../lib/common.sh"
load_env
acquire_lock
require_cmd docker date find

ENV_FILE="$INFRA_DIR/.env.prod"
REPORT_DIR="${REPORT_DIR:-/var/log/fem-batch/reports}"
JOB=walletMonthlySnapshotJob

# --- month -------------------------------------------------------------------------------------------------------
# Last month counted from the 1st of this month: `date -d 'last month'` on the 31st gives "31 September" = 1 October.
YM="${1:-$(date -d "$(date +%Y-%m-01) -1 month" +%Y-%m)}"
# Validated before it goes anywhere near SQL below (it is concatenated into the queries).
[[ "$YM" =~ ^[0-9]{4}-(0[1-9]|1[0-2])$ ]] || die "invalid month '$YM' (expected yyyy-MM)"

log "start month=$YM"

# Runs SQL on fem_batch as the fem_batch user. The password goes through MYSQL_PWD — never on the command line,
# where `ps` and the log would show it. Extra arguments go to the mysql client (e.g. -N: no header row).
batch_sql() {
  local sql="$1" pw
  shift
  pw="$(grep -E '^FEM_BATCH_DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
  docker exec -e MYSQL_PWD="$pw" fem-mysql-db \
    mysql -u"${FEM_BATCH_DB_USER:-fem_batch}" -D fem_batch --batch "$@" -e "$sql"
}

# --- already done? -----------------------------------------------------------------------------------------------
# Checked BEFORE launching: a second launch of a COMPLETED month makes the job exit 1, and run-java-job.sh's ERR
# trap would then send a ❌ for something that is not a failure. On the very first run the BATCH_* tables do not
# exist yet (Flyway creates them when the job starts) — the query fails, which simply means "not done".
done_count=$(batch_sql "
  SELECT COUNT(*)
  FROM BATCH_JOB_INSTANCE i
  JOIN BATCH_JOB_EXECUTION e        ON e.JOB_INSTANCE_ID  = i.JOB_INSTANCE_ID
  JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID
  WHERE i.JOB_NAME = '$JOB' AND p.PARAMETER_NAME = 'yearMonth' AND p.PARAMETER_VALUE = '$YM'
    AND e.STATUS = 'COMPLETED'" -N 2>/dev/null || echo 0)
if (( done_count > 0 )); then
  log "month $YM was already snapshotted — nothing to do"
  exit 0
fi

# --- run the job -------------------------------------------------------------------------------------------------
# `|| rc=$?` keeps the exit code without tripping this script's ERR trap — the codes are handled here.
rc=0
bash "$BATCH_HOME/bin/run-java-job.sh" "$JOB" "yearMonth=$YM" || rc=$?
case "$rc" in
  0) ;;
  5)
    # run-java-job.sh has already logged and notified the failure.
    log "job FAILED — fix the cause, then run again: bash $0 $YM (resumes from the last committed chunk)"
    exit 5
    ;;
  *)
    die "job could not start (exit $rc) — see the job output above"
    ;;
esac

# --- report ------------------------------------------------------------------------------------------------------
mkdir -p "$REPORT_DIR"
report="$REPORT_DIR/wallet-snapshot-$YM.tsv"
batch_sql "
  SELECT family_id, wallet_id, wallet_name, currency, initial_balance, income, expense, transfer_in,
         transfer_out, adjustments, loans, closing_balance
  FROM WALLET_MONTHLY_SNAPSHOTS
  WHERE snapshot_month = '$YM'
  ORDER BY family_id, wallet_id" > "$report"

read -r wallets total < <(batch_sql "
  SELECT COUNT(*), COALESCE(SUM(closing_balance), 0)
  FROM WALLET_MONTHLY_SNAPSHOTS
  WHERE snapshot_month = '$YM'" -N)
log "report: $report ($wallets wallets, total closing balance $total)"
notify "✅ fem: wallet snapshot $YM — $wallets wallets, total closing balance $total"

# Reports are small; keep a bit over a year of them.
find "$REPORT_DIR" -name 'wallet-snapshot-*.tsv' -mtime +400 -delete

log "done"
