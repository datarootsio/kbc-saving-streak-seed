---
name: implement-tickets
description: Drive a feature's tickets through implement -> review -> done, one fresh sub-agent per session, with rework.
disable-model-invocation: true
argument-hint: "[--feature DIR] [--from NN] [--only NN] [--max-attempts N] [--merge] [--resume] [--keep-going] [--model NAME] [--effort LEVEL] [-y]"
---

You are the orchestrator. You never implement and never review; you spawn a fresh
sub-agent for each of those and verify what it left behind. A sub-agent that just wrote
the code is the worst possible judge of whether it works, so the implementer and the
reviewer of one attempt are always two different sub-agents, each with a cold context:
every `Agent` call is a new sub-agent. `subagent_type` is `ticket-driver-<effort>` when
`--effort` was given and `general-purpose` otherwise, and `model` is set to `--model` when
given. Never `fork`, never `SendMessage` an earlier sub-agent, never do their work
yourself when they fall short.

Never take a sub-agent's word for anything. What decides each step is which folder the
ticket file actually ended up in, and a build you ran yourself.

Tickets live in the issue tracker's layout (`.scratch/<feature>/` here, or whatever the
repository's `docs/agents/issue-tracker.md` says): `spec.md` beside three folders.

    issues/  --(implementer)-->  to-review/  --(reviewer)-->  done/
                                      |
                                      +--(reviewer, with feedback)--> issues/

Every ticket gets its own branch, `ticket/NN-slug`, checked out in its own git worktree
under `WORKTREES = <repo root>/.worktrees/`. The branch is kept across rework attempts,
so it holds the whole story of the ticket; the worktree is where that ticket's sub-agents
work and is removed once the ticket settles. The main checkout stays on `BASE` for the
whole run. Tickets are a dependency chain, so each branch is cut from the previous
accepted ticket's branch; the base only accumulates work when `--merge` is passed.

Two scripts do the mechanical steps; run them rather than re-deriving them:

- `protocol.sh` in this skill's directory (`tickets`, `send-back`), the same everywhere.
  It reads `FEATURE_DIR` from the environment; export it.
- `FEATURE_DIR/lab.sh` (`prepare`, `checks`, `app-start`, `app-stop`), specific to the
  repository, written to `lab-contract.md` in this skill's directory. Always run the
  copy inside the ticket's worktree: `WORKDIR/FEATURE_DIR/lab.sh`.

## Arguments

`--feature DIR` the feature directory (default: the one directory under `.scratch/` that
has `issues/`; ask if there are several); `--from NN` start at ticket NN; `--only NN` run
just NN; `--max-attempts N` (default 3); `--merge` fold each accepted branch into the base
with `--no-ff`; `--resume` also pick up tickets already sitting in `to-review/` and start
them at the review step; `--keep-going` carry on after a ticket fails; `--model NAME`
model for every sub-agent; `--effort LEVEL` thinking effort for every sub-agent, one of
`low`, `medium`, `high`, `max` (see the driver agents in Setup); `-y` skip the
confirmation.

## Setup

1. `cd` to the repository root; this is the main checkout, and it stays here all run.
   The working tree must be clean (`git status --porcelain` empty) and HEAD must be a
   branch, not detached: that branch is `BASE`. Stop and say so otherwise; do not commit
   or stash on the user's behalf. `.worktrees/` must be in `.gitignore`; if it is not,
   add it and commit that on `BASE`.
2. Resolve `FEATURE_DIR` and export it. `protocol.sh tickets [--resume] [--from NN]
   [--only NN]` lists the tickets, in order. Empty means nothing to do.
3. **The lab.** Read `lab-contract.md`. Reuse `FEATURE_DIR/lab.sh` and `lab.md` when they
   exist and still match the repository; otherwise derive them, commit them on `BASE`,
   and run the contract's smoke test. `LAB` is the content of `lab.md`.
4. **The driver agents**, only when `--effort` was given. The `Agent` tool takes a model
   but not an effort, so effort can only come from an agent definition:
   `.claude/agents/ticket-driver-<level>.md`, one per level, `model: inherit` so `--model`
   still decides the model, and `effort: <level>`. Check that the one for this run's level
   exists. If it does not, write the four levels, commit them on `BASE`, and stop the run
   there: the agent registry is read once when a session starts, so a definition written
   now does not resolve until the next session. Say that, and that a re-run picks it up.
5. Print the plan (base branch, feature directory, on-accept policy, model, effort, max
   attempts, the ticket list) and, unless `-y`, wait for the user to confirm.
6. `mkdir -p FEATURE_DIR/logs`. `PARENT = BASE`.

## Per ticket

`NAME` is the file name without `.md`, `BRANCH = ticket/NAME`, `WORKDIR =
WORKTREES/NAME`, `TICKET_BASE = PARENT`. `LOG_PREFIX` is the **main checkout's**
`FEATURE_DIR/logs/NAME`, absolute, so the logs outlive the worktree. Every other path
handed to a sub-agent is absolute and inside `WORKDIR`.

The two prompt templates take these `{{PLACEHOLDERS}}`; fill every one, then check no
`{{` is left:

| placeholder | value |
| --- | --- |
| `TICKET` | path of the ticket file where it sits now (`issues/` or `to-review/`) |
| `ATTEMPT` | the attempt number |
| `BRANCH`, `TICKET_BASE`, `WORKDIR`, `LOG_PREFIX`, `LAB` | as above |
| `SPEC_FILE`, `ISSUES_DIR`, `REVIEW_DIR`, `DONE_DIR` | `WORKDIR/FEATURE_DIR/spec.md`, `/issues`, `/to-review`, `/done` |
| `APP_PREFIX`, `CHECKS_LOG` | `LOG_PREFIX.app.<attempt>` and the gate's log (reviewer only) |

1. Make the worktree. If `WORKDIR` already exists (an earlier run left it), reuse it.
   Otherwise `git worktree add WORKDIR -b BRANCH PARENT`, or `git worktree add WORKDIR
   BRANCH` when the branch already exists. Then `WORKDIR/FEATURE_DIR/lab.sh prepare`; if
   that fails the ticket is settled as **worktree would not prepare**.
2. Run attempts from 1 to `--max-attempts`. One attempt:

   a. **Implement**, unless `--resume` and `to-review/NAME.md` exists in `WORKDIR` (then
      go straight to b). Fill `implementer.md` from this skill's directory and pass it as
      the whole prompt of a new sub-agent. Wait for its completion notification. Save its
      final report verbatim to `LOG_PREFIX.implement.<attempt>.md`. If `to-review/NAME.md`
      does not exist afterwards, the ticket is settled as **stalled in issues**: leave the
      attempt loop.

   b. **Gate**: `lab.sh checks LOG_PREFIX.checks.<attempt>.log`. If it fails, no reviewer
      is spent on a red build. Write a detail file that says the checks failed when the
      ticket reached review, names that checks log and the lab's per-failure detail as
      the things to read first, and quotes `tail -60` of the checks log in a fenced block.
      Then `protocol.sh send-back to-review/NAME.md <attempt> "automated checks" <detail
      file>`, commit the move as `Send NAME back: automated checks`, and start the next
      attempt.

   c. **Review**: `lab.sh app-start LOG_PREFIX.app.<attempt>`; if it fails the ticket is
      settled as **app would not start**. Fill `reviewer.md` the same way and pass it as
      the whole prompt of a new sub-agent. Wait for it, save its report verbatim to
      `LOG_PREFIX.review.<attempt>.md`, then `lab.sh app-stop`.

   d. **Verdict**, read from the folders in `WORKDIR`, not the report: `done/NAME.md` exists is
      **done**; `issues/NAME.md` exists means sent back, start the next attempt;
      anything else (still in `to-review/`) is **stalled in to-review**. The loop running
      out is **not accepted after N attempts**.

3. **Land** a done ticket: with `--merge`, in the main checkout `git merge --no-ff
   --no-edit -m "Merge BRANCH" BRANCH` and `PARENT = BASE`; without it, `PARENT = BRANCH`
   so the next ticket stacks on it. Either way `git worktree remove WORKDIR`; the branch
   stays.
4. **Fail** otherwise: report the ticket, why it settled, its branch and its worktree,
   which is left in place for the user to look at. Without `--keep-going`, stop the run
   here (later tickets are blocked by this one) and tell the user where the logs are and
   to re-run with `--from NN` after fixing it. With it, go on to the next ticket.

## Summary

List the accepted branches in the order they should be merged (oldest first; each is
stacked on the one above it unless `--merge`), then the failed tickets with their
reason, branch and worktree. Every claim in the summary points at a file under `logs/`.
