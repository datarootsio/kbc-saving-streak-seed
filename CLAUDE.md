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
