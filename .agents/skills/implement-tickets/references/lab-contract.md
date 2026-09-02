# The Lab

The ticket protocol is reusable; the lab records how this repository is checked, run, and
exercised. The first protocol run creates two files in `FEATURE_DIR`, commits them, and later
runs reuse them:

- `FEATURE_DIR/lab.sh`: deterministic commands the orchestrator runs
- `FEATURE_DIR/lab.md`: concise repository guidance inserted into sub-agent prompts as `{{LAB}}`

If both exist, verify that their commands still match the repository before reuse. Otherwise,
derive and smoke-test them before the first ticket.

## Derive the Lab

Read these sources in order and take each answer from the first authoritative source:

1. Repository agent instructions such as `AGENTS.md`, equivalent root instruction files, and
   files under `docs/agents/`. These define conventions and review rules beyond automated
   checks.
2. CI configuration such as `.github/workflows/` or `.gitlab-ci.yml`. CI is authoritative
   for required checks; the lab runs equivalent commands.
3. Build and package files such as `pom.xml`, `build.gradle`, `package.json`,
   `pyproject.toml`, `Cargo.toml`, or `Makefile`.
4. Application configuration defining local ports, health checks, database or state paths,
   and verbose logging.

## `lab.sh`

The orchestrator runs the copy on each checked-out ticket branch. Make the script act on the
repository checkout containing that copy, not the caller's directory:

```bash
SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
cd "$(git -C "$(dirname "$SELF")" rev-parse --show-toplevel)"
```

Implement four subcommands. Each exits non-zero when its promised outcome does not occur.

- `prepare`: idempotently provide what a fresh checkout lacks before checks can run, such as
  dependencies, generated code, or a local environment file. Compilers that fetch their own
  dependencies need no extra preparation.
- `checks <log>`: run every CI check in order and append all output to `<log>`. Do not enable
  quiet mode; rejected work needs assertion text and stack traces. Print the location of
  per-test failure details when the build tool creates them.
- `app-start <prefix>`: start the application for a reviewer. It must:

  - refuse to start if required ports are occupied, with an actionable message;
  - use throwaway state so review cannot alter real data;
  - enable verbose logs for application, web, and persistence code;
  - write every process's output to `<prefix>.<process>.log`;
  - wait for a health endpoint, cleaning up and failing if readiness is not reached; and
  - print URLs and log paths.

  If the repository is a library or CLI with no service, state that and succeed; explain the
  alternative exercise method in `lab.md`.
- `app-stop`: idempotently stop everything started by `app-start`.

Add a usage comment naming all four subcommands and their repository-specific behavior.
Print it for unknown or missing commands.

## `lab.md`

Write for a sub-agent arriving without conversation history. Keep it short because it is
inserted into an already detailed prompt, using these headings in order:

1. **Checks**: exact commands and per-failure detail locations.
2. **Running the application**: throwaway-state startup, verbose logging, URLs, and log file
   shapes.
3. **Exercising it**: what to drive and with which available browser, HTTP, CLI, or REPL tool;
   include the error stream to capture and where to store it.
4. **Rules this repository holds work to**: agent-document conventions beyond passing tests,
   phrased as review checks. Preserve exact repository wording when it is material.

## Smoke Test

Commit `lab.sh` and `lab.md` on `BASE` so every ticket branch inherits them. With `BASE`
checked out and clean, run:

1. `lab.sh prepare`
2. `lab.sh checks <logs>/lab.smoke.log`
3. `lab.sh app-start <logs>/lab.smoke`
4. Verify the health endpoint while the app is running.
5. `lab.sh app-stop`

Always run `app-stop`, including after a failed start. Verify that the checkout is still
clean afterward. If base checks are red, stop: the gate would otherwise blame every ticket
for a pre-existing failure.
