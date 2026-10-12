# shellcheck shell=bash
# notify "<message>" — sourced by common.sh. Always writes the message to the log; also sends it to Telegram when
# TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID are set (conf/batch.env). Never fails the calling script: a broken
# notification channel must not turn a finished task into a failed one.
#
# Add another channel (email, Slack webhook, ...) here, behind its own variables.

notify() {
  local message="$1"
  log "NOTIFY: $message"
  if [[ -n "${TELEGRAM_BOT_TOKEN:-}" && -n "${TELEGRAM_CHAT_ID:-}" ]]; then
    curl -fsS -m 10 -o /dev/null \
      "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
      --data-urlencode "chat_id=${TELEGRAM_CHAT_ID}" \
      --data-urlencode "text=${message}" \
      || log "WARN: Telegram notification failed"
  fi
}
