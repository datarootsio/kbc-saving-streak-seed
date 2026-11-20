# Retail account opening — compliance repair and sub-accounts

The KBC Retail Account Opening COBOL service (`ACCTOPEN`) compiles cleanly and passes its 7 starter tests, yet nightly compliance reconciliation flagged two control failures. After repairing both defects, you will clarify and add regulated **Linked Savings Sub-Account** opening.

**Goal:** practise evidence-based debugging, requirement alignment, test-driven COBOL changes, and subagent review using **Claude Code** (`claude`).

## Setup

In a terminal inside this repository, verify the compiler and starter suite:

```bash
make tools
make test
make checkpoint
```

Run `claude` in your terminal. `CLAUDE.md` is loaded automatically with house 72-column COBOL rules, copybooks, JCL `DD_*` file mappings, and return-code conventions.

- `make build` — check the 72-column limit and compile `bin/run-acctopen`.
- `make test` — build and run all `.case` tests (`make test CASE=sanction` filters by name).
- `./scripts/invoke.sh` — run `bin/run-acctopen` with `KEY=VALUE` args or a `.case` file against `data/work/*.dat`.
- `make reset-data` — reset `data/work/*.dat` from `data/seed/`.
- `make checkpoint` — snapshot `copy/`, `src/`, `tests/`, and `data/seed/`.
- `make diff` / `make diff-baseline` — show changes since the last checkpoint or workshop start.
- `make restore` — revert code and tests to `.checkpoint/` (`CLAUDE.md`, `.claude/`, and markdown specs stay intact).

## Part 1 — Investigate and repair compliance defects

Internal Audit raised two findings (`INC-4418`) even though `make test` is green:

- **INC-4418-A:** Read-only consultation proxies (`POA-VIEW`) are refused on `MOBILE` (`RC=08`), yet were able to open `ACTIVE` accounts at `BRANCH` counters.
- **INC-4418-B:** When KYC screening times out (`SVC-UNAVAIL`), `ACCTOPEN` returns a warning instead of failing closed (`RC=12`), allocates an IBAN, and writes an `ACTIVE` account to `DD_ACCTFILE`.

### Your task

1. Use `claude` to trace how `ACCTOPEN`, `ELBCHK`, `EXTRISK`, `IBANGEN`, `ACCTDB`, and `AUDITLOG` interact before changing code. Separate observed facts from hypotheses.
2. Reproduce both defects using `data/seed/` fixtures via `./scripts/invoke.sh` or `.case` tests. Check stored `DD_ACCTFILE` and `DD_AUDFILE` records as well as the response codes.
3. Add regression `.case` tests in `tests/cases/` that fail before your fix and pass afterward, while keeping legitimate mandates and all starter tests green (`make test`).
4. In a fresh `claude` session (or unprimed review subagent), review `make diff` and your tests against `INC-4418` and `CLAUDE.md`. Address valid concerns, then run `make checkpoint`.

## Part 2 — Add linked savings sub-account opening

Extend `ACCTOPEN` (`REQ-OPERATION = 'OPEN-SUB'`, i.e. `OP=OPEN-SUB`, `PARENT_IBAN`, `SUB_PURPOSE`) by delegating to a new `src/SUBACCT.cbl` module (`make build` links all `src/*.cbl` automatically). Allocate a `'SUB'` IBAN via `IBANGEN`, persist approved sub-accounts to `DD_SUBFILE` via `ACCTDB`, and log every attempt via `AUDITLOG`.

### Your task

1. Start a fresh `claude` session (`/clear`) and resolve ambiguities before coding (inspect `data/seed/` fixtures):
   - Which parent account types, statuses, ownership rules, and currencies are eligible?
   - How many sub-accounts are allowed per parent, and do `CLOSED` ones count toward the cap?
   - May a `POA-TRANSACT` proxy open a sub-account, or only `HOLDER` / `JOINT-FULL`?
   - What minimum deposit and `08` / `12` reason codes apply?
   Save the agreed rules and examples to `subaccount-spec.md`.
2. Implement in small test-driven slices (`make checkpoint` → failing `.case` test → COBOL change → `make test`).
3. Start a fresh `claude` session and run three parallel review subagents (**Code**, **Tests**, **Regulated Compliance**) over `make diff-baseline` and `subaccount-spec.md`. Fix valid gaps.

## Be ready to show

Your reproductions and regression tests for `INC-4418`, `subaccount-spec.md` with the ambiguities you resolved, your working `SUBACCT.cbl` and `make test` output, and what your fresh-context review subagents caught.
