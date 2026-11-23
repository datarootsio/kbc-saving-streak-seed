# KBC Mainframe COBOL Standards & Compliance Rules

House engineering standards and regulatory policies for the KBC Retail Account Opening service (`ACCTOPEN`) and its authorization, KYC/AML risk-screening, IBAN-generation, ledger persistence, and audit subprograms.

## Fixed-Format Mainframe COBOL Style

1. **Strict 72-column fixed format** (enforced automatically during compilation):
   - Columns 1–6: spaces (`      `).
   - Column 7: `*` for comments, space (` `) for code.
   - Columns 8–11 (Area A): division/section headers, paragraph names, `FD`, and `01`-level items.
   - Columns 12–72 (Area B): statements and subordinate items (`05`, `10`, `88`).
   - Columns 73+: forbidden — any `.cbl` or `.cpy` line longer than 72 characters fails the build.
2. **Stateless, re-entrant subprograms**:
   - Declare callable subprograms with `PROGRAM-ID. <NAME> IS INITIAL.` and explicitly initialize output commareas on entry so consecutive calls never leak `WORKING-STORAGE` state.
3. **Structured control flow**:
   - Use numbered paragraphs (`0000-MAIN`, `1000-...`, `2000-...`), `88`-level condition names, `EVALUATE TRUE`, and `EXIT PARAGRAPH` / `GOBACK`. Never use `GO TO`.

## Copybooks & JCL `DD_*` Dataset Mappings

- `copy/ACCTREQ.cpy` / `copy/ACCTRSP.cpy` — caller linkage contract (`ACCT-REQUEST` / `ACCT-RESPONSE`) used by `ACCTCLI` and `ACCTOPEN`.
- `copy/ELBAREA.cpy` — `ELBCHK` mandate check commarea (`ELB-CHECK-AREA`), reading `DD_ELBFILE` (`mandates.dat`).
- `copy/EXTAREA.cpy` — `EXTRISK` KYC/PEP/sanctions commarea (`EXT-RISK-AREA`), reading `DD_RISKFILE` (`customers.dat`), plus `IBANGEN` commarea (`IBAN-GEN-AREA`) generating 16-char Belgian KBC IBANs (`BE` + 2 ISO check digits + bank code `736` + 7-digit sequence + 2 national mod-97 digits; `IBAN-ACCT-CLASS` `'PRI'` or `'SUB'`).
- `copy/DBAREA.cpy` — DB2-style ledger commarea (`DB-ACCESS-AREA`) for `ACCTDB` (`DD_ACCTFILE` -> `accounts.dat`, `DD_SUBFILE` -> `subaccounts.dat`) with `DB-SQLCODE` (`0` OK, `+100` not found, `-803` duplicate key, `-904` unavailable), plus `AUDIT-LOG-AREA` for `AUDITLOG` (`DD_AUDFILE` -> `audit.dat`).

## KBC ELB Mandate Policy

- **Transactional / write mandates:** `HOLDER`, `JOINT-FULL`, and `POA-TRANSACT` (subject to channel and product eligibility rules).
- **Consultation-only mandate:** `POA-VIEW` authorizes balance and statement inquiry (`INQUIRE`) only and must **never** authorize opening or modifying accounts on **any** channel (`BRANCH`, `MOBILE`, or `BATCH`).
- **Inactive / absent mandates:** `REVOKED` and `NONE` hold zero authority.

## External Risk Rules, Return Codes & Fail-Closed Compliance

Every call populates `RSP-RETURN-CODE` (`PIC X(02)`) and `RSP-REASON-CODE` (`PIC X(04)`):

- `'00'` (`RSP-RC-OK`, reason `'0000'`): All mandate, validation, and KYC checks (`RISK-CLEAR`) passed; IBAN allocated, ledger row inserted, and audit record written.
- `'04'` (`RSP-RC-WARN`): Non-blocking informational notice only. **Strictly forbidden** whenever mandate verification or KYC/AML/sanctions screening is incomplete, degraded, or unavailable.
- `'08'` (`RSP-RC-REFUSED`): Business or compliance refusal — invalid input, insufficient ELB mandate (including `POA-VIEW`, `REVOKED`, `NONE`), `PEP-HOLD` or `SANCTION-BLOCK` from `EXTRISK`, duplicate request/key (`-803`), or product/sub-account policy violation.
- `'12'` (`RSP-RC-TECH-ERR`): Upstream or technical failure — `ELBCHK` `ERROR`, `EXTRISK` timeout (`SVC-UNAVAIL`), `IBANGEN` error, or `ACCTDB` `-904`.

**Fail-closed invariant:** On `'08'` or `'12'`, never allocate an IBAN (`RSP-ALLOCATED-IBAN` must remain spaces) and never insert into `DD_ACCTFILE` or `DD_SUBFILE`, while **always** recording the attempt in `DD_AUDFILE` via `AUDITLOG`.
