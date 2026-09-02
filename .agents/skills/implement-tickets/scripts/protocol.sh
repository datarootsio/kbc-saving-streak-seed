#!/usr/bin/env bash
#
# Reusable mechanical steps for the implement-tickets protocol.
# FEATURE_DIR must point to the feature directory containing issues/.
#
#   tickets [--resume] [--from NN] [--only NN]     list ticket paths in order
#   send-back <ticket> <attempt> <heading> <detail-file>
#                                                  append feedback and move to issues/

set -euo pipefail

SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"

cmd_tickets() {
    local resume=0 from="" only="" num
    : "${FEATURE_DIR:?set FEATURE_DIR to the directory holding issues/}"
    local ISSUES_DIR="$FEATURE_DIR/issues" REVIEW_DIR="$FEATURE_DIR/to-review"
    cd "$(git rev-parse --show-toplevel)"
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --resume) resume=1; shift ;;
            --from) from="$2"; shift 2 ;;
            --only) only="$2"; shift 2 ;;
            *) echo "tickets: unknown option $1" >&2; exit 2 ;;
        esac
    done
    [[ -d "$ISSUES_DIR" ]] || { echo "no issues directory at $ISSUES_DIR" >&2; exit 1; }
    {
        find "$ISSUES_DIR" -maxdepth 1 -name '[0-9][0-9]-*.md'
        if [[ $resume -eq 1 && -d "$REVIEW_DIR" ]]; then
            find "$REVIEW_DIR" -maxdepth 1 -name '[0-9][0-9]-*.md'
        fi
    } | awk -F/ '!seen[$NF]++ { print $NF "\t" $0 }' | sort | cut -f2- \
      | while IFS= read -r ticket; do
            num="$(basename "$ticket" | cut -d- -f1)"
            [[ -n "$only" && "$num" != "$only" ]] && continue
            [[ -n "$from" && "$num" < "$from" ]] && continue
            echo "$ticket"
        done
}

cmd_send_back() {
    local ticket="${1:?send-back: ticket path}" attempt="${2:?attempt}"
    local heading="${3:?heading}" detail_file="${4:?detail file}"
    local feature issues
    feature="$(cd "$(dirname "$ticket")/.." && pwd)"
    issues="$feature/issues"
    {
        echo
        echo "## Review feedback - attempt $attempt ($heading)"
        echo
        cat "$detail_file"
    } >> "$ticket"
    mkdir -p "$issues"
    git -C "$feature" mv "$ticket" "$issues/" 2>/dev/null || mv "$ticket" "$issues/"
    echo "$issues/$(basename "$ticket")"
}

sub="${1:-}"
shift || true
case "$sub" in
    tickets) cmd_tickets "$@" ;;
    send-back) cmd_send_back "$@" ;;
    *) sed -n '2,8p' "$SELF" >&2; exit 2 ;;
esac
