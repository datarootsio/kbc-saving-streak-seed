# Saving Streak

A training application: money moves from a current account into a savings account, and every whole
euro moved earns a point that can be spent on a reward.

- `backend/` — Spring Boot 3.5 (Java 17, Maven wrapper), SQLite at `data/saving-streak.db`
- `frontend/` — React 18 + Vite 6, dev server on 5173 proxying `/api` to `localhost:8080`

## Agent skills

### Issue tracker

Issues and specs live as markdown files under `.scratch/<feature-slug>/` in this repo; there is no
remote tracker. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, each label string equal to its name. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Logging

Every feature logs its own flow; this is part of the work, not an extra. A reviewer (and
the next agent) reads `io.dataroots.savingstreak` at DEBUG to see what the app actually
did, so silent code is unreviewable.

Use SLF4J (`LoggerFactory.getLogger(Thing.class)`), never `System.out`. One INFO line per
business event with the values that decided it, WARN on every refusal with its reason,
DEBUG for the inputs behind a decision, ERROR with the exception for unexpected failures.
Keep lines greppable: `log.info("deposit accepted customerId={} cents={} points={}", ...)`.
