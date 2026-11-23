# KBC Retail Account Opening — Mainframe COBOL Exercise

Standalone fixed-format COBOL service simulating KBC Retail Account Opening (`ACCTOPEN`), backed by KBC ELB mandate verification, external KYC/AML/sanctions screening, Belgian IBAN allocation, DB2-style ledger access, and regulatory audit logging.

Start with [exercises.md](exercises.md) for the **Claude Code** (`claude`) workshop instructions.

## Architecture & Directory Map

```
ACCTCLI (bin/run-acctopen)
  └──▶ ACCTOPEN  ──┬──▶ ELBCHK    (DD_ELBFILE  : customer & proxy mandates)
                   ├──▶ EXTRISK   (DD_RISKFILE : KYC, PEP & sanctions status)
                   ├──▶ IBANGEN   (ISO 13616 Belgian KBC IBAN generator, bank 736)
                   ├──▶ ACCTDB    (DD_ACCTFILE & DD_SUBFILE : DB2-style SQLCODEs)
                   └──▶ AUDITLOG  (DD_AUDFILE  : regulatory audit trail)
```

- `copy/` — Shared `LINKAGE` and `WORKING-STORAGE` copybooks:
  - `ACCTREQ.cpy` / `ACCTRSP.cpy` — Request and response contract for `ACCTOPEN`.
  - `ELBAREA.cpy` — KBC ELB mandate check commarea (`HOLDER`, `JOINT-FULL`, `POA-TRANSACT`, `POA-VIEW`, `REVOKED`, `NONE`).
  - `EXTAREA.cpy` — External KYC risk screening (`EXT-RISK-AREA`) and IBAN generator (`IBAN-GEN-AREA`).
  - `DBAREA.cpy` — DB2-style ledger commarea (`DB-SQLCODE`: `0`, `+100`, `-803`, `-904`) and audit commarea.
- `src/` — Fixed-format COBOL programs (strictly columns 1–72, compiled with `GnuCOBOL` `-fixed -Wall`). Any `.cbl` added to `src/` is automatically linked into `bin/run-acctopen` on `make build`.
- `data/seed/` — Reference fixtures for primary accounts, linked sub-accounts, ELB mandates, and customer KYC profiles.
- `tests/` — Zero-dependency automated test runner (`tests/run_tests.sh`) and declarative `.case` files (`tests/cases/*.case`).
- `docs/standards-and-style.md` — House fixed-format COBOL standards, copybook and JCL `DD_*` conventions, ELB mandate rules, and fail-closed compliance policies.

## Commands

Run `make help` at any time. All build, verification, data reset, and local snapshot operations use `make`:

| Command | What it does |
| --- | --- |
| `make tools` | Verify (or install) the `GnuCOBOL` (`cobc`) compiler and initialize local snapshots |
| `make build` | Enforce the 72-column fixed-format limit and compile `bin/run-acctopen` |
| `make test` | Build and run all `.case` tests in isolated temp data (`make test CASE=05` runs a subset) |
| `make reset-data` | Reset interactive working files in `data/work/` from `data/seed/` |
| `make checkpoint` | Save current `copy/`, `src/`, `tests/`, and `data/seed/` to `.checkpoint/` |
| `make diff` | Show unified diff of changes since the last `make checkpoint` |
| `make diff-baseline` | Show unified diff of all changes since the start of the exercise |
| `make restore` | Restore `copy/`, `src/`, `tests/`, and `data/seed/` from `.checkpoint/` (keeps `CLAUDE.md`, `.claude/`, `docs/`, and spec files intact) |

## Running a Request Interactively

To send an ad-hoc request from the terminal against `data/work/*.dat`:

```bash
make reset-data
./scripts/invoke.sh <<'EOF'
REQ_ID=REQ000009001
OP=OPEN-ACCT
CHANNEL=MOBILE
CUSTOMER_ID=CUST000101
REQUESTER_ID=CUST000101
ACCOUNT_TYPE=CURRENT
CURRENCY=EUR
DEPOSIT=250.00
EOF
```

Inspect the resulting ledger and audit records directly in `data/work/accounts.dat`, `data/work/subaccounts.dat`, and `data/work/audit.dat`.
