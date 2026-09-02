#!/usr/bin/env bash
#
# Drive each ticket through implement -> review -> done, with rework.
#
#   issues/      work not started, or sent back by a reviewer with feedback
#   to-review/   an implementer says it is finished; nobody has checked
#   done/        a reviewer has read the code and watched the feature work
#
# Every session is a fresh Claude Code session with a cold context. The implementer
# and the reviewer are deliberately different sessions: a session that just wrote the
# code is the worst possible judge of whether the code works.
#
# Each ticket gets its own branch, ticket/NN-slug, and keeps it: nothing is merged. Every
# rework attempt stays on that one branch, so it holds the whole story of one ticket.
# Because the tickets are a dependency chain, each branch is cut from the previous
# accepted ticket's branch rather than from the base, which stacks them in order. Pass
# --merge to fold each accepted branch back into the base instead.
#
# The script never takes a session's word for anything. It checks which folder the
# ticket file actually ended up in, and runs the build itself.

set -euo pipefail

FEATURE_DIR="${FEATURE_DIR:-.scratch/seed-repo-lab-readiness}"
ISSUES_DIR="$FEATURE_DIR/issues"
REVIEW_DIR="$FEATURE_DIR/to-review"
DONE_DIR="$FEATURE_DIR/done"
LOG_DIR="$FEATURE_DIR/logs"
SPEC_FILE="$FEATURE_DIR/spec.md"

BACKEND_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:5173"

BRANCH_PREFIX="${BRANCH_PREFIX:-ticket}"
PERMISSION_MODE="acceptEdits"
MODEL=""
MAX_ATTEMPTS=3
MERGE_ON_ACCEPT=0
DRY_RUN=0
KEEP_GOING=0
ASSUME_YES=0
FROM=""
ONLY=""

BASE_BRANCH=""
PARENT_REF=""
APP_STARTED_BY_US=0
APP_DB=""
APP_BACKEND_LOG=""
APP_FRONTEND_LOG=""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    cat <<'USAGE'
Usage: scripts/implement-tickets.sh [options]

Moves each ticket through three folders, one fresh Claude Code session at a time:

  issues/  --(implementer)-->  to-review/  --(reviewer)-->  done/
                                    |
                                    +--(reviewer, with feedback)--> issues/

A reviewer that is not convinced writes what is missing into the ticket and sends it
back. The next attempt is a new implementer session that reads that feedback. A ticket
gets --max-attempts tries before the script gives up on it.

The reviewer runs /code-review and also drives the running application for itself:
Playwright against the web page for tickets that touch the UI, HTTP against the API for
the ones that do not. The script boots the app against a throwaway database before each
review and shuts it down afterwards.

Branches:
  Each ticket is implemented on its own branch, ticket/NN-slug, and keeps it. Every
  attempt at a ticket, including rework after a reviewer sends it back, stays on that one
  branch, so the branch holds the whole story of the ticket.

  Nothing is merged. Because the tickets are a dependency chain (03 is blocked by 01, 06
  by 05), each branch is cut from the previous accepted ticket's branch rather than from
  the base, so the branches stack in ticket order and each one has the work it needs.
  Merge them yourself, oldest first.

  Pass --merge to fold each accepted branch back into the base with --no-ff instead. The
  branches still exist afterwards; the base just accumulates the work as it goes.

Options:
  --from NN            Start at ticket NN, skipping earlier ones
  --only NN            Run just ticket NN
  --max-attempts N     Implement/review cycles before giving up on a ticket (default 3)
  --merge              Merge each accepted branch back into the base branch
  --branch-prefix P    Branch name prefix (default "ticket")
  --model NAME         Model for every session (e.g. opus, sonnet)
  --yolo               Use --permission-mode bypassPermissions (see the warning below)
  --keep-going         Carry on after a ticket fails instead of stopping
  -y, --yes            Skip the confirmation prompt
  -n, --dry-run        Print what would run and exit
  -h, --help           This

Logs:
  Everything a run produces lands in <feature>/logs, named per ticket and per attempt so
  nothing is overwritten and a finished run can be read back in order:

    <ticket>.implement.<n>.log     what the implementer ran, and what it got back
    <ticket>.review.<n>.log        the same for the reviewer
    <ticket>.*.jsonl               the untruncated transcript behind each of those
    <ticket>.checks.<n>.log        full mvnw test and typecheck output for that attempt
    <ticket>.app.<n>.backend.log   the application instance that attempt was reviewed on
    <ticket>.app.<n>.frontend.log  the Vite dev server for it
    <ticket>.app.<n>.browser.log   console and page errors, if the reviewer drove the page

  Both prompts name these paths, so the sessions read them rather than guessing. The app
  under review runs with its own package, the web layer and Hibernate's SQL at DEBUG.
  The directory is gitignored.

Permissions:
  The default (acceptEdits) auto-approves file edits but still asks before running
  commands. In non-interactive mode there is nobody to ask, so a session that needs to
  run the build, drive a browser or commit will be refused and the ticket will fail.
  For a genuinely unattended run you want --yolo, which bypasses every permission check
  for these sessions. That is a real loosening: the sessions can run any command. It is
  survivable here because work lands one commit at a time on a branch, so anything
  unwanted is a git reset away, but do not point this at a repo you cannot throw away.
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --from) FROM="$2"; shift 2 ;;
        --only) ONLY="$2"; shift 2 ;;
        --max-attempts) MAX_ATTEMPTS="$2"; shift 2 ;;
        --merge) MERGE_ON_ACCEPT=1; shift ;;
        --branch-prefix) BRANCH_PREFIX="$2"; shift 2 ;;
        --model) MODEL="$2"; shift 2 ;;
        --yolo) PERMISSION_MODE="bypassPermissions"; shift ;;
        --keep-going) KEEP_GOING=1; shift ;;
        -y|--yes) ASSUME_YES=1; shift ;;
        -n|--dry-run) DRY_RUN=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

cd "$(git rev-parse --show-toplevel)"

command -v claude >/dev/null || { echo "claude is not on PATH" >&2; exit 1; }
[[ -d "$ISSUES_DIR" ]] || { echo "no issues directory at $ISSUES_DIR" >&2; exit 1; }

BASE_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$BASE_BRANCH" == "HEAD" ]]; then
    echo "HEAD is detached. Check out the branch these tickets should land on." >&2
    exit 1
fi
PARENT_REF="$BASE_BRANCH"

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
        echo "  using node $(node -v) from nvm"
    else
        echo "  warning: node $(node -v 2>/dev/null || echo missing) is too old for Vite" >&2
    fi
}

has_playwright() {
    python3 -c "import playwright" >/dev/null 2>&1
}

listening_on() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

kill_port() {
    local pids
    pids="$(lsof -nP -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null || true)"
    [[ -n "$pids" ]] && kill $pids 2>/dev/null || true
}

wait_for() {
    local url="$1" seconds="${2:-180}" n=0
    while ! curl -sf -o /dev/null "$url"; do
        n=$((n + 1))
        if [[ $n -ge $seconds ]]; then
            return 1
        fi
        sleep 1
    done
}

# The app under review gets its own throwaway database, so a reviewer making deposits
# does not spend the demo data a trainer is about to present with.
# The prefix is per ticket and per attempt, so a reviewer reads the boot it is actually
# reviewing rather than whatever last overwrote a shared app-backend.log.
#
# The app is started chattier than it runs in normal use: its own package at DEBUG, the
# web layer at DEBUG for request and response lines, and Hibernate's SQL. A reviewer that
# sees a wrong number or a 500 can then answer "why" from the log instead of guessing.
start_app() {
    local prefix="$1"
    APP_BACKEND_LOG="$prefix.backend.log"
    APP_FRONTEND_LOG="$prefix.frontend.log"
    if listening_on 8080 && listening_on 5173; then
        echo "  app already running; leaving it alone (no logs captured for it)"
        APP_STARTED_BY_US=0
        return 0
    fi
    APP_STARTED_BY_US=1
    APP_DB="$(mktemp -d)/saving-streak.db"
    echo "  starting app (database: $APP_DB)"
    echo "  backend log:  $APP_BACKEND_LOG"
    echo "  frontend log: $APP_FRONTEND_LOG"
    ( cd backend && SAVING_STREAK_DB="$APP_DB" ./mvnw -q spring-boot:run \
        -Dspring-boot.run.arguments="--logging.level.io.dataroots.savingstreak=DEBUG --logging.level.org.springframework.web=DEBUG --logging.level.org.hibernate.SQL=DEBUG" \
        > "$APP_BACKEND_LOG" 2>&1 & )
    ( cd frontend && npm run dev > "$APP_FRONTEND_LOG" 2>&1 & )
    if ! wait_for "$BACKEND_URL/api/customers" 180; then
        echo "  backend did not come up; see $APP_BACKEND_LOG" >&2
        return 1
    fi
    if ! wait_for "$FRONTEND_URL/" 60; then
        echo "  frontend did not come up; see $APP_FRONTEND_LOG" >&2
        return 1
    fi
    echo "  app is up"
}

stop_app() {
    [[ $APP_STARTED_BY_US -eq 1 ]] || return 0
    echo "  stopping app"
    kill_port 8080
    kill_port 5173
    APP_STARTED_BY_US=0
}

trap stop_app EXIT

# Not -q. A rejected implementer is handed this log, and Maven's quiet mode drops the
# assertion text and the stack trace, which is the only part worth reading.
run_checks() {
    local label="$1" log="$2"
    echo "--- checks ($label) -> $log"
    {
        echo "=== backend: ./mvnw test"
        ( cd backend && ./mvnw test )
    } > "$log" 2>&1 || { echo "    backend tests failed; see $log" >&2; return 1; }
    {
        echo
        echo "=== frontend: npm run typecheck"
        ( cd frontend && npm run typecheck )
    } >> "$log" 2>&1 || { echo "    frontend typecheck failed; see $log" >&2; return 1; }
    echo "--- checks passed ($label)"
}

# Surefire writes a .txt per test class with the failure and its stack trace. Naming the
# directory is more use to an agent than any amount of Maven console output.
SUREFIRE_DIR="backend/target/surefire-reports"

# One branch per ticket, reused across rework attempts so the branch holds the whole
# story of the ticket rather than only its last try.
start_branch() {
    local branch="$1"
    if git show-ref --verify --quiet "refs/heads/$branch"; then
        git checkout --quiet "$branch"
    else
        git checkout --quiet -b "$branch" "$PARENT_REF"
    fi
}

land_branch() {
    local branch="$1"
    if [[ $MERGE_ON_ACCEPT -eq 1 ]]; then
        git checkout --quiet "$BASE_BRANCH"
        git merge --no-ff --no-edit -m "Merge $branch" "$branch"
        PARENT_REF="$BASE_BRANCH"
        echo "    merged into $BASE_BRANCH"
    else
        PARENT_REF="$branch"
        echo "    kept on $branch; the next ticket branches from it"
    fi
}

# Two logs per session. The .jsonl is the whole transcript as the CLI emitted it;
# the .log is that rendered readable. `--verbose` alone would log only the final
# message, which never answers the question you actually have about a bad session,
# namely what it ran and what it got back.
claude_session() {
    local log="$1"
    local jsonl="${log%.log}.jsonl"
    local args=(-p --permission-mode "$PERMISSION_MODE" --verbose
                --output-format stream-json)
    if [[ -n "$MODEL" ]]; then
        args+=(--model "$MODEL")
    fi
    # No --continue and no --resume: every session starts with a cold context.
    claude "${args[@]}" 2>&1 \
        | tee "$jsonl" \
        | python3 "$SCRIPT_DIR/format-transcript.py" \
        | tee "$log"
}

implementer_prompt() {
    local ticket="$1" attempt="$2" branch="$3" log_prefix="$4"
    cat <<PROMPT
/mattpocock-skills:implement $ticket

You are implementing exactly one ticket: $ticket. Read it in full before anything else.
This is attempt $attempt.

Context:
- The spec these tickets came from is $SPEC_FILE. Read it for background.
- Tickets are numbered in dependency order. Everything under "Blocked by" is already
  implemented and committed, so build on it rather than reimplementing it.
- Finished tickets are in $DONE_DIR. Tickets waiting to be checked are in $REVIEW_DIR.
- If the ticket has a "Review feedback" section, a reviewer looked at an earlier attempt
  and was not satisfied. That feedback is the most important thing in the file. Address
  every point in it. Do not delete the section; a later reviewer will read it to see
  whether you did.

Branch:
- You are on $branch, which exists for this ticket alone and was cut from $PARENT_REF.
- Commit your work here. Do not switch, rebase, merge or delete any branch, and do not
  push. The script handles all of that once a reviewer has accepted the work.

Logs, and how to find out what is actually happening:
- Run the backend suite with \`cd backend && ./mvnw test\`. When something fails, do not
  read only the console summary: \`$SUREFIRE_DIR/<test class>.txt\` holds the
  assertion text and the full stack trace for that class, which is the part worth reading.
- The frontend check is \`cd frontend && npm run typecheck\`.
- If you need to see the running application, start it yourself against a throwaway
  database so you do not spend the demo data, and keep its log where you can read it:
      cd backend && SAVING_STREAK_DB=\$(mktemp -d)/s.db ./mvnw -q spring-boot:run \\
        -Dspring-boot.run.arguments="--logging.level.io.dataroots.savingstreak=DEBUG --logging.level.org.springframework.web=DEBUG --logging.level.org.hibernate.SQL=DEBUG" \\
        > $log_prefix.dev.$attempt.backend.log 2>&1 &
      cd frontend && npm run dev > $log_prefix.dev.$attempt.frontend.log 2>&1 &
  The API is then at $BACKEND_URL and the page at $FRONTEND_URL. Stop both when you are
  done. A 500 from the API is a stack trace in that backend log; go and read it.
- If this is not attempt 1, the earlier attempts left logs beside the ticket:
  \`$log_prefix.implement.<n>.log\` is what the previous implementer did,
  \`$log_prefix.review.<n>.log\` is what the reviewer ran and saw, and
  \`$log_prefix.checks.<n>.log\` is the build output that rejected it. The reviewer's log
  is usually the fastest way to reproduce the failure they are describing. Each has a
  \`.jsonl\` twin with the untruncated transcript if the rendering has cut something you
  need.

Logging is part of the feature, not an extra:
- Anything you implement must log its own flow. The script runs the application with
  \`io.dataroots.savingstreak\` at DEBUG and tells the reviewer to review from that log,
  so a feature that logs nothing leaves the reviewer with response bodies and guesswork
  and will be sent back.
- Use SLF4J, never System.out:
      private static final Logger log = LoggerFactory.getLogger(Thing.class);
- What to log, at which level:
    INFO   one line per business event, with its outcome and the values that decided it
           (amounts, ids, balances, points, which branch was taken).
    WARN   every refusal or rejected request, and the reason it was refused.
    DEBUG  the inputs and intermediate values behind a decision, so someone reading the
           log can recompute the result by hand.
    ERROR  unexpected failures, with the exception, not a swallowed message.
- Log the reason, not only the fact: "rejected deposit: amount 0" beats "deposit failed".
- Keep the lines greppable and machine-readable, in the style of the surrounding code:
      log.info("deposit accepted customerId={} cents={} pointsEarned={} newBalance={}",
               ...);
- Do not log secrets or full request bodies, and do not log inside a tight loop.
- If you are the first to add logging to a class or package, that is expected; set the
  pattern rather than skipping it.

Rules:
- Work only on this ticket. Do not start, edit or tick off any other ticket file.
- Do not weaken, skip or delete existing tests to make something pass.
- The full backend test suite and the frontend typecheck must both pass.
- You are not the reviewer. Do not move this ticket to $DONE_DIR under any circumstance.

When you believe every acceptance criterion is met and your work is committed:
1. Tick the checkboxes you have genuinely satisfied. Leave the rest unticked.
2. Set the Status line to: Status: needs-review
3. Move the file into $REVIEW_DIR (create the directory if needed). Use git mv if the
   file is tracked, plain mv if it is not.
4. Commit that move.

If you cannot get there, leave the file in $ISSUES_DIR with its Status unchanged, and
end your reply saying what blocked you.
PROMPT
}

reviewer_prompt() {
    local ticket="$1" attempt="$2" playwright="$3" branch="$4" base="$5"
    local app_prefix="$6" checks_log="$7"
    cat <<PROMPT
You are reviewing somebody else's work. You did not write this code and you should not
trust it. The ticket is $ticket, on attempt $attempt. Read it in full, then read
$SPEC_FILE for the intent behind it.

The work is on branch $branch, which was cut from $base. Everything that branch adds on
top of $base is what you are reviewing, and nothing else.

Do two independent things, in this order.

1. Review the code.
   Run /code-review over $base..$branch. Judge it against the ticket's acceptance
   criteria and against the surrounding code's conventions, not against your own taste.

2. Watch the feature actually work.
   The application is already running: API at $BACKEND_URL, web page at $FRONTEND_URL.
   It is on a throwaway database seeded with the usual demo customers, so you may
   deposit, withdraw, claim and change anything you like.
   - If the ticket changes the web page, drive it in a browser and look at what you get.
     Playwright is $playwright. Take screenshots and actually read them; a blank frame or
     an unstyled page is a failure, not a pass.
   - If the ticket is backend only, exercise it over HTTP against the running API.
   - Either way, try the refusals and edge cases the ticket names, not only the happy
     path. A criterion you did not exercise is a criterion you cannot tick.
   Run the full backend test suite and the frontend typecheck yourself too.

   The application is logging for you. Read the logs; do not review from response bodies
   alone.
   - \`$app_prefix.backend.log\` is this exact application instance, started with its own
     package, the web layer and Hibernate's SQL all at DEBUG. Every request it served is
     in there, along with the SQL it ran and the stack trace behind any 500.
   - \`$app_prefix.frontend.log\` is the Vite dev server: build and transform errors show
     up here and nowhere else.
   - \`$checks_log\` is the build the script already ran to let this ticket reach you.
     Per-class failures and stack traces are in \`$SUREFIRE_DIR/*.txt\`.
   - If you drive the page with Playwright, subscribe to the browser's own output before
     you navigate and write it to \`$app_prefix.browser.log\`, then read it:
         page.on("console", lambda m: log(f"[console:{m.type}] {m.text}"))
         page.on("pageerror", lambda e: log(f"[pageerror] {e}"))
         page.on("requestfailed", lambda r: log(f"[requestfailed] {r.url} {r.failure}"))
     A screenshot that looks right while the console is full of errors is not a pass.
   - Quote what you actually read from these logs in whichever section you write. A claim
     with a log line behind it is worth far more to the next person than an assertion.
   - The implementer was required to log the flow it built: business events and their
     deciding values at INFO, refusals and their reason at WARN, the inputs behind a
     decision at DEBUG. Check that it did. If you exercise the new behaviour and
     \`$app_prefix.backend.log\` shows nothing from \`io.dataroots.savingstreak\` about
     it, or a refusal you triggered left no line saying why, that is a missing acceptance
     criterion: send the ticket back and say which flow was silent.

Then decide, and act on the decision. Commit whichever you do to $branch; do not switch
branches, merge, rebase, delete a branch or push. The ticket keeps this branch.

If every acceptance criterion is genuinely met and you have seen the feature work:
1. Tick every checkbox in the ticket.
2. Set the Status line to: Status: done
3. Append a short section titled "Verified" saying what you actually ran and saw. Write
   it for someone who was not here and is deciding whether to merge this branch.
4. Move the file into $DONE_DIR (create it if needed), and commit the move.

If anything is missing, broken, or you could not verify it:
1. Leave the checkboxes for anything unproven unticked.
2. Set the Status line to: Status: needs-info
3. Append a section titled "Review feedback - attempt $attempt" listing, as concrete
   points, exactly what is missing or wrong and how you found it. Write it for the next
   implementer, who will arrive with no memory of this conversation: name the behaviour
   you expected, what you saw instead, and how to reproduce it.
4. Move the file back into $ISSUES_DIR, and commit the move.

You are a reviewer, not an implementer. Do not fix the code, do not adjust the tests to
make them pass, and do not move the ticket to $DONE_DIR because it is nearly there.
Sending a ticket back is a normal outcome and costs far less than a wrong pass.
PROMPT
}

send_back() {
    local ticket_path="$1" attempt="$2" heading="$3" detail="$4"
    {
        echo
        echo "## Review feedback - attempt $attempt ($heading)"
        echo
        echo "$detail"
    } >> "$ticket_path"
    mv "$ticket_path" "$ISSUES_DIR/"
}

tickets() {
    find "$ISSUES_DIR" -maxdepth 1 -name '[0-9][0-9]-*.md' | sort
}

selected=()
while IFS= read -r ticket; do
    num="$(basename "$ticket" | cut -d- -f1)"
    if [[ -n "$ONLY" && "$num" != "$ONLY" ]]; then
        continue
    fi
    if [[ -n "$FROM" && "$num" < "$FROM" ]]; then
        continue
    fi
    selected+=("$ticket")
done < <(tickets)

if [[ ${#selected[@]} -eq 0 ]]; then
    echo "nothing to do: no matching tickets in $ISSUES_DIR"
    exit 0
fi

PLAYWRIGHT_NOTE="not installed, so use HTTP against the API and say in your feedback that you could not drive the page"
if has_playwright; then
    PLAYWRIGHT_NOTE="available (python3, sync API, chromium)"
fi

echo "Repository:   $(pwd)"
echo "Base branch:  $BASE_BRANCH"
echo "Branches:     $BRANCH_PREFIX/NN-slug, one per ticket"
if [[ $MERGE_ON_ACCEPT -eq 1 ]]; then
    echo "On accept:    branch kept, and merged into $BASE_BRANCH with --no-ff"
else
    echo "On accept:    branch kept, nothing merged; the next ticket branches from it"
fi
echo "Permissions:  $PERMISSION_MODE"
echo "Model:        ${MODEL:-<default>}"
echo "Max attempts: $MAX_ATTEMPTS per ticket"
echo "Playwright:   $PLAYWRIGHT_NOTE"
echo "Tickets:"
for ticket in "${selected[@]}"; do
    echo "  - $(basename "$ticket")"
done
echo

if [[ $DRY_RUN -eq 1 ]]; then
    echo "dry run: nothing was executed"
    exit 0
fi

if [[ -n "$(git status --porcelain)" ]]; then
    echo "working tree is not clean. Each ticket should land as its own commit," >&2
    echo "so commit or stash what you have first." >&2
    exit 1
fi

if [[ $ASSUME_YES -eq 0 ]]; then
    read -r -p "Run ${#selected[@]} ticket(s) from $BASE_BRANCH? [y/N] " reply
    if [[ "$reply" != "y" && "$reply" != "Y" ]]; then
        echo "aborted"
        exit 1
    fi
fi

mkdir -p "$ISSUES_DIR" "$REVIEW_DIR" "$DONE_DIR" "$LOG_DIR"
pick_node

echo "=== baseline checks before starting"
if ! run_checks baseline "$LOG_DIR/baseline.checks.log"; then
    echo "the repository is already failing its own checks; fix that before looping" >&2
    echo "see $LOG_DIR/baseline.checks.log" >&2
    exit 1
fi

failed=()
accepted=()
for ticket in "${selected[@]}"; do
    file="$(basename "$ticket")"
    name="${file%.md}"
    branch="$BRANCH_PREFIX/$name"
    ticket_base="$PARENT_REF"
    echo
    echo "############ $name"

    if ! start_branch "$branch"; then
        echo "    could not create or check out $branch" >&2
        failed+=("$name (branch $branch unavailable)")
        if [[ $KEEP_GOING -eq 0 ]]; then exit 1; fi
        continue
    fi
    echo "    branch: $branch (from $ticket_base)"

    attempt=1
    settled=""
    while [[ $attempt -le $MAX_ATTEMPTS ]]; do
        echo
        echo "=== $name: implementing (attempt $attempt/$MAX_ATTEMPTS)"
        implementer_prompt "$ISSUES_DIR/$file" "$attempt" "$branch" "$LOG_DIR/$name" \
            | claude_session "$LOG_DIR/$name.implement.$attempt.log" || true

        if [[ ! -f "$REVIEW_DIR/$file" ]]; then
            echo "    implementer did not move the ticket to to-review" >&2
            settled="stalled in issues"
            break
        fi
        echo "    -> to-review"

        # Cheap objective gate before spending a review session on a red build.
        if ! run_checks "$name attempt $attempt" "$LOG_DIR/$name.checks.$attempt.log"; then
            echo "    build is red; sending back without review"
            send_back "$REVIEW_DIR/$file" "$attempt" "automated checks" \
"The backend test suite or the frontend typecheck failed when this ticket reached
review, so no reviewer looked at it. Make both pass before sending it back.

The full output of that run is in \`$LOG_DIR/$name.checks.$attempt.log\`. Read it before
you change anything; the tail below is only the last of it. Per-class failures and their
stack traces are in \`$SUREFIRE_DIR/*.txt\`.

Tail of the failing run:

\`\`\`
$(tail -60 "$LOG_DIR/$name.checks.$attempt.log")
\`\`\`"
            attempt=$((attempt + 1))
            continue
        fi

        echo
        echo "=== $name: reviewing (attempt $attempt/$MAX_ATTEMPTS)"
        if ! start_app "$LOG_DIR/$name.app.$attempt"; then
            echo "    could not start the app for review" >&2
            settled="app would not start"
            break
        fi
        reviewer_prompt "$REVIEW_DIR/$file" "$attempt" "$PLAYWRIGHT_NOTE" "$branch" \
            "$ticket_base" "$LOG_DIR/$name.app.$attempt" "$LOG_DIR/$name.checks.$attempt.log" \
            | claude_session "$LOG_DIR/$name.review.$attempt.log" || true
        stop_app

        if [[ -f "$DONE_DIR/$file" ]]; then
            echo "    -> done"
            settled="done"
            break
        fi
        if [[ -f "$ISSUES_DIR/$file" ]]; then
            echo "    -> sent back to issues with feedback"
            attempt=$((attempt + 1))
            continue
        fi
        echo "    reviewer left the ticket in to-review; treating as unreviewed" >&2
        settled="stalled in to-review"
        break
    done

    if [[ "$settled" == "done" ]]; then
        land_branch "$branch"
        accepted+=("$branch")
        continue
    fi

    if [[ -z "$settled" ]]; then
        settled="not accepted after $MAX_ATTEMPTS attempts"
    fi
    echo "    FAILED: $settled"
    failed+=("$name ($settled) on $branch")

    if [[ $KEEP_GOING -eq 0 ]]; then
        echo
        echo "stopping. Later tickets are blocked by this one." >&2
        echo "The work so far is on $branch, which is still checked out." >&2
        echo "Logs: $LOG_DIR/$name.*.log" >&2
        echo "Fix or finish it, then re-run with --from ${name%%-*}" >&2
        exit 1
    fi
    git checkout --quiet "$BASE_BRANCH"
done

echo
if [[ ${#accepted[@]} -gt 0 ]]; then
    echo "accepted branches, in the order they should be merged:"
    for entry in "${accepted[@]}"; do
        echo "  - $entry"
    done
    if [[ $MERGE_ON_ACCEPT -eq 0 ]]; then
        echo "Each is stacked on the one above it, so merge them oldest first."
    fi
    echo
fi

if [[ ${#failed[@]} -gt 0 ]]; then
    echo "finished with failures:"
    for entry in "${failed[@]}"; do
        echo "  - $entry"
    done
    exit 1
fi
echo "all tickets accepted. Completed tickets are in $DONE_DIR"
