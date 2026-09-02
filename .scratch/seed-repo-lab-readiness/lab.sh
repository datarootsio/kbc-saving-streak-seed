#!/usr/bin/env bash
#
# The lab for this repository, written to the contract in the implement-tickets skill
# (lab-contract.md). Spring Boot backend on 8080, Vite frontend on 5173.
#
#   prepare              what a fresh worktree lacks: frontend/node_modules
#   checks <log>         mvnw test + npm run typecheck, all output to <log>
#   app-start <prefix>   boot both on a throwaway SQLite database, logs at <prefix>.*.log
#   app-stop             stop whatever listens on 8080 and 5173

set -euo pipefail

BACKEND_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:5173"

# The checkout this copy lives in, not the caller's: the orchestrator runs a worktree's
# copy from the main checkout, and the worktree is where the work must happen.
SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
cd "$(git -C "$(dirname "$SELF")" rev-parse --show-toplevel)"

# Vite needs Node 20.19+; the default node here may be older. Prefer a new enough one
# from nvm over failing every frontend check for a reason unrelated to any ticket.
pick_node() {
    local major candidate
    major="$(node -v 2>/dev/null | sed 's/^v//; s/\..*//')" || major=""
    if [[ -n "$major" && "$major" -ge 20 ]]; then
        return 0
    fi
    candidate="$(ls -d "$HOME"/.nvm/versions/node/v2[0-9]* 2>/dev/null | sort -V | tail -1 || true)"
    if [[ -n "$candidate" ]]; then
        export PATH="$candidate/bin:$PATH"
        echo "using node $(node -v) from nvm"
    else
        echo "warning: node $(node -v 2>/dev/null || echo missing) is too old for Vite" >&2
    fi
}

# A worktree is a fresh checkout: no node_modules. Maven fetches its own dependencies
# into ~/.m2 on first use, so the backend needs nothing here.
cmd_prepare() {
    pick_node
    if [[ -d frontend/node_modules ]]; then
        echo "frontend/node_modules present; nothing to prepare"
        return 0
    fi
    ( cd frontend && npm ci --no-audit --no-fund )
    echo "prepared: frontend dependencies installed"
}

# Not -q. A rejected implementer is handed this log, and Maven's quiet mode drops the
# assertion text and the stack trace, which is the only part worth reading.
cmd_checks() {
    local log="${1:?checks: log path required}"
    mkdir -p "$(dirname "$log")"
    pick_node
    {
        echo "=== backend: ./mvnw test"
        ( cd backend && ./mvnw test )
    } > "$log" 2>&1 || { echo "backend tests failed; see $log" >&2; return 1; }
    {
        echo
        echo "=== frontend: npm run typecheck"
        ( cd frontend && npm run typecheck )
    } >> "$log" 2>&1 || { echo "frontend typecheck failed; see $log" >&2; return 1; }
    echo "checks passed; output in $log"
}

listening_on() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

wait_for() {
    local url="$1" seconds="${2:-180}" n=0
    while ! curl -sf -o /dev/null "$url"; do
        n=$((n + 1))
        [[ $n -ge $seconds ]] && return 1
        sleep 1
    done
}

# The app under review gets its own throwaway database, so a reviewer making deposits
# does not spend the demo data a trainer is about to present with. It is started
# chattier than in normal use: its own package at DEBUG, the web layer at DEBUG for
# request and response lines, and Hibernate's SQL, so a reviewer that sees a wrong
# number or a 500 can answer "why" from the log instead of guessing.
cmd_app_start() {
    local prefix="${1:?app-start: log prefix required}"
    local backend_log="$prefix.backend.log" frontend_log="$prefix.frontend.log" db
    if listening_on 8080 || listening_on 5173; then
        echo "something is already listening on 8080 or 5173; run app-stop first" >&2
        return 1
    fi
    mkdir -p "$(dirname "$prefix")"
    pick_node
    db="$(mktemp -d)/saving-streak.db"
    echo "database:     $db"
    echo "backend log:  $backend_log"
    echo "frontend log: $frontend_log"
    # The redirection sits outside the subshell on purpose. Inside it, after `cd backend`,
    # a log path relative to the repository root does not resolve and the app never starts.
    ( cd backend && SAVING_STREAK_DB="$db" ./mvnw -q spring-boot:run \
        -Dspring-boot.run.arguments="--logging.level.io.dataroots.savingstreak=DEBUG --logging.level.org.springframework.web=DEBUG --logging.level.org.hibernate.SQL=DEBUG" \
        ) > "$backend_log" 2>&1 &
    ( cd frontend && npm run dev ) > "$frontend_log" 2>&1 &
    if ! wait_for "$BACKEND_URL/api/customers" 180; then
        echo "backend did not come up; see $backend_log" >&2
        cmd_app_stop
        return 1
    fi
    if ! wait_for "$FRONTEND_URL/" 60; then
        echo "frontend did not come up; see $frontend_log" >&2
        cmd_app_stop
        return 1
    fi
    echo "app is up: API $BACKEND_URL, page $FRONTEND_URL"
}

cmd_app_stop() {
    local port pids
    for port in 8080 5173; do
        pids="$(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
        [[ -n "$pids" ]] && kill $pids 2>/dev/null || true
    done
    echo "app stopped"
}

sub="${1:-}"; shift || true
case "$sub" in
    prepare) cmd_prepare ;;
    checks) cmd_checks "$@" ;;
    app-start) cmd_app_start "$@" ;;
    app-stop) cmd_app_stop ;;
    *) sed -n '2,10p' "$SELF" >&2; exit 2 ;;
esac
