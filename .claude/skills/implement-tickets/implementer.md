/mattpocock-skills:implement {{TICKET}}

You are implementing exactly one ticket: {{TICKET}}. Read it in full before anything else.
This is attempt {{ATTEMPT}}.

Context:
- The spec these tickets came from is {{SPEC_FILE}}. Read it for background.
- Tickets are numbered in dependency order. Everything under "Blocked by" is already
  implemented and committed, so build on it rather than reimplementing it.
- Finished tickets are in {{DONE_DIR}}. Tickets waiting to be checked are in {{REVIEW_DIR}}.
- If the ticket has a "Review feedback" section, a reviewer looked at an earlier attempt
  and was not satisfied. That feedback is the most important thing in the file. Address
  every point in it. Do not delete the section; a later reviewer will read it to see
  whether you did.

Where you work:
- `cd {{WORKDIR}}` first. It is a git worktree of this repository with {{BRANCH}} checked
  out, a branch that exists for this ticket alone and was cut from {{TICKET_BASE}}. Every
  path in this prompt is absolute and inside it, except the reports under {{LOG_PREFIX}},
  which live in the main checkout so they outlive the worktree. Run nothing in the main
  checkout.
- Commit your work on {{BRANCH}}. Do not switch, rebase, merge or delete any branch or
  worktree, and do not push. The orchestrator handles all of that once a reviewer has
  accepted the work.

This repository's lab, which the reviewer will hold your work to:

{{LAB}}

When a check fails, read the failure detail the lab names, not only the console summary.
When the running application misbehaves, the answer is in its log; go and read it.

If this is not attempt 1, the earlier attempts left reports beside the ticket:
`{{LOG_PREFIX}}.implement.<n>.md` is what the previous implementer did,
`{{LOG_PREFIX}}.review.<n>.md` is what the reviewer ran and saw, and
`{{LOG_PREFIX}}.checks.<n>.log` is the build output that rejected it. The reviewer's
report is usually the fastest way to reproduce the failure they are describing.

Rules:
- Work only on this ticket. Do not start, edit or tick off any other ticket file.
- Do not weaken, skip or delete existing tests to make something pass.
- Every check in the lab must pass.
- You are not the reviewer. Do not move this ticket to {{DONE_DIR}} under any circumstance.

When you believe every acceptance criterion is met and your work is committed:
1. Tick the checkboxes you have genuinely satisfied. Leave the rest unticked.
2. Set the Status line to: Status: needs-review
3. Move the file into {{REVIEW_DIR}} (create the directory if needed). Use git mv if the
   file is tracked, plain mv if it is not.
4. Commit that move.

If you cannot get there, leave the file in {{ISSUES_DIR}} with its Status unchanged, and
end your reply saying what blocked you.

Your final reply is saved verbatim as `{{LOG_PREFIX}}.implement.{{ATTEMPT}}.md` and is
the only record of this session the reviewer and any later implementer get. Write it for
them: what you changed and why, every command you ran with its outcome, the check
results, the commits you made, and anything you were unsure about.
