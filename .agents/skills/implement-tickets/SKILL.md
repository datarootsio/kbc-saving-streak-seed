---
name: implement-tickets
description: Drive a feature's local tickets through implementation, independent review, and completion using a fresh isolated sub-agent for every role and rework attempt. Invoke explicitly when a user asks to run the ticket implementation protocol.
---

# Implement Tickets

Act only as the orchestrator. Never implement or review a ticket yourself. Spawn a fresh,
isolated sub-agent for every implementation and review step, give it the complete rendered
prompt, and wait for it to finish. Do not reuse an earlier sub-agent or continue its context.
When the host supports disabling context inheritance, use a cold context. When it supports
model and reasoning-effort overrides, forward the requested values to every sub-agent.

Never take a sub-agent's report as the verdict. Decide each step from the ticket's actual
folder and from checks you run yourself.

Set `SKILL_DIR` to the absolute directory containing this `SKILL.md` before invoking bundled
resources.

Every implementer must enter through Matt Pocock's repository-local `implement` skill. Set
`IMPLEMENT_SKILL_RELATIVE = .agents/skills/implement/SKILL.md`; each ticket prompt receives
the copy at `WORKDIR/IMPLEMENT_SKILL_RELATIVE`. That skill may invoke other repository-local
skills, including `tdd` and `code-review`. Its review is an implementation-stage self-review;
the separate reviewer in this protocol remains the only agent that may accept the ticket.

Tickets use the issue tracker's documented layout (`.scratch/<feature>/` in this repository,
or the layout named by `docs/agents/issue-tracker.md`): `spec.md` beside three folders.

```text
issues/  --(implementer)-->  to-review/  --(reviewer)-->  done/
                                      |
                                      +--(reviewer, with feedback)--> issues/
```

Give every ticket its own branch, `ticket/NN-slug`, checked out in its own git worktree
under `WORKTREES = <repo root>/.worktrees/`. Keep the branch across rework attempts so it
contains the whole ticket history. Remove the worktree only after the ticket settles as
done. Keep the main checkout on `BASE` for the entire run. Tickets form a dependency chain,
so cut each branch from the previous accepted ticket's branch; update the base checkout
only when `--merge` is passed.

Use the mechanical helpers instead of re-deriving them:

- [`scripts/protocol.sh`](scripts/protocol.sh) provides `tickets` and `send-back`. Invoke it
  as `SKILL_DIR/scripts/protocol.sh` after exporting `FEATURE_DIR`.
- `FEATURE_DIR/lab.sh` provides repository-specific `prepare`, `checks`, `app-start`, and
  `app-stop` commands. Read [`references/lab-contract.md`](references/lab-contract.md) when
  the lab is missing or stale. Always run the copy inside the ticket worktree:
  `WORKDIR/FEATURE_DIR/lab.sh`.

## Arguments

- `--feature DIR`: feature directory. Default to the single directory under `.scratch/`
  containing `issues/`; ask if several match.
- `--from NN`: start at ticket `NN`.
- `--only NN`: run only ticket `NN`.
- `--max-attempts N`: maximum attempts per ticket; default `3`.
- `--merge`: merge each accepted branch into `BASE` with `--no-ff`.
- `--resume`: include tickets already in `to-review/` and begin them at review.
- `--keep-going`: continue after a ticket fails even though later tickets may depend on it.
- `--model NAME`: use the named model for every sub-agent when the host supports it.
- `--effort LEVEL`: use the named reasoning effort for every sub-agent when supported.
  Validate the level against the host before starting.
- `-y`: skip the confirmation step.

If a requested model or effort is unsupported, say so before confirmation. Do not silently
substitute another value.

## Setup

1. Go to the repository root. This main checkout remains there for the whole run. Require a
   clean working tree (`git status --porcelain` is empty) and a named branch at `HEAD`; that
   branch is `BASE`. Stop otherwise. Do not commit or stash the user's existing changes.
   Ensure `.worktrees/` is in `.gitignore`; if absent, add and commit it on `BASE`.
2. Resolve and export `FEATURE_DIR`. Run `SKILL_DIR/scripts/protocol.sh tickets [--resume]
   [--from NN] [--only NN]` and retain the ordered result. An empty result means there is
   nothing to do.
3. Read `SKILL_DIR/references/lab-contract.md`. Reuse `FEATURE_DIR/lab.sh` and `lab.md` when
   both still match the repository. Otherwise derive them, commit them on `BASE`, and run the
   contract's smoke test. Set `LAB` to the complete content of `lab.md`.
4. Confirm that the host can spawn fresh isolated sub-agents. Resolve requested model and
   reasoning-effort settings without creating custom host-specific agent profiles.
5. Run `git cat-file -e "$BASE:$IMPLEMENT_SKILL_RELATIVE"` to verify that every ticket
   worktree will contain Matt Pocock's `implement` entry point. Stop and report the missing
   skill if it does not.
6. Print the plan: base branch, feature directory, implement skill path, accept policy,
   model, effort, maximum attempts, and ordered ticket list. Unless `-y` was passed, wait
   for confirmation.
7. Create `FEATURE_DIR/logs`. Set `PARENT = BASE`.

## Per Ticket

Let `NAME` be the filename without `.md`, `BRANCH = ticket/NAME`, `WORKDIR =
WORKTREES/NAME`, and `TICKET_BASE = PARENT`. `LOG_PREFIX` is the absolute path to the main
checkout's `FEATURE_DIR/logs/NAME`, so logs survive worktree removal. Every other path sent
to a sub-agent is absolute and inside `WORKDIR`. Set `IMPLEMENT_SKILL` to the absolute path
`WORKDIR/IMPLEMENT_SKILL_RELATIVE`.

Render the templates in [`references/implementer.md`](references/implementer.md) and
[`references/reviewer.md`](references/reviewer.md). Replace every placeholder and verify
that no `{{` remains.

| Placeholder | Value |
| --- | --- |
| `TICKET` | Current ticket path in `issues/` or `to-review/` |
| `ATTEMPT` | Attempt number |
| `BRANCH`, `TICKET_BASE`, `WORKDIR`, `LOG_PREFIX`, `LAB`, `IMPLEMENT_SKILL` | Values defined above |
| `SPEC_FILE`, `ISSUES_DIR`, `REVIEW_DIR`, `DONE_DIR` | Absolute paths under `WORKDIR/FEATURE_DIR` |
| `APP_PREFIX`, `CHECKS_LOG` | `LOG_PREFIX.app.<attempt>` and the gate log; reviewer only |

1. Prepare the worktree. Reuse `WORKDIR` when an earlier run left it. Otherwise run
   `git worktree add WORKDIR -b BRANCH PARENT`, or `git worktree add WORKDIR BRANCH` if the
   branch exists. Then run `WORKDIR/FEATURE_DIR/lab.sh prepare`. If it fails, settle the
   ticket as **worktree would not prepare**.
2. Run attempts from 1 through `--max-attempts`:

   a. **Implement.** Skip this only when `--resume` is active and
      `to-review/NAME.md` exists in `WORKDIR`. Render the implementer template and use it as
      the entire prompt for a new isolated sub-agent. The template must direct the sub-agent
      through `IMPLEMENT_SKILL` before it performs ticket work. Wait for completion and save
      its final report verbatim to `LOG_PREFIX.implement.<attempt>.md`. If
      `to-review/NAME.md` does not exist afterward, settle as **stalled in issues**.

   b. **Gate.** Run `WORKDIR/FEATURE_DIR/lab.sh checks
      LOG_PREFIX.checks.<attempt>.log`. On failure, do not spend a reviewer on a red build.
      Write a detail file naming the checks log and the lab's per-failure detail as the first
      evidence to inspect, followed by the last 60 lines of the checks log in a fenced block.
      Run `SKILL_DIR/scripts/protocol.sh send-back to-review/NAME.md <attempt>
      "automated checks" <detail-file>`, commit as
      `Send NAME back: automated checks`, and start the next attempt.

   c. **Review.** Run `WORKDIR/FEATURE_DIR/lab.sh app-start
      LOG_PREFIX.app.<attempt>`. If it fails, settle as **app would not start**. Render the
      reviewer template and use it as the entire prompt for a different new isolated
      sub-agent. Wait for completion, save its final report verbatim to
      `LOG_PREFIX.review.<attempt>.md`, and always run
      `WORKDIR/FEATURE_DIR/lab.sh app-stop` after the reviewer returns or fails.

   d. **Verdict.** Read the worktree folders, not the report. `done/NAME.md` means **done**.
      `issues/NAME.md` means sent back, so begin the next attempt. Anything else means
      **stalled in to-review**. Exhausting the loop means **not accepted after N attempts**.

3. **Land a done ticket.** With `--merge`, run in the main checkout
   `git merge --no-ff --no-edit -m "Merge BRANCH" BRANCH` and set `PARENT = BASE`. Without
   `--merge`, set `PARENT = BRANCH` so the next ticket stacks on it. Remove `WORKDIR` with
   `git worktree remove`; retain the branch.
4. **Handle any other result.** Report the reason, branch, and worktree, leaving both branch
   and worktree for inspection. Without `--keep-going`, stop because later tickets depend on
   this one. Point to the logs and tell the user to rerun with `--from NN` after resolving it.
   With `--keep-going`, continue to the next ticket.

## Summary

List accepted branches in merge order, oldest first, noting whether they are stacked or
already merged. Then list every failed ticket with its reason, branch, worktree, and the
relevant evidence under `FEATURE_DIR/logs/`. Every outcome claim must point to a log file.
