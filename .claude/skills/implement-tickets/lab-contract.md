# The lab

The protocol in `SKILL.md` is the same in every repository. What differs is the **lab**:
how this repository is checked, how its application runs, how a reviewer exercises it,
and which of its own rules the reviewer holds the work to. The lab is two files in
`FEATURE_DIR`, written once per repository by whichever session first runs the skill
there, committed, and reused by every later run:

- `FEATURE_DIR/lab.sh`, the commands the orchestrator runs
- `FEATURE_DIR/lab.md`, the notes spliced into both sub-agent prompts as `{{LAB}}`

If both exist, read them, confirm the commands they name still exist in the repo, and
use them. Otherwise derive them as below, then smoke-test them before the first ticket.

## Deriving the lab

Read, in this order, and take the answer from the first place that has it:

1. The repository's agent docs: `CLAUDE.md`, `AGENTS.md`, anything under `docs/agents/`.
   These hold the unwritten conventions and the rules a reviewer must enforce.
2. CI configuration (`.github/workflows/`, `.gitlab-ci.yml`, ...). CI is the authoritative
   list of what "the checks" are. The lab runs the same commands, so a green gate here
   means a green pipeline there.
3. Build and package files (`pom.xml`, `build.gradle`, `package.json`, `pyproject.toml`,
   `Cargo.toml`, `Makefile`, ...) for the commands themselves.
4. The application's configuration for how to run it locally: its ports, a health URL,
   the setting that points it at its database or state directory, and how to raise its
   own log level.

## `lab.sh`

Every ticket is worked on its own branch in the repository's one checkout, and the
orchestrator runs the copy of `lab.sh` that is on the branch under test. So the script
must act on the checkout it lives in, not on the caller's working directory:

    SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
    cd "$(git -C "$(dirname "$SELF")" rev-parse --show-toplevel)"

Four subcommands, each exiting non-zero when the thing did not happen:

- `prepare`: whatever the checkout lacks before the checks can run: dependency installs
  (`npm ci`, `pip install -e .`, ...), generated code, a local env file copied from its
  example. Idempotent, and cheap when there is nothing to do, because it runs again on
  every ticket branch. Everything it writes must be ignored by git, or it stands in the
  way of the next branch switch. Compilers that fetch their own dependencies (Maven,
  Cargo) need nothing here.
- `checks <log>`: every check CI runs, in order, all output appended to `<log>`. Do not
  run the build tool in quiet mode: a rejected implementer is handed this log, and quiet
  modes drop the assertion text and the stack trace, which is the only part worth
  reading. Print where per-test failure detail lives if the build tool writes it
  somewhere (Surefire reports, pytest's `--junitxml`, ...).
- `app-start <prefix>`: boot the application for a reviewer. It must
  - refuse to start if its ports are already taken (print what to do; exit 1),
  - run on throwaway state (a temp directory, a fresh database file) so a reviewer
    changing data spends nothing real,
  - log verbosely for the application's own packages and its web and persistence
    layers, so a reviewer answers "why" from the log rather than guessing,
  - write each process's output to `<prefix>.<process>.log`,
  - wait for a health URL, and on failure stop what it started and exit 1,
  - print the URLs and the log paths.
  If the repository has nothing to run (a library, a CLI), print that and exit 0; the
  notes then say how a reviewer exercises the code instead.
- `app-stop`: stop whatever `app-start` started, idempotent.

Put a usage comment at the top naming the four subcommands and what this repository's
versions of them do, and print it when called with anything else.

## `lab.md`

Written for a sub-agent arriving with a cold context, under these four headings, in
this order, and short: it is spliced into a prompt that is already long.

1. **Checks**: the exact commands, one per line, and where per-failure detail lives.
2. **Running the application**: how to start it yourself on throwaway state with
   verbose logging (the orchestrator starts it for reviewers; implementers start it
   themselves), its URLs, and the shape of the log files.
3. **Exercising it**: what a reviewer drives and with what: a browser (name the tool
   and whether it is installed, e.g. `python3 -c "import playwright"`), HTTP, a CLI, a
   REPL. Say what to subscribe to for errors (browser console, server log) and where to
   write it.
4. **Rules this repository holds work to**: the conventions from the agent docs that go
   beyond "tests pass", stated as things a reviewer checks and sends work back for. Quote
   the repository's own wording where it has one.

## Smoke test

Commit `lab.sh` and `lab.md` on the base branch first, so every ticket branch carries
them. Then run the test in the checkout itself, on `BASE`, before the first ticket branch
exists:

- `lab.sh prepare`, then `lab.sh checks <logs>/lab.smoke.log`, must be green. If the base
  is red, stop and tell the user: the gate would blame every ticket for a failure none of
  them caused.
- `lab.sh app-start <logs>/lab.smoke` then `lab.sh app-stop`, and the health URL must
  have answered in between.

Leave the checkout clean and still on `BASE` afterwards; `app-stop` even if `app-start`
failed.
