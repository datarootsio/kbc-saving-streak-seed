#!/usr/bin/env bash
#
# The parts of the implement-tickets protocol that are the same in every repository.
# FEATURE_DIR must be set to the feature directory (the one holding issues/).
#
#   tickets [--resume] [--from NN] [--only NN]     list the ticket paths to run, in order
#   send-back <ticket> <attempt> <heading> <detail-file>
#                                                  append review feedback, move to issues/
#                                                  (works on the ticket's own checkout)

set -euo pipefail

SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"

# Ordered by ticket number, not by path, so a resumed ticket sorts among the rest
# rather than after them. A ticket cannot be in both folders, but dedupe anyway: the
# first path found for a name wins, and issues/ is listed first.
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

# The orchestrator's own verdict when the build is red: no reviewer looked at it, so
# say so in the ticket in the same shape a reviewer would, and put it back in issues/.
# issues/ is found beside the ticket rather than via FEATURE_DIR, so this works on any
# checkout without FEATURE_DIR having been exported.
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

sub="${1:-}"; shift || true
case "$sub" in
    tickets) cmd_tickets "$@" ;;
    send-back) cmd_send_back "$@" ;;
    *) sed -n '2,8p' "$SELF" >&2; exit 2 ;;
esac
