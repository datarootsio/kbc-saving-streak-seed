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
the copy at `REPO/IMPLEMENT_SKILL_RELATIVE`. That skill may invoke other repository-local
skills, including `tdd` and `code-review`. Its review is an implementation-stage self-review;
the separate reviewer in this protocol remains the only agent that may accept the ticket.

Tickets use the issue tracker's documented layout (`.scratch/<feature>/` in this repository,
or the layout named by `docs/agents/issue-tracker.md`): `spec.md` beside three folders.

```text
issues/  --(implementer)-->  to-review/  --(reviewer)-->  done/
                                      |
                                      +--(reviewer, with feedback)--> issues/
```

Give every ticket its own branch, `ticket/NN-slug`, and use the repository's single checkout
for the whole run. Ticket roles run sequentially on the checked-out ticket branch. Keep the
branch across rework attempts so it contains the whole ticket history. Tickets form a
dependency chain: cut each new branch from the previous accepted ticket's branch. Without
`--merge`, leave the newest accepted branch checked out so the next invocation naturally
starts from the right parent.

Use the mechanical helpers instead of re-deriving them:

- [`scripts/protocol.sh`](scripts/protocol.sh) provides `tickets` and `send-back`. Invoke it
  as `SKILL_DIR/scripts/protocol.sh` after exporting `FEATURE_DIR`.
- `FEATURE_DIR/lab.sh` provides repository-specific `prepare`, `checks`, `app-start`, and
  `app-stop` commands. Read [`references/lab-contract.md`](references/lab-contract.md) when
  the lab is missing or stale. Always run the copy on the currently checked-out branch:
  `REPO/FEATURE_DIR/lab.sh`.

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

1. Go to the repository root and set its absolute path as `REPO`. Require a clean checkout
   (`git status --porcelain` is empty) and a named branch at `HEAD`; that branch is `BASE`.
   Stop otherwise. Do not commit or stash the user's existing changes. Every branch switch
   in this protocol has the same clean-checkout precondition.
2. Resolve and export `FEATURE_DIR`. Run `SKILL_DIR/scripts/protocol.sh tickets [--resume]
   [--from NN] [--only NN]` and retain the ordered result. An empty result means there is
   nothing to do.
3. Read `SKILL_DIR/references/lab-contract.md`. Reuse `FEATURE_DIR/lab.sh` and `lab.md` when
   both still match the repository. Otherwise derive them, commit them on `BASE`, and run the
   contract's smoke test. Set `LAB` to the complete content of `lab.md`.
4. Confirm that the host can spawn fresh isolated sub-agents. Resolve requested model and
   reasoning-effort settings without creating custom host-specific agent profiles.
5. Verify that `FEATURE_DIR/logs/` is ignored, because reports must survive branch switches
   without dirtying the checkout. If it is not ignored, add the narrow feature-log pattern to
   `.gitignore` and commit it on `BASE`.
6. Set `PARENT = BASE`. Verify that every dependency named by the first selected ticket is
   present under `done/` on `PARENT`. If an accepted dependency exists only on another
   branch, stop and ask the user to check out or merge that branch; do not rebuild it.
7. Run `git cat-file -e "$PARENT:$IMPLEMENT_SKILL_RELATIVE"` and verify that `PARENT` also
   contains this skill's implementer and reviewer templates and `scripts/protocol.sh`. These
   files must remain available after the checkout moves to a ticket branch. Stop and report
   any missing protocol file.
8. Print the plan: base branch, feature directory, implement skill path, branch-switch and
   accept policy, model, effort, maximum attempts, and ordered ticket list. Unless `-y` was
   passed, wait for confirmation.
9. Create `FEATURE_DIR/logs`.

## Per Ticket

Let `NAME` be the filename without `.md`, `BRANCH = ticket/NAME`, and `TICKET_BASE = PARENT`.
`LOG_PREFIX` is the absolute path `REPO/FEATURE_DIR/logs/NAME`. Every path sent to a
sub-agent is absolute and inside `REPO`. Set `IMPLEMENT_SKILL` to the absolute path
`REPO/IMPLEMENT_SKILL_RELATIVE`.

Render the templates in [`references/implementer.md`](references/implementer.md) and
[`references/reviewer.md`](references/reviewer.md). Replace every placeholder and verify
that no `{{` remains.

| Placeholder | Value |
| --- | --- |
| `TICKET` | Current ticket path in `issues/` or `to-review/` |
| `ATTEMPT` | Attempt number |
| `BRANCH`, `TICKET_BASE`, `REPO`, `LOG_PREFIX`, `LAB`, `IMPLEMENT_SKILL` | Values defined above |
| `SPEC_FILE`, `ISSUES_DIR`, `REVIEW_DIR`, `DONE_DIR` | Absolute paths under `REPO/FEATURE_DIR` |
| `APP_PREFIX`, `CHECKS_LOG` | `LOG_PREFIX.app.<attempt>` and the gate log; reviewer only |

1. Check out the ticket branch. Require the checkout to be clean. If `BRANCH` exists, verify
   that `PARENT` is its ancestor, then run `git switch BRANCH`; a branch with the wrong parent
   settles as **branch does not contain its expected base**. Otherwise run
   `git switch -c BRANCH PARENT`. Verify the current branch. If `DONE_DIR/NAME.md` already
   exists, treat the ticket as done without spending another implementer or reviewer.
   Otherwise run `REPO/FEATURE_DIR/lab.sh prepare`; if it fails, settle as
   **branch would not prepare**.
2. Run attempts from 1 through `--max-attempts`:

   a. **Implement.** Skip this only when `--resume` is active and
      `REVIEW_DIR/NAME.md` exists. Render the implementer template and use it as
      the entire prompt for a new isolated sub-agent. The template must direct the sub-agent
      through `IMPLEMENT_SKILL` before it performs ticket work. Wait for completion and save
      its final report verbatim to `LOG_PREFIX.implement.<attempt>.md`. If
      `REVIEW_DIR/NAME.md` does not exist afterward, settle as **stalled in issues**. Before
      continuing, verify that `BRANCH` is still checked out and `git status --porcelain` is
      empty; otherwise settle as **unsafe agent handoff**.

   b. **Gate.** Run `REPO/FEATURE_DIR/lab.sh checks
      LOG_PREFIX.checks.<attempt>.log`. On failure, do not spend a reviewer on a red build.
      Write a detail file naming the checks log and the lab's per-failure detail as the first
      evidence to inspect, followed by the last 60 lines of the checks log in a fenced block.
      Run `SKILL_DIR/scripts/protocol.sh send-back to-review/NAME.md <attempt>
      "automated checks" <detail-file>`, commit as
      `Send NAME back: automated checks`, and start the next attempt.

   c. **Review.** Run `REPO/FEATURE_DIR/lab.sh app-start
      LOG_PREFIX.app.<attempt>`. If it fails, settle as **app would not start**. Render the
      reviewer template and use it as the entire prompt for a different new isolated
      sub-agent. Wait for completion, save its final report verbatim to
      `LOG_PREFIX.review.<attempt>.md`, and always run `REPO/FEATURE_DIR/lab.sh app-stop`
      after the reviewer returns or fails. Verify the checked-out branch and clean status
      before deciding the verdict.

   d. **Verdict.** Read the checkout's folders, not the report. `done/NAME.md` means **done**.
      `issues/NAME.md` means sent back, so begin the next attempt. Anything else means
      **stalled in to-review**. Exhausting the loop means **not accepted after N attempts**.

3. **Land a done ticket.** With `--merge`, run `git switch BASE`, then
   `git merge --no-ff --no-edit -m "Merge BRANCH" BRANCH`, and set `PARENT = BASE`. Without
   `--merge`, keep `BRANCH` checked out and set `PARENT = BRANCH` so the next ticket stacks
   on it naturally. Retain every ticket branch.
4. **Handle any other result.** Run `app-stop`. When the checkout is clean, return to
   `TICKET_BASE` while retaining `BRANCH` for inspection. Report the reason and branch.
   Without `--keep-going`, stop because later tickets depend on this one. Point to the logs
   and tell the user to rerun with `--from NN` after resolving it. With `--keep-going`,
   continue from `PARENT`; a dirty checkout prevents continuation.

## Summary

List accepted branches in merge order, oldest first, noting whether they are stacked or
already merged and which branch remains checked out. Then list every failed ticket with its
reason, branch, and the relevant evidence under `FEATURE_DIR/logs/`. Every outcome claim
must point to a log file.
