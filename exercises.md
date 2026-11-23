# Retail account opening

The KBC Retail Account Opening COBOL service passes its existing tests, yet nightly compliance reconciliation flagged two control failures:

- Read-only consultation proxies (`POA-VIEW`) are refused on mobile, yet were able to open active accounts at branch counters.
- When KYC risk screening times out (`SVC-UNAVAIL`), the service returns a warning instead of failing closed, allocates an IBAN, and records an active account without completed screening.

**Goal:** practise evidence-based debugging, requirement alignment, test-driven changes, and fresh-context review using Claude Code.

## Part 1 — Investigate and repair compliance defects

1. Use AI to investigate why both issues occur before changing code. Follow how the agent finds the relevant COBOL programs, copybooks, and seed data, and keep observed facts separate from hypotheses.
2. Reproduce both failures. Check the stored account and audit records as well as the returned status codes.
3. Propose a fix and test it. Check that regression tests detect the original failures and pass after the repair, and that legitimate mandates and account openings still work.
4. Have another agent review the fix in a fresh context. Give it the compliance findings, your evidence, and the changes. Evaluate its findings and address valid concerns.

## Part 2 — Open linked savings sub-accounts

Extend the service so retail customers can open a **linked savings sub-account** (such as goal savings, youth savings, or a tax reserve) attached to an existing KBC parent account.

1. Start a fresh session and align on how sub-account opening should behave before coding. Clarify at least:
   - Which parent account types, statuses, ownership rules, and currencies are eligible?
   - How many sub-accounts are allowed per parent, and do closed ones count toward the limit?
   - Which mandates and channels may open a sub-account?
   - What deposit rules and refusal reasons apply?
2. Have the agent explain the agreed behaviour through concrete examples, correct any wrong assumptions, and save the agreement as a specification.
3. Slice the feature into small tasks with observable outcomes, implementing and verifying one slice at a time.
4. In a fresh session, run parallel review subagents covering COBOL standards, test coverage, and banking compliance. Address valid findings.

## Be ready to show

The reproductions and cause of both compliance defects, the ambiguities you resolved and your saved specification, your working sub-account feature and verification evidence, and what your fresh-context reviews caught.
